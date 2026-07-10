package com.norule.musicbot.shorturl.infra;

import com.norule.musicbot.ShortUrlService;
import com.norule.musicbot.config.BotConfig;
import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.service.shorturl.ImageShareService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShortUrlGatewayServer {
    private static final long MULTIPART_OVERHEAD_BYTES = 128L * 1024L;
    private static final Pattern MULTIPART_BOUNDARY = Pattern.compile("boundary=(?:\\\"([^\\\"]+)\\\"|([^;\\s]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_DISPOSITION_NAME = Pattern.compile("(?:^|;)\\s*name=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Set<String> RESERVED_PATHS = Set.of(
            "api", "assets", "static", "web", "dashboard", "short-url", "index", "404"
    );

    private final ShortUrlService shortUrlService;
    private final Supplier<BotConfig.ShortUrl> configSupplier;
    private volatile HttpServer server;
    private volatile String bindHost = "";
    private volatile int bindPort = -1;

    public ShortUrlGatewayServer(ShortUrlService shortUrlService, Supplier<BotConfig.ShortUrl> configSupplier) {
        if (shortUrlService == null) {
            throw new IllegalArgumentException("shortUrlService cannot be null");
        }
        if (configSupplier == null) {
            throw new IllegalArgumentException("configSupplier cannot be null");
        }
        this.shortUrlService = shortUrlService;
        this.configSupplier = configSupplier;
    }

    public synchronized void syncWithConfig() {
        BotConfig.ShortUrl config = config();
        if (!config.isEnabled()) {
            stop();
            return;
        }
        if (server != null && Objects.equals(bindHost, config.getBindHost()) && bindPort == config.getBindPort()) {
            return;
        }
        stop();
        start(config);
    }

    public synchronized void shutdown() {
        stop();
    }

    private BotConfig.ShortUrl config() {
        BotConfig.ShortUrl config = configSupplier.get();
        return config == null ? BotConfig.ShortUrl.defaultValues() : config;
    }

    private void start(BotConfig.ShortUrl config) {
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress(config.getBindHost(), config.getBindPort()), 0);
            created.createContext("/api/short", this::handleShortUrlApi);
            created.createContext("/web/", this::handleWebAsset);
            created.createContext("/", this::handleResolve);
            created.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "NoRule-ShortUrl");
                t.setDaemon(true);
                return t;
            }));
            created.start();
            this.server = created;
            this.bindHost = config.getBindHost();
            this.bindPort = config.getBindPort();
            System.out.println("[NoRule] Short URL gateway started on http://" + config.getBindHost() + ":" + config.getBindPort());
        } catch (Exception e) {
            System.out.println("[NoRule] Failed to start short URL gateway: " + e.getMessage());
        }
    }

    private void stop() {
        HttpServer current = this.server;
        if (current == null) {
            return;
        }
        current.stop(0);
        this.server = null;
        System.out.println("[NoRule] Short URL gateway stopped.");
    }

    private void handleResolve(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            if (!isGetOrHead(exchange)) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            sendHtml(exchange, 200, loadTemplate("web/short-url.html"));
            return;
        }
        if (rawPath.startsWith("/api/")) {
            sendHtml(exchange, 404, buildShortUrlNotFoundPage());
            return;
        }
        String code = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (code.isBlank() || code.contains("/") || RESERVED_PATHS.contains(code.toLowerCase(Locale.ROOT))) {
            sendHtml(exchange, 404, buildShortUrlNotFoundPage());
            return;
        }

        ImageShare imageShare = shortUrlService.resolveImageShare(code);
        if (imageShare != null) {
            handleImageShareResolve(exchange, imageShare);
            return;
        }

        if (!isGetOrHead(exchange)) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String target = shortUrlService.resolveTarget(code);
        if (target == null || target.isBlank()) {
            sendHtml(exchange, 404, buildShortUrlNotFoundPage());
            return;
        }
        exchange.getResponseHeaders().set("Location", target);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void handleShortUrlApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/short/image/config".equals(path)) {
            handleImageShareConfig(exchange);
            return;
        }
        if ("/api/short/image".equals(path)) {
            handleCreateImageShare(exchange);
            return;
        }
        if ("/api/short".equals(path)) {
            handleCreateShortUrl(exchange);
            return;
        }
        sendJson(exchange, 404, DataObject.empty()
                .put("error", "Not Found")
                .put("errorCode", "NOT_FOUND")
                .toString());
    }

    private void handleCreateShortUrl(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, DataObject.empty()
                    .put("error", "Method Not Allowed")
                    .put("errorCode", "METHOD_NOT_ALLOWED")
                    .toString());
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> form = parseRequestBody(body, contentType);
        String target = form.getOrDefault("url", "").trim();
        String customCode = form.getOrDefault("customCode", form.getOrDefault("code", form.getOrDefault("slug", ""))).trim();
        if (target.isBlank()) {
            sendJson(exchange, 400, DataObject.empty()
                    .put("error", "Missing url")
                    .put("errorCode", "MISSING_URL")
                    .toString());
            return;
        }

        ShortUrlService.ShortUrlEntry created = shortUrlService.create(target, customCode);
        if (created == null) {
            sendJson(exchange, 400, DataObject.empty()
                    .put("error", "Invalid url or code")
                    .put("errorCode", "INVALID_URL_OR_CODE")
                    .toString());
            return;
        }

        sendJson(exchange, 200, DataObject.empty()
                .put("code", created.getCode())
                .put("shortUrl", shortUrlService.toPublicUrl(created.getCode()))
                .put("targetUrl", created.getTarget())
                .toString());
    }

    private void handleImageShareConfig(HttpExchange exchange) throws IOException {
        if (!isGetOrHead(exchange)) {
            sendJson(exchange, 405, DataObject.empty()
                    .put("error", "Method Not Allowed")
                    .put("errorCode", "METHOD_NOT_ALLOWED")
                    .toString());
            return;
        }
        ImageShareService.Options options = shortUrlService.imageShareOptions();
        if (options == null) {
            sendJson(exchange, 200, DataObject.empty().put("enabled", false).toString());
            return;
        }
        sendJson(exchange, 200, DataObject.empty()
                .put("enabled", options.enabled())
                .put("defaultRetentionHours", options.defaultRetentionMillis() / (60L * 60L * 1000L))
                .put("maxRetentionDays", options.maxRetentionMillis() / (24L * 60L * 60L * 1000L))
                .put("maxFileSizeBytes", options.maxFileSizeBytes())
                .put("maxFileSizeMb", options.maxFileSizeBytes() / (1024L * 1024L))
                .toString());
    }

    private void handleCreateImageShare(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendImageError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
            return;
        }
        ImageShareService.Options options = shortUrlService.imageShareOptions();
        if (options == null || !options.enabled()) {
            sendImageError(exchange, 503, "IMAGE_SHARING_DISABLED", "Image sharing is disabled");
            return;
        }

        try {
            MultipartForm form = parseMultipartForm(exchange, options.maxFileSizeBytes());
            byte[] image = form.firstFile("image");
            if (image == null) {
                sendImageError(exchange, 400, "IMAGE_REQUIRED", "An image file is required");
                return;
            }
            long requestedRetention = parseRetentionMillis(form.value("retentionHours"), options);
            if (requestedRetention < 0L) {
                sendImageError(exchange, 400, "RETENTION_TOO_LONG", "The requested retention is outside the allowed range");
                return;
            }
            boolean passwordProtected = Boolean.parseBoolean(form.value("passwordProtected"));
            ImageShareService.UploadResult result = shortUrlService.createImageShare(
                    new ImageShareService.Upload(image, passwordProtected, form.value("password"), requestedRetention)
            );
            if (!result.isSuccess()) {
                sendImageUploadFailure(exchange, result.error());
                return;
            }
            ImageShare created = result.imageShare();
            sendJson(exchange, 200, DataObject.empty()
                    .put("code", created.code())
                    .put("shortUrl", shortUrlService.toPublicUrl(created.code()))
                    .put("expiresAt", created.expiresAt())
                    .put("passwordProtected", created.isPasswordProtected())
                    .toString());
        } catch (RequestBodyTooLargeException e) {
            sendImageError(exchange, 413, "IMAGE_TOO_LARGE", "The uploaded image exceeds the configured size limit");
        } catch (InvalidMultipartException e) {
            sendImageError(exchange, 400, "INVALID_IMAGE_UPLOAD", "Invalid image upload request");
        }
    }

    private void handleImageShareResolve(HttpExchange exchange, ImageShare imageShare) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        if (imageShare.isPasswordProtected()) {
            boolean authenticated = "POST".equalsIgnoreCase(method)
                    && shortUrlService.verifyImageSharePassword(imageShare, readPassword(exchange));
            if (!authenticated) {
                sendImagePasswordPage(exchange, "POST".equalsIgnoreCase(method));
                return;
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        sendImage(exchange, imageShare);
    }

    private String readPassword(HttpExchange exchange) throws IOException {
        try {
            String body = new String(readBodyLimited(exchange, 64L * 1024L), StandardCharsets.UTF_8);
            Map<String, String> form = parseRequestBody(body, exchange.getRequestHeaders().getFirst("Content-Type"));
            return form.getOrDefault("password", "");
        } catch (RequestBodyTooLargeException ignored) {
            return "";
        }
    }

    private void sendImagePasswordPage(HttpExchange exchange, boolean incorrectPassword) throws IOException {
        String feedback = incorrectPassword
                ? "<p class=\"error\">Incorrect password. Please try again.</p>"
                : "";
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Protected Image</title><style>body{font-family:system-ui,sans-serif;background:#101827;color:#f8fafc;display:grid;min-height:100vh;place-items:center;margin:0}.card{width:min(90vw,360px);background:#1e293b;padding:2rem;border-radius:1rem}label,input,button{display:block;width:100%;box-sizing:border-box}input,button{margin-top:.5rem;padding:.75rem;border-radius:.5rem;border:0}button{background:#06b6d4;color:#082f49;font-weight:700}.error{color:#fda4af}</style>
                </head><body><main class="card"><h1>Protected image</h1><p>Enter the password to view this image.</p>%s<form method="post"><label for="password">Password</label><input id="password" name="password" type="password" required autofocus><button type="submit">View image</button></form></main></body></html>
                """.formatted(feedback);
        sendHtml(exchange, 401, html);
    }

    private void sendImage(HttpExchange exchange, ImageShare imageShare) throws IOException {
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Type", imageShare.contentType());
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        try (InputStream input = shortUrlService.openImageShare(imageShare)) {
            if (input == null) {
                sendHtml(exchange, 404, buildShortUrlNotFoundPage());
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", imageShare.contentType());
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + imageShare.storageName() + "\"");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, imageShare.sizeBytes());
            input.transferTo(exchange.getResponseBody());
            exchange.close();
        }
    }

    private void sendImageUploadFailure(HttpExchange exchange, ImageShareService.UploadError error) throws IOException {
        if (error == null) {
            sendImageError(exchange, 500, "IMAGE_CREATE_FAILED", "Unable to create image share");
            return;
        }
        switch (error) {
            case DISABLED -> sendImageError(exchange, 503, "IMAGE_SHARING_DISABLED", "Image sharing is disabled");
            case IMAGE_REQUIRED -> sendImageError(exchange, 400, "IMAGE_REQUIRED", "An image file is required");
            case UNSUPPORTED_IMAGE -> sendImageError(exchange, 400, "UNSUPPORTED_IMAGE", "Only PNG, JPEG, GIF, and WebP images are supported");
            case IMAGE_TOO_LARGE -> sendImageError(exchange, 413, "IMAGE_TOO_LARGE", "The uploaded image exceeds the configured size limit");
            case RETENTION_TOO_LONG -> sendImageError(exchange, 400, "RETENTION_TOO_LONG", "The requested retention is outside the allowed range");
            case INVALID_PASSWORD -> sendImageError(exchange, 400, "INVALID_PASSWORD", "Password must contain 4 to 128 characters");
            case CREATE_FAILED -> sendImageError(exchange, 500, "IMAGE_CREATE_FAILED", "Unable to create image share");
        }
    }

    private void sendImageError(HttpExchange exchange, int status, String errorCode, String error) throws IOException {
        sendJson(exchange, status, DataObject.empty().put("error", error).put("errorCode", errorCode).toString());
    }

    private void handleWebAsset(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.startsWith("/web/") || path.contains("..")) {
            sendHtml(exchange, 404, buildShortUrlNotFoundPage());
            return;
        }
        String resourcePath = path;
        try (InputStream in = ShortUrlGatewayServer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                sendHtml(exchange, 404, buildShortUrlNotFoundPage());
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", webAssetContentType(resourcePath));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private String buildShortUrlNotFoundPage() {
        return renderTemplateString(loadTemplate("web/404.html"), Map.of(
                "__NOT_FOUND_KICKER__", "NoRule URL",
                "__NOT_FOUND_TITLE__", "短網址不存在或已失效",
                "__NOT_FOUND_DESCRIPTION__", "短網址不存在或已失效",
                "__NOT_FOUND_ACTION_URL__", "/",
                "__NOT_FOUND_ACTION_TEXT__", "Back to Short URL Home"
        ));
    }

    private boolean isGetOrHead(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private long parseRetentionMillis(String rawHours, ImageShareService.Options options) {
        if (rawHours == null || rawHours.isBlank()) {
            return 0L;
        }
        try {
            long hours = Long.parseLong(rawHours.trim());
            long maxHours = options.maxRetentionMillis() / (60L * 60L * 1000L);
            if (hours < 1L || hours > maxHours) {
                return -1L;
            }
            return Math.multiplyExact(hours, 60L * 60L * 1000L);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private MultipartForm parseMultipartForm(HttpExchange exchange, long maxFileSizeBytes) throws IOException, InvalidMultipartException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = extractMultipartBoundary(contentType);
        if (boundary == null) {
            throw new InvalidMultipartException();
        }
        long requestLimit;
        try {
            requestLimit = Math.addExact(maxFileSizeBytes, MULTIPART_OVERHEAD_BYTES);
        } catch (ArithmeticException e) {
            throw new RequestBodyTooLargeException();
        }
        byte[] body = readBodyLimited(exchange, requestLimit);
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] headersEndMarker = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] nextBoundaryMarker = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        if (!matchesAt(body, boundaryBytes, 0)) {
            throw new InvalidMultipartException();
        }

        List<MultipartPart> parts = new ArrayList<>();
        int position = 0;
        while (position < body.length) {
            if (!matchesAt(body, boundaryBytes, position)) {
                throw new InvalidMultipartException();
            }
            position += boundaryBytes.length;
            if (matchesAt(body, "--".getBytes(StandardCharsets.US_ASCII), position)) {
                return new MultipartForm(parts);
            }
            if (!matchesAt(body, lineBreak, position)) {
                throw new InvalidMultipartException();
            }
            position += lineBreak.length;

            int headersEnd = indexOf(body, headersEndMarker, position);
            if (headersEnd < 0) {
                throw new InvalidMultipartException();
            }
            String headers = new String(body, position, headersEnd - position, StandardCharsets.ISO_8859_1);
            String name = extractPartName(headers);
            if (name == null || name.isBlank()) {
                throw new InvalidMultipartException();
            }
            int contentStart = headersEnd + headersEndMarker.length;
            int nextBoundary = findNextBoundary(body, nextBoundaryMarker, contentStart);
            if (nextBoundary < 0) {
                throw new InvalidMultipartException();
            }
            parts.add(new MultipartPart(name, Arrays.copyOfRange(body, contentStart, nextBoundary)));
            position = nextBoundary + lineBreak.length;
        }
        throw new InvalidMultipartException();
    }

    private String extractMultipartBoundary(String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            return null;
        }
        Matcher matcher = MULTIPART_BOUNDARY.matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        String boundary = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        if (boundary == null || boundary.isBlank() || boundary.length() > 200 || !boundary.chars().allMatch(ch -> ch > 0x20 && ch < 0x7F)) {
            return null;
        }
        return boundary;
    }

    private String extractPartName(String headers) {
        for (String line : headers.split("\\r\\n")) {
            if (!line.regionMatches(true, 0, "Content-Disposition:", 0, "Content-Disposition:".length())) {
                continue;
            }
            Matcher matcher = CONTENT_DISPOSITION_NAME.matcher(line.substring("Content-Disposition:".length()));
            return matcher.find() ? matcher.group(1) : null;
        }
        return null;
    }

    private byte[] readBodyLimited(HttpExchange exchange, long limit) throws IOException, RequestBodyTooLargeException {
        long contentLength = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (contentLength > limit) {
            throw new RequestBodyTooLargeException();
        }
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw new RequestBodyTooLargeException();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private long parseContentLength(String rawContentLength) {
        if (rawContentLength == null || rawContentLength.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(rawContentLength.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private int findNextBoundary(byte[] source, byte[] marker, int fromIndex) {
        int candidate = indexOf(source, marker, fromIndex);
        while (candidate >= 0) {
            int suffix = candidate + marker.length;
            if (matchesAt(source, "--".getBytes(StandardCharsets.US_ASCII), suffix)
                    || matchesAt(source, "\r\n".getBytes(StandardCharsets.US_ASCII), suffix)) {
                return candidate;
            }
            candidate = indexOf(source, marker, candidate + 1);
        }
        return -1;
    }

    private int indexOf(byte[] source, byte[] target, int fromIndex) {
        if (target.length == 0 || source.length < target.length) {
            return -1;
        }
        for (int index = Math.max(0, fromIndex); index <= source.length - target.length; index++) {
            if (matchesAt(source, target, index)) {
                return index;
            }
        }
        return -1;
    }

    private boolean matchesAt(byte[] source, byte[] target, int index) {
        if (index < 0 || index + target.length > source.length) {
            return false;
        }
        for (int offset = 0; offset < target.length; offset++) {
            if (source[index + offset] != target[offset]) {
                return false;
            }
        }
        return true;
    }

    private String loadTemplate(String resourcePath) {
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream input = ShortUrlGatewayServer.class.getResourceAsStream(normalizedPath)) {
            if (input == null) {
                throw new IllegalStateException("Missing short-url template: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load short-url template: " + resourcePath, exception);
        }
    }

    private String renderTemplateString(String template, Map<String, String> replacements) {
        String rendered = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        return rendered;
    }

    private Map<String, String> parseUrlEncoded(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            if (!key.isBlank()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private Map<String, String> parseRequestBody(String body, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            try {
                DataObject json = DataObject.fromJson(body == null ? "{}" : body);
                Map<String, String> map = new HashMap<>();
                map.put("url", json.getString("url", "").trim());
                map.put("customCode", json.getString("customCode", "").trim());
                map.put("code", json.getString("code", "").trim());
                map.put("slug", json.getString("slug", "").trim());
                map.put("password", json.getString("password", ""));
                return map;
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return parseUrlEncoded(body);
    }

    private String urlDecode(String value) {
        return java.net.URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String webAssetContentType(String resourcePath) {
        if (resourcePath.endsWith(".js")) {
            return "text/javascript; charset=UTF-8";
        }
        if (resourcePath.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (resourcePath.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (resourcePath.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        return "text/plain; charset=UTF-8";
    }

    private void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record MultipartPart(String name, byte[] content) {
    }

    private static final class MultipartForm {
        private final List<MultipartPart> parts;

        private MultipartForm(List<MultipartPart> parts) {
            this.parts = parts;
        }

        private String value(String name) {
            for (MultipartPart part : parts) {
                if (name.equals(part.name())) {
                    return new String(part.content(), StandardCharsets.UTF_8).trim();
                }
            }
            return "";
        }

        private byte[] firstFile(String name) {
            for (MultipartPart part : parts) {
                if (name.equals(part.name())) {
                    return part.content();
                }
            }
            return null;
        }
    }

    private static final class RequestBodyTooLargeException extends IOException {
    }

    private static final class InvalidMultipartException extends Exception {
    }
}
