package com.norule.musicbot.i18n;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGameStatusTranslationsTest {
    private static final List<String> GAME_STATUS_KEYS = List.of(
            "number_chain.status_title",
            "number_chain.status_desc",
            "number_chain.rules_summary",
            "word_chain.status_title",
            "word_chain.status_desc",
            "word_chain.rules_summary"
    );

    @Test
    void defaultLanguageBundlesContainGameStatusEmbedText() throws IOException {
        for (String resourcePath : List.of(
                "defaults/lang/zh-TW.yml",
                "defaults/lang/zh-CN.yml",
                "defaults/lang/en.yml"
        )) {
            Map<String, String> bundle = loadBundle(resourcePath);
            for (String key : GAME_STATUS_KEYS) {
                assertTrue(bundle.containsKey(key), () -> resourcePath + " is missing " + key);
                assertFalse(bundle.get(key).isBlank(), () -> resourcePath + " has blank " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadBundle(String resourcePath) throws IOException {
        try (InputStream in = DefaultGameStatusTranslationsTest.class
                .getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            Object root = new Yaml().load(in);
            Map<String, String> values = new HashMap<>();
            if (root instanceof Map<?, ?> rootMap) {
                flatten("", (Map<String, Object>) rootMap, values);
            }
            return values;
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                flatten(key, (Map<String, Object>) child, target);
            } else {
                target.put(key, value == null ? "" : String.valueOf(value));
            }
        }
    }
}
