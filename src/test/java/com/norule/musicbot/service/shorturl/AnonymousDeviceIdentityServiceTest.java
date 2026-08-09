package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.domain.shorturl.AccessTier;
import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.domain.shorturl.MediaOwnerType;
import com.norule.musicbot.domain.shorturl.MediaStorageState;
import com.norule.musicbot.shorturl.MediaSecurityRepository;
import com.norule.musicbot.shorturl.SqliteImageShareRepository;
import com.norule.musicbot.shorturl.SqliteMediaSecurityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AnonymousDeviceIdentityServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loginAndLogoutPreserveQuotaLineageAndTransferActiveOwnership() {
        Path database = tempDir.resolve("identity.db");
        SqliteImageShareRepository images = new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        AnonymousDeviceIdentityService identities = identityService(security, clock);

        AnonymousDeviceIdentityService.DeviceIdentity anonymous = identities.resolveAnonymous("", "203.0.113.5");
        String group = anonymous.quotaSubject().quotaGroupId();
        images.save(new ImageShare("share1", "share1.png", "image/png", 400L,
                clock.millis(), clock.millis() + 60_000L, "", "hash", 0L,
                MediaStorageState.ACTIVE, "", 0L, MediaOwnerType.ANONYMOUS_DEVICE,
                anonymous.quotaSubject().ownerId(), group,
                anonymous.quotaSubject().deviceIdHash(), anonymous.quotaSubject().ipHash()));
        for (int i = 0; i < 18; i++) {
            security.recordUploadEvent(group, anonymous.quotaSubject().ipHash(), clock.millis(), 1L, true);
        }
        assertEquals(400L, security.activeStorageBytes(group));

        AnonymousDeviceIdentityService.AuthenticationResult login = identities.authenticate(
                anonymous.token(), "123456789", "198.51.100.9");

        assertEquals(MediaSecurityRepository.IdentityMergeStatus.MERGED, login.mergeStatus());
        assertEquals(group, login.quotaSubject().quotaGroupId());
        assertEquals(18L, security.countSuccessfulUploads(group, clock.millis() - 60_000L));
        assertEquals(400L, security.activeStorageBytes(login.quotaSubject().quotaGroupId()));
        ImageShare transferred = images.findByCode("share1");
        assertEquals(MediaOwnerType.DISCORD_USER, transferred.ownerType());
        assertEquals("123456789", transferred.ownerId());

        AnonymousDeviceIdentityService.AuthenticationResult callbackRetry = identities.authenticate(
                anonymous.token(), "123456789", "198.51.100.9");
        assertEquals(MediaSecurityRepository.IdentityMergeStatus.ALREADY_MERGED,
                callbackRetry.mergeStatus());
        assertEquals(18L, security.countSuccessfulUploads(group, clock.millis() - 60_000L));

        AnonymousDeviceIdentityService.DeviceIdentity afterLogout = identities.resolveAnonymous(
                anonymous.token(), "192.0.2.77");
        assertEquals(AccessTier.ANONYMOUS, afterLogout.quotaSubject().accessTier());
        assertEquals(group, afterLogout.quotaSubject().quotaGroupId());
    }

    @Test
    void deviceCookieNotIpControlsIdentityAndSurvivesRestart() {
        Path database = tempDir.resolve("restart.db");
        new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository firstRepository = new SqliteMediaSecurityRepository(database);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        AnonymousDeviceIdentityService firstService = identityService(firstRepository, clock);
        AnonymousDeviceIdentityService.DeviceIdentity first = firstService.resolveAnonymous("", "203.0.113.5");

        AnonymousDeviceIdentityService.DeviceIdentity sameDeviceDifferentIp = firstService.resolveAnonymous(
                first.token(), "198.51.100.20");
        AnonymousDeviceIdentityService.DeviceIdentity sameIpNoCookie = firstService.resolveAnonymous(
                "", "203.0.113.5");

        assertEquals(first.quotaSubject().quotaGroupId(),
                sameDeviceDifferentIp.quotaSubject().quotaGroupId());
        assertNotEquals(first.quotaSubject().quotaGroupId(),
                sameIpNoCookie.quotaSubject().quotaGroupId());

        SqliteMediaSecurityRepository restartedRepository = new SqliteMediaSecurityRepository(database);
        AnonymousDeviceIdentityService restarted = identityService(restartedRepository, clock);
        assertEquals(first.quotaSubject().quotaGroupId(),
                restarted.resolveAnonymous(first.token(), "192.0.2.10").quotaSubject().quotaGroupId());
    }

    @Test
    void rapidAccountSwitchDoesNotTransferAccountAMediaToAccountB() {
        Path database = tempDir.resolve("switch.db");
        SqliteImageShareRepository images = new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        AnonymousDeviceIdentityService identities = identityService(security, clock);
        AnonymousDeviceIdentityService.DeviceIdentity anonymous = identities.resolveAnonymous("", "203.0.113.5");
        String group = anonymous.quotaSubject().quotaGroupId();
        images.save(new ImageShare("share2", "share2.png", "image/png", 100L,
                clock.millis(), clock.millis() + 60_000L, "", "hash", 0L,
                MediaStorageState.ACTIVE, "", 0L, MediaOwnerType.ANONYMOUS_DEVICE,
                anonymous.quotaSubject().ownerId(), group,
                anonymous.quotaSubject().deviceIdHash(), anonymous.quotaSubject().ipHash()));

        identities.authenticate(anonymous.token(), "account-a", "203.0.113.5");
        AnonymousDeviceIdentityService.AuthenticationResult accountB = identities.authenticate(
                anonymous.token(), "account-b", "203.0.113.5");

        assertEquals(MediaSecurityRepository.IdentityMergeStatus.ACCOUNT_SWITCH_BLOCKED,
                accountB.mergeStatus());
        assertEquals(group, accountB.quotaSubject().quotaGroupId());
        assertEquals("account-a", images.findByCode("share2").ownerId());
    }

    @Test
    void loginLogoutLoopCannotRefreshDailyLimit() {
        Path database = tempDir.resolve("quota-loop.db");
        new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        AnonymousDeviceIdentityService identities = identityService(security, clock);
        AnonymousDeviceIdentityService.DeviceIdentity anonymous = identities.resolveAnonymous("", "203.0.113.5");
        for (int i = 0; i < 18; i++) {
            security.recordUploadEvent(anonymous.quotaSubject().quotaGroupId(),
                    anonymous.quotaSubject().ipHash(), clock.millis() - 60L * 60L * 1000L, 1L, true);
        }
        var authenticated = identities.authenticate(anonymous.token(), "account-a", "203.0.113.5");
        for (int i = 0; i < 10; i++) {
            security.recordUploadEvent(authenticated.quotaSubject().quotaGroupId(),
                    authenticated.quotaSubject().ipHash(), clock.millis() - 30L * 60L * 1000L, 1L, true);
        }

        var afterLogout = identities.resolveAnonymous(anonymous.token(), "203.0.113.5");
        MediaQuotaService quota = new MediaQuotaService(security, MediaQuotaService.Options.defaults(), clock);

        assertEquals(28L, security.countSuccessfulUploads(afterLogout.quotaSubject().quotaGroupId(),
                clock.millis() - 2L * 60L * 60L * 1000L));
        assertEquals(MediaQuotaService.Rejection.DAILY_LIMIT,
                quota.checkUpload(afterLogout.quotaSubject(), 1L, 60_000L));
    }

    @Test
    void mergeAddsAnonymousEventsToExistingDiscordUsage() {
        Path database = tempDir.resolve("sum-events.db");
        new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        AnonymousDeviceIdentityService identities = identityService(security, clock);
        var existingAccount = identities.authenticate("", "account-a", "198.51.100.1");
        for (int i = 0; i < 30; i++) {
            security.recordUploadEvent(existingAccount.quotaSubject().quotaGroupId(),
                    existingAccount.quotaSubject().ipHash(), clock.millis(), 1L, true);
        }
        var anonymous = identities.resolveAnonymous("", "203.0.113.5");
        for (int i = 0; i < 20; i++) {
            security.recordUploadEvent(anonymous.quotaSubject().quotaGroupId(),
                    anonymous.quotaSubject().ipHash(), clock.millis(), 1L, true);
        }

        var merged = identities.authenticate(anonymous.token(), "account-a", "203.0.113.5");

        assertEquals(existingAccount.quotaSubject().quotaGroupId(),
                merged.quotaSubject().quotaGroupId());
        assertEquals(50L, security.countSuccessfulUploads(
                merged.quotaSubject().quotaGroupId(), clock.millis() - 60_000L));
    }

    private AnonymousDeviceIdentityService identityService(MediaSecurityRepository repository,
                                                           Clock clock) {
        return new AnonymousDeviceIdentityService(repository,
                AnonymousDeviceIdentityService.Options.defaults(),
                "quota-test-secret", "device-test-secret", clock);
    }
}
