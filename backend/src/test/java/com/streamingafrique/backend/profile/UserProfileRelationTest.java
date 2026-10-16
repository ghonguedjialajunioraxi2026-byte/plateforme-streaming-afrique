package com.streamingafrique.backend.profile;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.10 : relation exacte users ↔ profiles (H2, transaction
 * annulée après chaque test). Vérifie le 1-1, les défauts et le contenu.
 */
@SpringBootTest
@Transactional
class UserProfileRelationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private UserProfileRepository profiles;

    @Test
    void profil_cree_trouve_par_user_avec_defauts() {
        User user = users.save(User.create("ama@example.com", "Ama", "hash"));
        assertFalse(profiles.existsByUserId(user.getId()));

        UserProfile profile = profiles.save(UserProfile.create(user));

        assertTrue(profiles.existsByUserId(user.getId()));
        UserProfile lu = profiles.findByUserId(user.getId()).orElseThrow();
        assertEquals(profile.getId(), lu.getId());
        assertTrue(lu.isNotifyLive());
        assertTrue(lu.isNotifyContent());
        assertTrue(lu.isPublic());
    }

    @Test
    void deuxieme_profil_meme_user_refuse() {
        User user = users.save(User.create("kofi@example.com", "Kofi", "hash"));
        profiles.saveAndFlush(UserProfile.create(user));

        assertThrows(DataIntegrityViolationException.class,
                () -> profiles.saveAndFlush(UserProfile.create(user)));
    }

    @Test
    void profil_sans_user_refuse() {
        assertThrows(Exception.class, () -> profiles.saveAndFlush(UserProfile.create(null)));
    }

    @Test
    void mise_a_jour_presentation_liens_naissance_preferences() {
        User user = users.save(User.create("zia@example.com", "Zia", "hash"));
        UserProfile profile = profiles.save(UserProfile.create(user));

        profile.updatePresentation("Dev Dakar", "SN", "fr");
        profile.updateLinks("https://zia.example.com", "@zia", "zia", "https://youtube.example.com/@zia");
        profile.updateBirthDate(LocalDate.of(1995, 5, 17));
        profile.updatePreferences(false, true, false);
        profiles.saveAndFlush(profile);

        UserProfile lu = profiles.findByUserId(user.getId()).orElseThrow();
        assertEquals("Dev Dakar", lu.getBio());
        assertEquals("SN", lu.getCountryCode());
        assertEquals("fr", lu.getLanguage());
        assertEquals("https://zia.example.com", lu.getWebsiteUrl());
        assertEquals(LocalDate.of(1995, 5, 17), lu.getBirthDate());
        assertFalse(lu.isNotifyLive());
        assertTrue(lu.isNotifyContent());
        assertFalse(lu.isPublic());
    }
}
