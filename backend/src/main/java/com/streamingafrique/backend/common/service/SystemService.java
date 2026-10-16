package com.streamingafrique.backend.common.service;

import com.streamingafrique.backend.common.dto.HealthDto;
import com.streamingafrique.backend.common.dto.InfoDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Endpoints système (santé + informations générales).
 * Aucune donnée sensible : juste l'état de l'application.
 */
@Service
public class SystemService {

    private final Environment environment;
    private final BuildProperties buildProperties;

    /** Moment où le service Spring a démarré (= démarrage de l'application). */
    private final Instant startedAt = Instant.now();

    @Value("${app.display-name:FLICK}")
    private String displayName;

    @Value("${app.description:Plateforme de streaming en direct}")
    private String description;

    @Value("${app.timezone:UTC}")
    private String timezone;

    /**
     * ObjectProvider : BuildProperties n'est présent que si META-INF/build-info.properties
     * a été généré par le goal build-info de Maven. On tolère son absence.
     */
    public SystemService(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties.getIfAvailable();
    }

    /** GET /api/health → 200 {"status":"UP","application":"FLICK"} */
    public HealthDto health() {
        return HealthDto.up(displayName);
    }

    /** GET /api/info → informations générales non sensibles. */
    public InfoDto info() {
        return new InfoDto(
                displayName,
                description,
                version(),
                builtAt(),
                System.getProperty("java.version", "inconnu"),
                timezone,
                activeProfiles(),
                startedAt,
                uptimeSeconds()
        );
    }

    /** Version du jar (issue du pom.xml via build-info), sinon "dev". */
    private String version() {
        return buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    /** Date de build du jar, null si informations absentes. */
    private Instant builtAt() {
        return buildProperties != null ? buildProperties.getTime() : null;
    }

    /** Profils actifs au démarrage (Variable SPRING_PROFILES_ACTIVE / --spring.profiles.active). */
    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? List.of(profiles) : List.of("default");
    }

    /** Durée d'activité du processus, en secondes. */
    private long uptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }
}