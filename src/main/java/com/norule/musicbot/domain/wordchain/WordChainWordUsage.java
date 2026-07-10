package com.norule.musicbot.domain.wordchain;

import java.time.Instant;

public record WordChainWordUsage(long userId, Instant usedAt) {
}
