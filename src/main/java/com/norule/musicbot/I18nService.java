package com.norule.musicbot;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class I18nService {
    private final String defaultLanguage;
    private final Map<String, Map<String, String>> bundles;

    private I18nService(String defaultLanguage, Map<String, Map<String, String>> bundles) {
        this.defaultLanguage = normalize(defaultLanguage);
        this.bundles = bundles;
    }

    public static I18nService load(Path languageDir, String defaultLanguage) {
        Map<String, Map<String, String>> bundles = new HashMap<>();
        try {
            Files.createDirectories(languageDir);
            Files.list(languageDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        int dot = fileName.lastIndexOf('.');
                        String language = dot > 0 ? fileName.substring(0, dot) : fileName;
                        bundles.put(normalize(language), readBundle(path));
                    });
        } catch (IOException ignored) {
        }
        if (!bundles.containsKey("en")) {
            bundles.put("en", Map.of());
        }
        return new I18nService(defaultLanguage, bundles);
    }

    public String normalizeLanguage(String language) {
        return normalize(language);
    }

    public boolean hasLanguage(String language) {
        return bundles.containsKey(normalize(language));
    }

    public String t(String language, String key) {
        String normalized = normalize(language);
        String value = lookup(normalized, key);
        if (value != null) {
            return value;
        }
        value = lookup(defaultLanguage, key);
        if (value != null) {
            return value;
        }
        value = lookup("en", key);
        return value == null ? key : value;
    }

    public String t(String language, String key, Map<String, String> placeholders) {
        String text = t(language, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return text;
    }

    public Map<String, String> getAvailableLanguages() {
        return bundles.keySet().stream().sorted().collect(Collectors.toMap(
                lang -> lang,
                lang -> t(lang, "language.name"),
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readBundle(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Object root = new Yaml().load(in);
            if (root instanceof Map<?, ?> rootMap) {
                Map<String, String> values = new HashMap<>();
                flatten("", (Map<String, Object>) rootMap, values);
                return values;
            }
        } catch (Exception ignored) {
        }
        return Map.of();
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

    private String lookup(String language, String key) {
        Map<String, String> bundle = bundles.get(language);
        return bundle == null ? null : bundle.get(key);
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        return language.trim();
    }
}
