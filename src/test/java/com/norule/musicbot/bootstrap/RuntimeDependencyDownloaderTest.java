package com.norule.musicbot.bootstrap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDependencyDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void formatsPercentageWhenContentLengthIsKnown() {
        String message = RuntimeDependencyDownloader.formatProgress(
                12, 58, "file.jar", 5_000L, 20_000L, 2_000.0, 2.5);

        assertTrue(message.contains("12/58"));
        assertTrue(message.contains("downloaded=5000 bytes"));
        assertTrue(message.contains("total=20000 bytes"));
        assertTrue(message.contains("progress=25.0%"));
        assertTrue(message.contains("speed="));
        assertTrue(message.contains("elapsed=2.5s"));
        assertTrue(message.contains("eta="));
    }

    @Test
    void omitsPercentageAndEtaWhenContentLengthIsUnknown() {
        String message = RuntimeDependencyDownloader.formatProgress(
                3, 58, "file.jar", 5_000L, -1L, 2_000.0, 2.5);

        assertTrue(message.contains("downloaded=5000 bytes"));
        assertTrue(message.contains("speed="));
        assertTrue(message.contains("elapsed=2.5s"));
        assertFalse(message.contains(" total="));
        assertFalse(message.contains("progress="));
        assertFalse(message.contains("eta="));
    }

    @Test
    void limitsProgressOutputToConfiguredInterval() {
        AtomicLong clock = new AtomicLong();
        List<String> messages = new ArrayList<>();
        RuntimeDependencyDownloader.DownloadProgressReporter reporter =
                new RuntimeDependencyDownloader.DownloadProgressReporter(
                        true, 2_000, 1, 1, "file.jar", 10_000L, 0L, clock::get, messages::add);

        clock.set(1_000_000_000L);
        reporter.onBytes(1_000L);
        clock.set(2_000_000_000L);
        reporter.onBytes(2_000L);
        clock.set(3_999_000_000L);
        reporter.onBytes(3_000L);
        clock.set(4_000_000_000L);
        reporter.onBytes(4_000L);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("downloaded=2000 bytes"));
        assertTrue(messages.get(1).contains("downloaded=4000 bytes"));
    }

    @Test
    void retriesAfterContinuousNoByteStall() throws IOException {
        byte[] expected = "complete jar".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        TestServer testServer = startServer(exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(200, expected.length);
                exchange.getResponseBody().flush();
                sleep(250L);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, expected.length);
            exchange.getResponseBody().write(expected);
            exchange.close();
        });
        try (testServer) {
            List<String> messages = new ArrayList<>();
            List<Long> retryDelays = new ArrayList<>();
            RuntimeDependencyDownloader downloader = new RuntimeDependencyDownloader(
                    settings(50, 1), List.of(testServer.baseUrl()), messages::add, System::nanoTime,
                    retryDelays::add);
            Path runtimeLibs = Files.createDirectories(tempDir.resolve("stall-runtime-libs"));

            RuntimeDependencyBootstrap.synchronizeRuntimeDependencies(
                    runtimeLibs, List.of(artifact()), Map.of(), settings(50, 1), downloader);

            assertEquals(2, requests.get());
            assertEquals(List.of(1_000L), retryDelays);
            assertEquals("complete jar", Files.readString(
                    runtimeLibs.resolve("demo-2.0.0.jar"), StandardCharsets.UTF_8));
            assertFalse(Files.exists(runtimeLibs.resolve("demo-2.0.0.jar.part")));
            assertTrue(messages.stream().anyMatch(message -> message.contains("exception=StallTimeoutException")
                    && message.contains("retry 1/1")));
        }
    }

    @Test
    void abortsAfterRetriesAreExhaustedAndDeletesPartFile() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        TestServer testServer = startServer(exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1L);
            exchange.close();
        });
        try (testServer) {
            List<Long> retryDelays = new ArrayList<>();
            RuntimeDependencyDownloader downloader = new RuntimeDependencyDownloader(
                    settings(100, 2), List.of(testServer.baseUrl()), ignored -> { }, System::nanoTime,
                    retryDelays::add);
            Path runtimeLibs = Files.createDirectories(tempDir.resolve("failed-runtime-libs"));
            Path part = runtimeLibs.resolve("demo-2.0.0.jar.part");

            IOException failure = assertThrows(IOException.class,
                    () -> RuntimeDependencyBootstrap.synchronizeRuntimeDependencies(
                            runtimeLibs, List.of(artifact()), Map.of(), settings(100, 2), downloader));

            assertEquals(3, requests.get());
            assertEquals(List.of(1_000L, 2_000L), retryDelays);
            assertFalse(Files.exists(part));
            assertFalse(Files.exists(runtimeLibs.resolve("demo-2.0.0.jar")));
            assertTrue(failure.getMessage().contains("artifact demo-2.0.0.jar"));
            assertTrue(failure.getMessage().contains("host=127.0.0.1"));
            assertTrue(failure.getMessage().contains("httpStatus=503"));
            assertTrue(failure.getMessage().contains("retries=2/2"));
            assertFalse(failure.getMessage().contains(testServer.baseUrl()));
        }
    }

    private static RuntimeDependencyBootstrap.DependencyArtifact artifact() {
        return new RuntimeDependencyBootstrap.DependencyArtifact("org.example", "demo", "2.0.0", "");
    }

    private static RuntimeDependencyBootstrap.BootstrapSettings settings(int stallTimeoutMs, int maxRetries) {
        return new RuntimeDependencyBootstrap.BootstrapSettings(
                true, false, false, false, 2_000, 1_000, 1_000, stallTimeoutMs, maxRetries);
    }

    private static TestServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
        return new TestServer(server, executor);
    }

    private static void sleep(long milliseconds) throws IOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Test server interrupted", e);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record TestServer(HttpServer server, ExecutorService executor) implements AutoCloseable {
        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
