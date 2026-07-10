package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;

import java.util.List;

public interface ImageShareRepository {
    ImageShare findByCode(String code);

    void save(ImageShare imageShare);

    void deleteByCode(String code);

    List<ImageShare> findExpired(long nowMillis);
}
