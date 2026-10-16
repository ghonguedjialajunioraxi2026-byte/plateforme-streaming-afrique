package com.streamingafrique.backend.clip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.ClipRequest;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Étape 15, §40-41 — lien court de partage {@code /c/{shareSlug}} (H2, transaction annulée).
 *
 * Couvre : résolution anonyme d'un clip PUBLIC, 404 sur slug inconnu,
 * PRIVATE masqué en anonyme (existence non révélée) mais lisible par le
 * propriétaire connecté.
 */
@SpringBootTest
@Transactional
class ClipShareSlugTest {

    @Autowired
    private ClipService clips;
    @Autowired
    private ClipRepository clipRepository;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private VideoRepository videos;

    private User owner;
    private CurrentUser ownerActor;
    private CreatorProfile channel;
    private Video video;

    private void setUp() {
        owner = users.save(User.create("slug15@example.com", "slug15", "hash"));
        owner.changeRole(User.Role.CREATOR);
        ownerActor = CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
        channel = channels.save(CreatorProfile.create(owner, "Slug TV", "slug15"));
        video = videos.save(Video.publish(channel, "VOD source", "Desc", null,
                "https://cdn.example.com/v.mp4", null, 3600));
    }

    private ClipDto createClip() {
        setUp();
        return clips.createFromVideo(channel.getId(), video.getId(),
                new ClipRequest("Moment", "https://cdn.example.com/c.mp4", null, 25),
                ownerActor);
    }

    @Test
    void resolvesPublicClipByShareSlugAnonymously() {
        ClipDto created = createClip();
        assertNotNull(created.shareSlug());

        ClipDto resolved = clips.getVisibleByShareSlug(created.shareSlug(), null);
        assertEquals(created.id(), resolved.id());
        assertEquals(created.shareSlug(), resolved.shareSlug());
    }

    @Test
    void unknownShareSlugIsNotFound() {
        createClip();
        assertThrows(ApiException.class,
                () -> clips.getVisibleByShareSlug("xxxxxxxxxx", null));
    }

    @Test
    void privateClipHiddenFromAnonymousButVisibleToOwner() {
        ClipDto created = createClip();
        Clip clip = clipRepository.findById(created.id()).orElseThrow();
        clip.changeVisibility(Clip.Visibility.PRIVATE);
        clipRepository.save(clip);

        assertThrows(ApiException.class,
                () -> clips.getVisibleByShareSlug(created.shareSlug(), null));

        ClipDto resolved = clips.getVisibleByShareSlug(created.shareSlug(), ownerActor);
        assertEquals(created.id(), resolved.id());
    }
}
