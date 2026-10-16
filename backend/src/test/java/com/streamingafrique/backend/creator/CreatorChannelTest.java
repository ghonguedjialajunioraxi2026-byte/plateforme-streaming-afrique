package com.streamingafrique.backend.creator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.11 : table channels ({@code creator_profiles}) — catégorie
 * principale, statut, unicités (H2, transaction annulée après chaque test).
 * Les compteurs (followers, vues, streams) ne sont PAS stockés : calculés
 * via les tables sources — aucun test de colonne compteur ici, volontairement.
 */
@SpringBootTest
@Transactional
class CreatorChannelTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private CategoryRepository categories;

    @Test
    void chaine_creee_active_sans_categorie() {
        User user = users.save(User.create("amina@example.com", "Amina", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(user, "Amina TV", "amina-tv"));

        assertEquals(CreatorProfile.Status.ACTIVE, channel.getStatus());
        assertNull(channel.getPrimaryCategory());
        assertTrue(channels.existsBySlug("amina-tv"));
        assertTrue(channels.existsByUserId(user.getId()));
    }

    @Test
    void categorie_principale_et_statut_modifiables() {
        User user = users.save(User.create("ibou@example.com", "Ibou", "hash"));
        Category jeux = categories.save(Category.create("Jeux", "jeux", "Gaming"));
        CreatorProfile channel = channels.save(CreatorProfile.create(user, "Ibou TV", "ibou-tv"));

        channel.updatePrimaryCategory(jeux);
        channel.changeStatus(CreatorProfile.Status.SUSPENDED);
        channels.saveAndFlush(channel);

        CreatorProfile lu = channels.findBySlug("ibou-tv").orElseThrow();
        assertEquals(jeux.getId(), lu.getPrimaryCategory().getId());
        assertEquals(CreatorProfile.Status.SUSPENDED, lu.getStatus());

        lu.updatePrimaryCategory(null);
        lu.changeStatus(CreatorProfile.Status.CLOSED);
        channels.saveAndFlush(lu);

        CreatorProfile ferme = channels.findByUserId(user.getId()).orElseThrow();
        assertNull(ferme.getPrimaryCategory());
        assertEquals(CreatorProfile.Status.CLOSED, ferme.getStatus());
    }

    @Test
    void slug_duplique_refuse() {
        User u1 = users.save(User.create("u1@example.com", "U1", "hash"));
        User u2 = users.save(User.create("u2@example.com", "U2", "hash"));
        channels.saveAndFlush(CreatorProfile.create(u1, "Chaîne", "doublon"));

        assertThrows(DataIntegrityViolationException.class,
                () -> channels.saveAndFlush(CreatorProfile.create(u2, "Autre", "doublon")));
    }

    @Test
    void deux_chaines_meme_user_refusees() {
        User user = users.save(User.create("solo@example.com", "Solo", "hash"));
        channels.saveAndFlush(CreatorProfile.create(user, "Solo TV", "solo-tv"));

        assertThrows(DataIntegrityViolationException.class,
                () -> channels.saveAndFlush(CreatorProfile.create(user, "Solo 2", "solo-2")));
    }
}
