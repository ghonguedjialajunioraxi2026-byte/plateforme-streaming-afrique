package com.streamingafrique.backend.creator.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.dashboard.dto.CreatorAnalyticsDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorCommunityDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorDashboardDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorModerationDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorRevenueDto;
import com.streamingafrique.backend.creator.dashboard.service.DashboardService;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 16 — sécurité et agrégats du Creator Studio (H2, transaction annulée).
 *
 * Couvre : anonyme refusé (403), compte sans chaîne (404 guidant vers la
 * création), isolation entre créateurs (chacun ne voit que SA chaîne — aucun
 * paramètre à falsifier, §17), période invalide (400), et les 5 endpoints
 * en fumée (présence des compteurs, listes bornées, série honnête).
 */
@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboard;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;

    private User owner;
    private CurrentUser ownerActor;
    private CreatorProfile channel;

    private void setUp() {
        owner = users.save(User.create("studio16@example.com", "studio16", "hash"));
        owner.changeRole(User.Role.CREATOR);
        ownerActor = CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
        channel = channels.save(CreatorProfile.create(owner, "Studio TV", "studio16"));
    }

    private CurrentUser actorOf(String email, String slug, User.Role role, boolean withChannel) {
        User user = users.save(User.create(email, slug, "hash"));
        user.changeRole(role);
        if (withChannel) {
            channels.save(CreatorProfile.create(user, "Chaîne " + slug, slug));
        }
        return CurrentUser.of(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void anonymousIsRejected() {
        setUp();
        assertThrows(ApiException.class, () -> dashboard.overview(null));
        assertThrows(ApiException.class, () -> dashboard.revenue(null));
    }

    @Test
    void accountWithoutChannelGetsNotFound() {
        CurrentUser plain = actorOf("plain16@example.com", "plain16", User.Role.USER, false);
        ApiException e = assertThrows(ApiException.class, () -> dashboard.overview(plain));
        assertTrue(e.getMessage().contains("Aucune chaîne"));
    }

    @Test
    void creatorOnlySeesOwnChannel() {
        setUp();
        CurrentUser other = actorOf("other16@example.com", "other16", User.Role.CREATOR, true);

        CreatorDashboardDto mine = dashboard.overview(ownerActor);
        CreatorDashboardDto theirs = dashboard.overview(other);

        assertEquals(channel.getId(), mine.channel().id());
        assertEquals("studio16", mine.channel().slug());
        assertNotNull(theirs.channel());
        assertEquals("other16", theirs.channel().slug());
    }

    @Test
    void overviewCountersArePresent() {
        setUp();
        CreatorDashboardDto dto = dashboard.overview(ownerActor);
        assertEquals(0, dto.followers());
        assertEquals(0, dto.subscribers());
        assertEquals(0, dto.videosTotal());
        assertEquals(0, dto.videoViews());
        assertNull(dto.live());
        assertNull(dto.wallet());
        assertNotNull(dto.recentActivity());
    }

    @Test
    void analyticsRejectsInvalidPeriod() {
        setUp();
        assertThrows(ApiException.class, () -> dashboard.analytics(ownerActor, 0));
        assertThrows(ApiException.class, () -> dashboard.analytics(ownerActor, 91));
    }

    @Test
    void analyticsReturnsHonestEmptySeries() {
        setUp();
        CreatorAnalyticsDto dto = dashboard.analytics(ownerActor, 7);
        assertEquals(7, dto.periodDays());
        assertNotNull(dto.series());
        assertNotNull(dto.topVideos());
        assertNotNull(dto.topClips());
    }

    @Test
    void revenueWithoutWalletIsZeroed() {
        setUp();
        CreatorRevenueDto dto = dashboard.revenue(ownerActor);
        assertNull(dto.wallet());
        assertEquals(0, dto.totalCoinsReceived());
        assertNotNull(dto.recentTransactions());
    }

    @Test
    void communityIsEmptyByDefault() {
        setUp();
        CreatorCommunityDto dto = dashboard.community(ownerActor);
        assertEquals(0, dto.followersCount());
        assertEquals(0, dto.subscribersCount());
        assertNotNull(dto.recentFollowers());
        assertNotNull(dto.recentSubscribers());
    }

    @Test
    void moderationIsEmptyByDefault() {
        setUp();
        CreatorModerationDto dto = dashboard.moderation(ownerActor);
        assertEquals(0, dto.openReports());
        assertNotNull(dto.recentOpenReports());
    }
}
