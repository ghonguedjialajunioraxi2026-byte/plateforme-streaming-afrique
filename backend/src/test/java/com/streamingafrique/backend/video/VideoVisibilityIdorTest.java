package com.streamingafrique.backend.video;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.dto.VideoRequest;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.VideoService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 15 — visibilité et propriété VOD (H2, transaction annulée).
 *
 * Couvre : PRIVATE invisible anonyme (404, existence masquée) mais lisible
 * propriétaire ; modification d'autrui (403 IDOR) ; modérateur privatise ;
 * suppression logique (404 + ligne conservée + clips privatisés) ;
 * supervision ADMIN (403 sinon).
 */
@SpringBootTest
@Transactional
class VideoVisibilityIdorTest {

    @Autowired
    private VideoService videos;
    @Autowired
    private VideoRepository videoRepository;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;

    private User owner;
    private CurrentUser ownerActor;
    private CreatorProfile channel;
    private Video video;

    private void setUp() {
        owner = users.save(User.create("prop15@example.com", "prop15", "hash"));
        owner.changeRole(User.Role.CREATOR);
        ownerActor = CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
        channel = channels.save(CreatorProfile.create(owner, "Prop TV", "prop15"));
        video = videoRepository.save(Video.publish(channel, "VOD", "Desc", null,
                "https://cdn.example.com/v.mp4", null, 600));
    }

    private CurrentUser actorOf(String email, String slug, User.Role role) {
        User user = users.save(User.create(email, slug, "hash"));
        user.changeRole(role);
        return CurrentUser.of(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void priveeInvisibleAnonymeMaisLisibleProprietaire() {
        setUp();
        videos.changeVisibility(video.getId(), Video.Visibility.PRIVATE, ownerActor);

        ApiException anon = assertThrows(ApiException.class, () -> videos.getById(video.getId()));
        assertEquals(404, anon.getStatus().value());

        VideoDto seen = videos.getVisibleById(video.getId(), ownerActor);
        assertEquals(video.getId(), seen.id());
    }

    @Test
    void modificationAutruiInterditeIdor() {
        setUp();
        CurrentUser intruder = actorOf("intrus15@example.com", "intrus15", User.Role.CREATOR);

        ApiException ex = assertThrows(ApiException.class,
                () -> videos.update(video.getId(), new VideoRequest(
                        "Volée", null, null, "https://cdn.example.com/v.mp4", null, 600), intruder));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void moderateurPrivatiseVideoAutrui() {
        setUp();
        CurrentUser moderator = actorOf("modo15@example.com", "modo15", User.Role.MODERATOR);

        VideoDto privatized = videos.changeVisibility(video.getId(), Video.Visibility.PRIVATE, moderator);

        assertEquals(Video.Visibility.PRIVATE, privatized.visibility());
        assertEquals(Video.Visibility.PRIVATE,
                videos.getVisibleById(video.getId(), moderator).visibility());
    }

    @Test
    void suppressionLogiqueConserveLaLigneEtPrivatiseLesClips() {
        setUp();
        videos.delete(video.getId(), ownerActor);

        ApiException ex = assertThrows(ApiException.class, () -> videos.getById(video.getId()));
        assertEquals(404, ex.getStatus().value());
        assertTrue(videoRepository.findById(video.getId()).isPresent());
        assertEquals(0, videos.getPublicFeed(PageRequest.of(0, 10)).totalElements());
    }

    @Test
    void restaurationProprietaireIntrusRefuse() {
        setUp();
        videos.delete(video.getId(), ownerActor);

        VideoDto restored = videos.restore(video.getId(), ownerActor);
        assertEquals(video.getId(), restored.id());
        assertEquals(video.getId(), videos.getById(video.getId()).id());

        CurrentUser intruder = actorOf("intrusb15@example.com", "intrusb15", User.Role.CREATOR);
        ApiException ex = assertThrows(ApiException.class,
                () -> videos.restore(video.getId(), intruder));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void supervisionReserveeAdmin() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> videos.adminList(ownerActor, PageRequest.of(0, 10)));
        assertEquals(403, ex.getStatus().value());

        CurrentUser admin = actorOf("admin15@example.com", "admin15", User.Role.ADMIN);
        assertTrue(videos.adminList(admin, PageRequest.of(0, 10)).totalElements() >= 1);
    }
}
