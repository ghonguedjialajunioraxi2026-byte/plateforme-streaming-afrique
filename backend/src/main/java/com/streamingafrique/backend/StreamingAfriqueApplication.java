package com.streamingafrique.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale du backend.
 *
 * Le package racine est {@code com.streamingafrique.backend}.
 * Spring Boot l'utilise comme point de départ :
 * toutes les classes situées EN DESSOUS de ce package
 * seront automatiquement détectées (contrôleurs, services, etc.).
 *
 * @SpringBootApplication = raccourci pour :
 *   - @Configuration        → déclare des beans Spring
 *   - @EnableAutoConfiguration → configure automatiquement les dépendances
 *   - @ComponentScan        → détecte les @Component/@Service/@Controller
 */
@SpringBootApplication
public class StreamingAfriqueApplication {

    public static void main(String[] args) {
        // Démarre l'application : initialise le contexte Spring
        // puis lance le serveur HTTP embarqué (Tomcat) sur le port configuré.
        SpringApplication.run(StreamingAfriqueApplication.class, args);
    }
}