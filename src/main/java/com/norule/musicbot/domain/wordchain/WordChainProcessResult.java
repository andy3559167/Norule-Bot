package com.norule.musicbot.domain.wordchain;

public record WordChainProcessResult(
        boolean handled,
        WordChainValidationResult result,
        String word,
        WordChainWordUsage priorWordUsage,
        Character expectedStartLetter,
        Character nextRequiredStartLetter,
        int chainCount
) {
    public static WordChainProcessResult ignored() {
        return new WordChainProcessResult(false, null, "", null, null, null, 0);
    }
}
