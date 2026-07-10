package com.norule.musicbot.domain.wordchain;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public final class WordChainState {
    private final boolean enabled;
    private final Long channelId;
    private final String lastWord;
    private final int chainCount;
    private final LinkedHashSet<String> usedWords;
    private final LinkedHashMap<String, WordChainWordUsage> wordUsages;
    private final LinkedHashMap<Long, WordChainPlayerStats> playerStats;

    public WordChainState(boolean enabled,
                          Long channelId,
                          String lastWord,
                          int chainCount,
                          Set<String> usedWords,
                          Map<Long, WordChainPlayerStats> playerStats) {
        this(enabled, channelId, lastWord, chainCount, usedWords, Map.of(), playerStats);
    }

    public WordChainState(boolean enabled,
                          Long channelId,
                          String lastWord,
                          int chainCount,
                          Set<String> usedWords,
                          Map<String, WordChainWordUsage> wordUsages,
                          Map<Long, WordChainPlayerStats> playerStats) {
        this.enabled = enabled;
        this.channelId = channelId;
        this.lastWord = normalizeWord(lastWord);
        this.chainCount = Math.max(0, chainCount);
        this.usedWords = normalizeSet(usedWords);
        this.wordUsages = normalizeUsages(wordUsages, this.usedWords);
        this.playerStats = normalizeStats(playerStats);
    }

    public static WordChainState empty() {
        return new WordChainState(false, null, "", 0, Set.of(), Map.of());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getLastWord() {
        return lastWord;
    }

    public int getChainCount() {
        return chainCount;
    }

    public Set<String> getUsedWords() {
        return new LinkedHashSet<>(usedWords);
    }

    public Map<String, WordChainWordUsage> getWordUsages() {
        return new LinkedHashMap<>(wordUsages);
    }

    public WordChainWordUsage getWordUsage(String word) {
        return wordUsages.get(normalizeWord(word));
    }

    public Map<Long, WordChainPlayerStats> getPlayerStats() {
        return new LinkedHashMap<>(playerStats);
    }

    public WordChainPlayerStats getPlayerStats(long userId) {
        return playerStats.getOrDefault(userId, WordChainPlayerStats.empty());
    }

    public WordChainState withChannelAndEnable(Long nextChannelId) {
        return new WordChainState(true, nextChannelId, lastWord, chainCount, usedWords, wordUsages, playerStats);
    }

    public WordChainState disabled() {
        return new WordChainState(false, null, lastWord, chainCount, usedWords, wordUsages, playerStats);
    }

    public WordChainState resetProgress() {
        return new WordChainState(enabled, channelId, "", 0, Set.of(), Map.of(), playerStats);
    }

    public WordChainState acceptWord(String word) {
        return acceptWord(word, 0L, null);
    }

    public WordChainState acceptWord(String word, long userId, Instant usedAt) {
        String normalized = normalizeWord(word);
        LinkedHashSet<String> nextUsed = new LinkedHashSet<>(usedWords);
        nextUsed.add(normalized);
        LinkedHashMap<String, WordChainWordUsage> nextUsages = new LinkedHashMap<>(wordUsages);
        if (userId > 0L && usedAt != null) {
            nextUsages.putIfAbsent(normalized, new WordChainWordUsage(userId, usedAt));
        }
        return new WordChainState(enabled, channelId, normalized, chainCount + 1, nextUsed, nextUsages, playerStats);
    }

    public WordChainState recordAttempt(long userId, WordChainValidationResult result) {
        if (userId <= 0L || result == null || result == WordChainValidationResult.DICTIONARY_API_ERROR) {
            return this;
        }
        LinkedHashMap<Long, WordChainPlayerStats> nextStats = new LinkedHashMap<>(playerStats);
        WordChainPlayerStats current = nextStats.getOrDefault(userId, WordChainPlayerStats.empty());
        WordChainPlayerStats updated = result == WordChainValidationResult.OK
                ? current.recordSuccess()
                : current.recordInvalid();
        nextStats.put(userId, updated);
        return new WordChainState(enabled, channelId, lastWord, chainCount, usedWords, wordUsages, nextStats);
    }

    private static LinkedHashSet<String> normalizeSet(Set<String> words) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (words == null) {
            return normalized;
        }
        for (String word : words) {
            String one = normalizeWord(word);
            if (!one.isBlank()) {
                normalized.add(one);
            }
        }
        return normalized;
    }

    private static LinkedHashMap<String, WordChainWordUsage> normalizeUsages(
            Map<String, WordChainWordUsage> usages,
            Set<String> acceptedWords
    ) {
        LinkedHashMap<String, WordChainWordUsage> normalized = new LinkedHashMap<>();
        if (usages == null || acceptedWords == null) {
            return normalized;
        }
        for (Map.Entry<String, WordChainWordUsage> entry : usages.entrySet()) {
            String word = normalizeWord(entry.getKey());
            WordChainWordUsage usage = entry.getValue();
            if (word.isBlank()
                    || !acceptedWords.contains(word)
                    || usage == null
                    || usage.userId() <= 0L
                    || usage.usedAt() == null) {
                continue;
            }
            normalized.put(word, new WordChainWordUsage(usage.userId(), usage.usedAt()));
        }
        return normalized;
    }

    private static String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return word.trim().toLowerCase(Locale.ROOT);
    }

    private static LinkedHashMap<Long, WordChainPlayerStats> normalizeStats(Map<Long, WordChainPlayerStats> stats) {
        LinkedHashMap<Long, WordChainPlayerStats> normalized = new LinkedHashMap<>();
        if (stats == null) {
            return normalized;
        }
        for (Map.Entry<Long, WordChainPlayerStats> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0L || entry.getValue() == null) {
                continue;
            }
            WordChainPlayerStats value = entry.getValue();
            normalized.put(entry.getKey(), new WordChainPlayerStats(
                    Math.max(0L, value.totalMessages()),
                    Math.max(0L, value.successCount()),
                    Math.max(0L, value.invalidCount())
            ));
        }
        return normalized;
    }
}
