package com.norule.musicbot.domain.discord;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEmbedSanitizerTest {
    @Test
    void constantsMatchJda65ValidationLimits() {
        assertEquals(MessageEmbed.TITLE_MAX_LENGTH, DiscordEmbedSanitizer.TITLE_MAX_LENGTH);
        assertEquals(MessageEmbed.DESCRIPTION_MAX_LENGTH, DiscordEmbedSanitizer.DESCRIPTION_MAX_LENGTH);
        assertEquals(MessageEmbed.MAX_FIELD_AMOUNT, DiscordEmbedSanitizer.FIELD_COUNT_MAX);
        assertEquals(MessageEmbed.TITLE_MAX_LENGTH, DiscordEmbedSanitizer.FIELD_NAME_MAX_LENGTH);
        assertEquals(MessageEmbed.VALUE_MAX_LENGTH, DiscordEmbedSanitizer.FIELD_VALUE_MAX_LENGTH);
        assertEquals(MessageEmbed.TEXT_MAX_LENGTH, DiscordEmbedSanitizer.FOOTER_MAX_LENGTH);
        assertEquals(MessageEmbed.AUTHOR_MAX_LENGTH, DiscordEmbedSanitizer.AUTHOR_MAX_LENGTH);
        assertEquals(MessageEmbed.EMBED_MAX_LENGTH_BOT, DiscordEmbedSanitizer.EMBED_TOTAL_MAX_LENGTH);
    }

    @Test
    void leavesExactlySizedFieldValueUnchanged() {
        String value = "a".repeat(1024);

        assertEquals(value, DiscordEmbedSanitizer.sanitizeFieldValue(value));
    }

    @Test
    void truncatesEveryIndividualEmbedTextLimit() {
        assertEquals(1024, DiscordEmbedSanitizer.sanitizeFieldValue("a".repeat(1025)).length());
        assertEquals(256, DiscordEmbedSanitizer.sanitizeTitle("a".repeat(257)).length());
        assertEquals(4096, DiscordEmbedSanitizer.sanitizeDescription("a".repeat(4097)).length());
        assertEquals(256, DiscordEmbedSanitizer.sanitizeFieldName("a".repeat(257)).length());
        assertEquals(2048, DiscordEmbedSanitizer.sanitizeFooter("a".repeat(2049)).length());
        assertEquals(256, DiscordEmbedSanitizer.sanitizeAuthor("a".repeat(257)).length());
    }

    @Test
    void doesNotSplitEmojiSurrogatePairAtBoundary() {
        String result = DiscordEmbedSanitizer.truncate("a".repeat(1022) + "\uD83D\uDE00x", 1024);

        assertTrue(result.length() <= 1024);
        assertFalse(hasUnpairedSurrogate(result));
        assertTrue(result.endsWith("\u2026"));
    }

    @Test
    void replacesNullEmptyAndWhitespaceRequiredValues() {
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeFieldName(null));
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeFieldValue(""));
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeTitle("   "));
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeDescription(null));
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeFooter("\t"));
        assertEquals("\u2014", DiscordEmbedSanitizer.sanitizeAuthor(null));
    }

    @Test
    void keepsShortRoleListComplete() {
        List<String> roles = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> "<@&" + index + ">")
                .toList();

        String result = DiscordEmbedSanitizer.joinWithinLimit(
                roles,
                1024,
                ", ",
                count -> "\u2026and " + count + " more roles"
        );

        assertEquals(String.join(", ", roles), result);
    }

    @Test
    void summarizesLargeRoleListWithinFieldLimit() {
        List<String> roles = java.util.stream.IntStream.range(0, 200)
                .mapToObj(index -> "<@&123456789012345" + index + ">")
                .toList();

        String result = DiscordEmbedSanitizer.joinWithinLimit(
                roles,
                1024,
                "\n",
                count -> "\u2026and " + count + " more roles"
        );

        assertTrue(result.length() <= 1024);
        assertTrue(result.matches("(?s).*\u2026and \\d+ more roles$"));
        assertTrue(result.startsWith(roles.get(0)));
    }

    @Test
    void truncatesOneOversizedRoleSafely() {
        String result = DiscordEmbedSanitizer.joinWithinLimit(
                List.of("x".repeat(1100) + "\uD83D\uDE00"),
                1024,
                ", ",
                count -> "\u2026and " + count + " more roles"
        );

        assertEquals(1024, result.length());
        assertFalse(hasUnpairedSurrogate(result));
    }

    @Test
    void enforcesFieldCountAndTotalTextLimit() {
        List<DiscordEmbedSanitizer.Field> fields = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            fields.add(new DiscordEmbedSanitizer.Field("n".repeat(300), "v".repeat(1200), false));
        }
        DiscordEmbedSanitizer.EmbedText sanitized = DiscordEmbedSanitizer.sanitizeToTotalLimit(
                new DiscordEmbedSanitizer.EmbedText(
                        "t".repeat(300),
                        "d".repeat(5000),
                        "f".repeat(3000),
                        "a".repeat(300),
                        fields
                )
        );

        assertTrue(sanitized.fields().size() <= 25);
        assertTrue(DiscordEmbedSanitizer.calculateTotalLength(sanitized) <= 6000);
        assertTrue(sanitized.fields().stream().allMatch(field -> field.name().length() <= 256));
        assertTrue(sanitized.fields().stream().allMatch(field -> field.value().length() <= 1024));
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
