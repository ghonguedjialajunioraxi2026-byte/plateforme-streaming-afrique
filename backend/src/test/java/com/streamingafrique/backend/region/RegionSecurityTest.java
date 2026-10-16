package com.streamingafrique.backend.region;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 19 — sécurité Africa+ : référentiels publics (200 anonyme),
 * écritures ADMIN uniquement (USER → 403, anonyme → 401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegionSecurityTest {

    private static final String PASSWORD = "motdepasse-19-region";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private com.streamingafrique.backend.region.repository.CountryRepository
            countryRepository;

    /** Référentiel minimal (seed Flyway absent en tests). */
    @org.junit.jupiter.api.BeforeEach
    void seed() {
        if (countryRepository.count() == 0) {
            countryRepository.save(
                    com.streamingafrique.backend.region.model.Country.create(
                            "CM", "Cameroun", "CENTRAL", "237", "XAF",
                            "fr-CM", "fr,en", "Africa/Douala"));
            countryRepository.save(
                    com.streamingafrique.backend.region.model.Country.create(
                            "NG", "Nigeria", "WEST", "234", "NGN",
                            "en-NG", "en", "Africa/Lagos"));
        }
    }

    private String basic(String email) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private String account(String email, User.Role role) {
        User user = User.create(email, email, passwords.encode(PASSWORD));
        user.changeRole(role);
        users.save(user);
        return basic(email);
    }

    @Test
    void publicReadsAreOpen() throws Exception {
        mockMvc.perform(get("/api/countries")).andExpect(status().isOk());
        mockMvc.perform(get("/api/countries/CM")).andExpect(status().isOk());
        mockMvc.perform(get("/api/locales")).andExpect(status().isOk());
        mockMvc.perform(get("/api/currencies")).andExpect(status().isOk());
        mockMvc.perform(get("/api/config/region")).andExpect(status().isOk());
        mockMvc.perform(get("/api/payment-methods?country=CM"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discovery/trending"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discovery/trending/NG"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discovery/creators/emerging"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownCountryIs404() throws Exception {
        mockMvc.perform(get("/api/countries/XX"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/discovery/trending/XX"))
                .andExpect(status().isNotFound());
    }

    @Test
    void regionAdminWritesAreAdminOnly() throws Exception {
        String user = account("r19user@example.com", User.Role.USER);

        mockMvc.perform(put("/api/admin/region/countries/CM")
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/region/countries/CM")
                .header("Authorization", user)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanToggleCountry() throws Exception {
        String admin = account("r19admin@example.com", User.Role.ADMIN);

        mockMvc.perform(put("/api/admin/region/countries/CM")
                .header("Authorization", admin)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/region/countries/CM")
                .header("Authorization", admin)
                .contentType("application/json")
                .content("{\"active\":true}"))
                .andExpect(status().isOk());
    }
}
