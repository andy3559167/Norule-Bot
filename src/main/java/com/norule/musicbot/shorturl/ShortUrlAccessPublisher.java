package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ShortUrlAccessEvent;

@FunctionalInterface
public interface ShortUrlAccessPublisher {
    ShortUrlAccessPublisher NO_OP = (channelId, event) -> {
        // Short URL logging is optional until a publisher and notification channel are configured.
    };

    void publish(long channelId, ShortUrlAccessEvent event);
}
