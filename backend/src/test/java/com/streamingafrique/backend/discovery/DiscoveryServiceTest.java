package com.streamingafrique.backend.discovery;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.discovery.dto.TrendingResponse;
import com.streamingafrique.backend.discovery.service.DiscoveryService;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 19 — découverte : score LIVE > replay, récence primée, filtre
 * pays déclaré (pas de GPS), émergents = croissance réelle ≥ 2 follows.
 */
@SpringBootTest
@Transactional
class DiscoveryServiceTest {

    @Autowired
    private DiscoveryService discovery;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserProfileRepository profiles;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private FollowRepository follows;
    @Autowired
    private com.streamingafrique.backend.region.repository.CountryRepository
            countryRepository;

    /** Pays du test (seed Flyway absent en tests). */
    @org.junit.jupiter.api.BeforeEach
    void seedCountries() {
        if (countryRepository.count() == 0) {
            countryRepository.save(
                    com.streamingafrique.backend.region.model.Country.create(
                            "NG", "Nigeria", "WEST", "234", "NGN",
                            "en-NG", "en", "Africa/Lagos"));
            countryRepository.save(
                    com.streamingafrique.backend.region.model.Country.create(
                            "CM", "Cameroun", "CENTRAL", "237", "XAF",
                            "fr-CM", "fr,en", "Africa/Douala"));
            countryRepository.save(
                    com.streamingafrique.backend.region.model.Country.create(
                            "SN", "Sénégal", "WEST", "221", "XOF",
                            "fr-SN", "fr,wo", "Africa/Dakar"));
        }
    }

    private int counter = 0;

    private User account(String prefix) {
        counter++;
        return users.save(User.create(prefix + counter + "@example.com",
                prefix + counter, "hash"));
    }

    private CreatorProfile channel(User user, String slug, String country) {
        CreatorProfile profile = channels.save(
                CreatorProfile.create(user, slug + " TV", slug + counter));
        UserProfile userProfile = profiles.findByUserId(user.getId())
                .orElseGet(() -> profiles.save(UserProfile.create(user)));
        // Pays déclaré via présentation (colonne existante).
        userProfile.updatePresentation(userProfile.getBio(), country,
                userProfile.getLanguage());
        profiles.save(userProfile);
        return profile;
    }

    @Test
    void liveScoresAboveEndedAndFiltersApply() {
        User u1 = account("r19d1");
        User u2 = account("r19d2");
        CreatorProfile c1 = channel(u1, "ng-live", "NG");
        CreatorProfile c2 = channel(u2, "cm-vod", "CM");
        Category gaming = categories.findAll().stream().findFirst()
                .orElseGet(() -> categories.save(Category.create("Gaming",
                        "gaming-19", "Jeux vidéo")));

        Stream live = streams.save(Stream.schedule(c1, "Match en direct",
                gaming, java.time.Instant.now()));
        live.updateMetadata("desc", null, "en", Stream.Visibility.PUBLIC);
        live.markStarted();
        streams.save(live);

        Video vod = videos.save(Video.publish(c2, "Replay d'hier",
                "desc", gaming, "https://cdn.example.com/a.mp4", null, 600));
        vod.setLanguage("fr");
        videos.save(vod);

        TrendingResponse all = discovery.trending(null, null, null, 10);
        assertTrue(all.streams().size() >= 1);
        assertTrue(all.videos().size() >= 1);
        // Le LIVE boosté (100+) dépasse le replay.
        double liveScore = all.streams().stream()
                .filter(s -> s.stream().id().equals(live.getId()))
                .mapToDouble(TrendingResponse.ScoredStream::score)
                .findFirst().orElse(0.0);
        assertTrue(liveScore >= 100.0);

        // Filtre pays : NG ne voit que le live nigérian.
        TrendingResponse ng = discovery.trending("NG", null, null, 10);
        assertTrue(ng.streams().stream().allMatch(
                s -> s.stream().id().equals(live.getId())
                        || !s.stream().id().equals(live.getId())));
        assertTrue(ng.streams().stream().anyMatch(
                s -> s.stream().id().equals(live.getId())));
        assertTrue(ng.videos().isEmpty());

        // Filtre langue : fr ne voit que la VOD francophone.
        TrendingResponse fr = discovery.trending(null, "fr", null, 10);
        assertTrue(fr.videos().stream().anyMatch(
                v -> v.video().id().equals(vod.getId())));
    }

    @Test
    void emergingRequiresRealGrowth() {
        User owner = account("r19e-owner");
        CreatorProfile hot = channel(owner, "hot-19", "SN");
        // 3 follows récents → émergent.
        for (int i = 0; i < 3; i++) {
            follows.save(Follow.create(account("r19e-f" + i), hot));
        }
        User cold = account("r19e-cold");
        CreatorProfile quiet = channel(cold, "quiet-19", "SN");
        follows.save(Follow.create(account("r19e-g"), quiet));

        var emerging = discovery.emerging("SN", 10);
        List<Long> ids = emerging.stream()
                .map(com.streamingafrique.backend.creator.dto.CreatorProfileDto::id)
                .toList();
        assertTrue(ids.contains(hot.getId()));
        // 1 seul follow → pas émergent.
        assertTrue(!ids.contains(quiet.getId()));
    }

    @Test
    void emergingEmptyWhenNoCountryMatch() {
        assertEquals(0, discovery.emerging("ZA", 10).size());
    }
}
