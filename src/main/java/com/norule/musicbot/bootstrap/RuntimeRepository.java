package com.norule.musicbot.bootstrap;

import java.util.Arrays;

enum RuntimeRepository {
    MAVEN_CENTRAL("central", "https://repo.maven.apache.org/maven2"),
    LAVALINK_RELEASES("lavalink-releases", "https://maven.lavalink.dev/releases"),
    TOPIWTF_RELEASES("topiwtf-releases", "https://maven.topi.wtf/releases");

    private final String id;
    private final String baseUrl;

    RuntimeRepository(String id, String baseUrl) {
        this.id = id;
        this.baseUrl = baseUrl;
    }

    String id() {
        return id;
    }

    String baseUrl() {
        return baseUrl;
    }

    static RuntimeRepository fromId(String id) {
        return Arrays.stream(values())
                .filter(repository -> repository.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown runtime dependency repository ID: " + id));
    }
}
