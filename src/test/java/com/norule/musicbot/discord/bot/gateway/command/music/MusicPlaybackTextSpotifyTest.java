package com.norule.musicbot.discord.bot.gateway.command.music;

import com.norule.musicbot.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicPlaybackTextSpotifyTest {
    @TempDir
    Path languageDir;

    @Test
    void mapsSpotifyPlaylistFailuresToLocalizedMessages() {
        I18nService i18n = I18nService.load(languageDir, "en");
        MusicPlaybackText text = new MusicPlaybackText(() -> i18n);

        assertEquals(
                "This is a Spotify-generated or personalized playlist, and its tracks cannot currently be "
                        + "accessed. Please copy the tracks to a public playlist you created and try again.",
                text.mapMusicLoadError("en", "SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE")
        );
        assertEquals(
                "This Spotify playlist was recognized, but Spotify prevents third-party apps from reading its tracks. "
                        + "This commonly affects personalized recommendations and Spotify-owned playlists. "
                        + "Copy the tracks to a public playlist you created, then try again.",
                text.mapMusicLoadError("en", "SPOTIFY_RESTRICTED_OR_PERSONALIZED")
        );
        assertEquals(
                "This Spotify playlist currently has no playable tracks.",
                text.mapMusicLoadError("en", "SPOTIFY_PLAYLIST_EMPTY")
        );
        assertEquals(
                "Spotify authentication failed, so the playlist cannot be read right now.",
                text.mapMusicLoadError("en", "SPOTIFY_AUTH_FAILED")
        );
        assertEquals(
                "Spotify is receiving too many requests. Please try again later.",
                text.mapMusicLoadError("en", "SPOTIFY_RATE_LIMITED")
        );
    }

    @Test
    void includesTraditionalAndSimplifiedChineseMessages() {
        I18nService i18n = I18nService.load(languageDir, "zh-TW");
        MusicPlaybackText text = new MusicPlaybackText(() -> i18n);

        assertEquals(
                "這是 Spotify 動態產生或個人化的播放清單，目前無法取得其中的歌曲。請將歌曲複製到你自己建立的公開播放清單後再試。",
                text.mapMusicLoadError("zh-TW", "SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE")
        );
        assertEquals(
                "已辨識此 Spotify 播放清單，但 Spotify 限制第三方應用程式讀取其中的歌曲。這通常發生於個人化推薦或 Spotify 官方播放清單。請將歌曲複製到你自己建立的公開播放清單後再試。",
                text.mapMusicLoadError("zh-TW", "SPOTIFY_RESTRICTED_OR_PERSONALIZED")
        );
        assertEquals(
                "這個 Spotify 播放清單目前沒有可播放的歌曲。",
                text.mapMusicLoadError("zh-TW", "SPOTIFY_PLAYLIST_EMPTY")
        );
        assertEquals(
                "Spotify 驗證失敗，暫時無法讀取播放清單。",
                text.mapMusicLoadError("zh-TW", "SPOTIFY_AUTH_FAILED")
        );
        assertEquals(
                "Spotify 請求過於頻繁，請稍後再試。",
                text.mapMusicLoadError("zh-TW", "SPOTIFY_RATE_LIMITED")
        );
        assertEquals(
                "已识别此 Spotify 播放列表，但 Spotify 限制第三方应用程序读取其中的歌曲。这通常发生于个性化推荐或 Spotify 官方播放列表。请将歌曲复制到你自己创建的公开播放列表后再试。",
                text.mapMusicLoadError("zh-CN", "SPOTIFY_RESTRICTED_OR_PERSONALIZED")
        );
        assertEquals(
                "这个 Spotify 播放列表目前没有可播放的歌曲。",
                text.mapMusicLoadError("zh-CN", "SPOTIFY_PLAYLIST_EMPTY")
        );
        assertEquals(
                "Spotify 验证失败，暂时无法读取播放列表。",
                text.mapMusicLoadError("zh-CN", "SPOTIFY_AUTH_FAILED")
        );
        assertEquals(
                "Spotify 请求过于频繁，请稍后再试。",
                text.mapMusicLoadError("zh-CN", "SPOTIFY_RATE_LIMITED")
        );
        assertEquals(
                "这是 Spotify 动态生成或个性化的播放列表，目前无法获取其中的歌曲。请将歌曲复制到你自己创建的公开播放列表后重试。",
                text.mapMusicLoadError("zh-CN", "AUDIO_SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE")
        );
    }
}
