package com.norule.musicbot.domain.discord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class DiscordEmbedSanitizer {
    public static final int TITLE_MAX_LENGTH = 256;
    public static final int DESCRIPTION_MAX_LENGTH = 4096;
    public static final int FIELD_COUNT_MAX = 25;
    public static final int FIELD_NAME_MAX_LENGTH = 256;
    public static final int FIELD_VALUE_MAX_LENGTH = 1024;
    public static final int FOOTER_MAX_LENGTH = 2048;
    public static final int AUTHOR_MAX_LENGTH = 256;
    public static final int EMBED_TOTAL_MAX_LENGTH = 6000;
    public static final String EMPTY_FALLBACK = "\u2014";
    private static final String ELLIPSIS = "\u2026";

    private DiscordEmbedSanitizer() {
    }

    public static String sanitizeTitle(String value) {
        return sanitizeRequired(value, TITLE_MAX_LENGTH);
    }

    public static String sanitizeDescription(String value) {
        return sanitizeRequired(value, DESCRIPTION_MAX_LENGTH);
    }

    public static String sanitizeFieldName(String value) {
        return sanitizeRequired(value, FIELD_NAME_MAX_LENGTH);
    }

    public static String sanitizeFieldValue(String value) {
        return sanitizeRequired(value, FIELD_VALUE_MAX_LENGTH);
    }

    public static String sanitizeFooter(String value) {
        return sanitizeRequired(value, FOOTER_MAX_LENGTH);
    }

    public static String sanitizeAuthor(String value) {
        return sanitizeRequired(value, AUTHOR_MAX_LENGTH);
    }

    public static String truncate(String input, int maxLength) {
        if (input == null || maxLength <= 0) {
            return "";
        }
        if (input.length() <= maxLength) {
            return input;
        }
        int cutoff = maxLength - ELLIPSIS.length();
        if (cutoff <= 0) {
            return ELLIPSIS.substring(0, maxLength);
        }
        if (cutoff < input.length()
                && Character.isHighSurrogate(input.charAt(cutoff - 1))
                && Character.isLowSurrogate(input.charAt(cutoff))) {
            cutoff--;
        }
        return input.substring(0, cutoff) + ELLIPSIS;
    }

    public static String joinWithinLimit(Collection<String> items,
                                         int maxLength,
                                         String separator,
                                         Function<Integer, String> overflowText) {
        if (maxLength <= 0) {
            return "";
        }
        if (items == null || items.isEmpty()) {
            return truncate(EMPTY_FALLBACK, maxLength);
        }
        List<String> safeItems = items.stream()
                .map(item -> sanitizeRequired(item, maxLength))
                .toList();
        String safeSeparator = separator == null ? "" : separator;
        StringBuilder prefix = new StringBuilder();
        String best = truncate(safeOverflowText(overflowText, safeItems.size()), maxLength);
        int bestIncluded = 0;

        for (int index = 0; index < safeItems.size(); index++) {
            if (index > 0) {
                prefix.append(safeSeparator);
            }
            prefix.append(safeItems.get(index));
            int omitted = safeItems.size() - index - 1;
            String candidate = prefix.toString();
            if (omitted > 0) {
                candidate += safeSeparator + safeOverflowText(overflowText, omitted);
            }
            if (candidate.length() <= maxLength) {
                best = candidate;
                bestIncluded = index + 1;
            }
        }

        if (bestIncluded == 0 && safeItems.size() > 1) {
            String overflow = truncate(safeOverflowText(overflowText, safeItems.size() - 1), maxLength);
            int availableForFirst = maxLength - safeSeparator.length() - overflow.length();
            if (availableForFirst > 0) {
                return truncate(safeItems.get(0), availableForFirst) + safeSeparator + overflow;
            }
        }
        return best;
    }

    public static int calculateTotalLength(EmbedText embed) {
        if (embed == null) {
            return 0;
        }
        int length = length(embed.title())
                + length(embed.description())
                + length(embed.footer())
                + length(embed.author());
        for (Field field : embed.fields()) {
            length += length(field.name()) + length(field.value());
        }
        return length;
    }

    public static EmbedText sanitizeToTotalLimit(EmbedText embed) {
        if (embed == null) {
            return new EmbedText(null, null, null, null, List.of());
        }
        int remaining = EMBED_TOTAL_MAX_LENGTH;
        String title = fitOptional(embed.title(), TITLE_MAX_LENGTH, remaining);
        remaining -= length(title);
        String author = fitOptional(embed.author(), AUTHOR_MAX_LENGTH, remaining);
        remaining -= length(author);
        String description = fitOptional(embed.description(), DESCRIPTION_MAX_LENGTH, remaining);
        remaining -= length(description);

        List<Field> fields = new ArrayList<>();
        for (Field field : embed.fields()) {
            if (fields.size() >= FIELD_COUNT_MAX || remaining < 2) {
                break;
            }
            String name = sanitizeFieldName(field.name());
            String value = sanitizeFieldValue(field.value());
            name = truncate(name, Math.min(FIELD_NAME_MAX_LENGTH, remaining - 1));
            remaining -= name.length();
            value = truncate(value, Math.min(FIELD_VALUE_MAX_LENGTH, remaining));
            remaining -= value.length();
            fields.add(new Field(name, value, field.inline()));
        }

        String footer = fitOptional(embed.footer(), FOOTER_MAX_LENGTH, remaining);
        return new EmbedText(title, description, footer, author, fields);
    }

    private static String sanitizeRequired(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? EMPTY_FALLBACK : value;
        return truncate(safe, maxLength);
    }

    private static String safeOverflowText(Function<Integer, String> overflowText, int omitted) {
        if (overflowText == null) {
            return ELLIPSIS;
        }
        String value = overflowText.apply(omitted);
        return value == null || value.isBlank() ? ELLIPSIS : value;
    }

    private static String fitOptional(String value, int itemLimit, int remaining) {
        if (value == null || remaining <= 0) {
            return null;
        }
        if (value.isBlank()) {
            return truncate(EMPTY_FALLBACK, Math.min(itemLimit, remaining));
        }
        return truncate(value, Math.min(itemLimit, remaining));
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    public record Field(String name, String value, boolean inline) {
    }

    public record EmbedText(String title,
                            String description,
                            String footer,
                            String author,
                            List<Field> fields) {
        public EmbedText {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }
}
