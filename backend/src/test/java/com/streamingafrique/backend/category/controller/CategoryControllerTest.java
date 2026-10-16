package com.streamingafrique.backend.category.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de la ressource Category sur la chaîne complète :
 * Controller → Service → Repository (base H2 en mémoire).
 *
 * Autorisation (étape 6.25) : le référentiel des catégories n'est modifiable
 * QUE par un ADMIN (compte créé en base avec mot de passe, authentification
 * Basic) ; les lectures restent publiques.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerTest {

    private static final String BASE = "/api/categories";
    private static final String PASSWORD = "motdepasse-6.25";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void reset() {
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User admin() {
        User admin = User.create("admin-cat@example.com", "Admin", passwordEncoder.encode(PASSWORD));
        admin.changeRole(User.Role.ADMIN);
        return userRepository.save(admin);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Long createCategory(String name) {
        return categoryRepository.save(Category.create(name, name.toLowerCase().replace(' ', '-'), "Démo"))
                .getId();
    }

    @Test
    void creer_une_categorie_renvoie_201_et_le_slug_genere() throws Exception {
        String corps = """
                { "name" : "Jeux Vidéo", "description" : "Gaming et esport" }
                """;

        mockMvc.perform(post(BASE)
                        .header("Authorization", basic(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Jeux Vidéo"))
                .andExpect(jsonPath("$.data.slug").value("jeux-video"))  // logique métier : slugify
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void creer_une_categorie_sans_admin_renvoie_403() throws Exception {
        User user = User.create("user-cat@example.com", "Simple user", passwordEncoder.encode(PASSWORD));
        userRepository.save(user);

        mockMvc.perform(post(BASE)
                        .header("Authorization", basic(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name" : "Interdite" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void creer_un_nom_duplique_renvoie_409() throws Exception {
        createCategory("Musique");

        mockMvc.perform(post(BASE)
                        .header("Authorization", basic(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name" : "musique" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void get_by_id_renvoie_200() throws Exception {
        Long id = createCategory("Cinéma");

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Cinéma"));
    }

    @Test
    void get_by_id_inconnu_renvoie_404() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void liste_paginee_renvoie_200_avec_total() throws Exception {
        createCategory("Alpha");
        createCategory("Bravo");
        createCategory("Charlie");

        mockMvc.perform(get(BASE).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void mettre_a_jour_une_categorie_renvoie_200() throws Exception {
        Long id = createCategory("Art");

        mockMvc.perform(put(BASE + "/{id}", id)
                        .header("Authorization", basic(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name" : "Arts visuels", "description" : "Peinture, dessin" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Arts visuels"))
                .andExpect(jsonPath("$.data.slug").value("arts-visuels"));
    }

    @Test
    void mettre_a_jour_une_categorie_inconnue_renvoie_404() throws Exception {
        mockMvc.perform(put(BASE + "/{id}", 9999L)
                        .header("Authorization", basic(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name" : "Objet fantôme" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void supprimer_une_categorie_la_rend_introuvable() throws Exception {
        Long id = createCategory("Sport");

        mockMvc.perform(delete(BASE + "/{id}", id)
                        .header("Authorization", basic(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void nom_vide_renvoie_400_validation_error() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", basic(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name" : "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }
}