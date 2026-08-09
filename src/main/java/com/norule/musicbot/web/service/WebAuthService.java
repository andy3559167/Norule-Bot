package com.norule.musicbot.web.service;

import com.norule.musicbot.web.infra.WebControlServer;
import com.norule.musicbot.web.infra.WebSettings;
import com.norule.musicbot.web.session.WebSessionManager;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

public final class WebAuthService {
    private static final long OAUTH_STATE_TTL_MILLIS = 5 * 60_000L;
    private static final String ANONYMOUS_DEVICE_COOKIE = "nr_anon_device";

    private final WebControlServer owner;
    private final WebSessionService webSessionService;

    public WebAuthService(WebControlServer owner, WebSessionService webSessionService) {
        this.owner = owner;
        this.webSessionService = webSessionService;
    }

    public void handleAuthLogin(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            owner.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        WebSettings web = owner.webSettings();
        String state = UUID.randomUUID().toString().replace("-", "");
        webSessionService.putOAuthState(state, System.currentTimeMillis() + OAUTH_STATE_TTL_MILLIS);

        String authorizeUrl = "https://discord.com/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + owner.encode(web.getDiscordClientId())
                + "&scope=" + owner.encode("identify guilds")
                + "&redirect_uri=" + owner.encode(web.getDiscordRedirectUri())
                + "&state=" + owner.encode(state);

        owner.redirect(exchange, authorizeUrl);
    }

    public void handleAuthCallback(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            owner.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = owner.parseUrlEncoded(exchange.getRequestURI().getRawQuery());
        String state = query.getOrDefault("state", "");
        String code = query.getOrDefault("code", "");
        if (code.isBlank() || state.isBlank()) {
            owner.sendText(exchange, 400, "Missing code/state");
            return;
        }

        WebSessionManager.OAuthState stateData = webSessionService.popOAuthState(state);
        if (stateData == null || stateData.expiresAtMillis < System.currentTimeMillis()) {
            owner.sendText(exchange, 401, "OAuth state expired");
            return;
        }

        WebSettings web = owner.webSettings();
        try {
            String accessToken = owner.discordOAuthClient().exchangeToken(
                    web.getDiscordClientId(),
                    web.getDiscordClientSecret(),
                    web.getDiscordRedirectUri(),
                    code
            );
            var me = owner.discordOAuthClient().fetchMe(accessToken);
            String userId = me.getString("id", "");
            String username = me.getString("username", "");
            String avatarUrl = owner.buildAvatarUrl(me);
            if (userId.isBlank()) {
                owner.sendText(exchange, 401, "Failed to get user profile");
                return;
            }

            try {
                var mediaIdentity = owner.shortUrlService().authenticateMediaIdentity(
                        readCookie(exchange, ANONYMOUS_DEVICE_COOKIE), userId, clientAddress(exchange));
                if (mediaIdentity != null && mediaIdentity.mergeStatus()
                        == com.norule.musicbot.shorturl.MediaSecurityRepository.IdentityMergeStatus.ACCOUNT_SWITCH_BLOCKED) {
                    System.err.println("[NoRule] MEDIA_DEVICE_ACCOUNT_SWITCH discordUserId=" + userId);
                }
            } catch (RuntimeException identityFailure) {
                System.err.println("[NoRule] Media identity merge failed after OAuth login: "
                        + identityFailure.getClass().getSimpleName());
            }

            long ttlMillis = Math.max(5, web.getSessionExpireMinutes()) * 60_000L;
            String sessionId = UUID.randomUUID().toString().replace("-", "");
            webSessionService.putSession(exchange, sessionId, userId, username, avatarUrl, accessToken,
                    System.currentTimeMillis() + ttlMillis, owner.isSecureCookie(web), web.getSessionExpireMinutes());
            owner.redirect(exchange, owner.resolveHomeUrl(web));
        } catch (Exception e) {
            owner.sendText(exchange, 401, "OAuth failed: " + e.getMessage());
        }
    }

    public void handleAuthLogout(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            owner.sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        webSessionService.clearSessionCookie(exchange, owner.isSecureCookie(owner.webSettings()));
        owner.redirect(exchange, "/");
    }

    public void handleApiMe(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            owner.sendJson(exchange, 405, net.dv8tion.jda.api.utils.data.DataObject.empty().put("error", "Method Not Allowed"));
            return;
        }
        WebSessionManager.WebSession session = webSessionService.requireSession(exchange);
        if (session == null) {
            owner.sendJson(exchange, 401, net.dv8tion.jda.api.utils.data.DataObject.empty().put("error", "Unauthorized"));
            return;
        }
        owner.sendJson(exchange, 200, net.dv8tion.jda.api.utils.data.DataObject.empty()
                .put("id", session.userId)
                .put("username", session.username)
                .put("avatarUrl", session.avatarUrl));
    }

    private String readCookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null || header.isBlank()) {
            return "";
        }
        for (String part : header.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && name.equals(pair[0].trim())) {
                return pair[1].trim();
            }
        }
        return "";
    }

    private String clientAddress(HttpExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && remoteAddress != null
                && remoteAddress.getAddress() != null
                && (remoteAddress.getAddress().isLoopbackAddress()
                || remoteAddress.getAddress().isSiteLocalAddress())) {
            return forwarded.split(",", 2)[0].trim();
        }
        return remoteAddress == null || remoteAddress.getAddress() == null
                ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }
}
