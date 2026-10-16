package com.streamingafrique.backend.common.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de démonstration, uniquement pour tester la validation d'entrée
 * (endpoint POST /api/test/errors/validation).
 *
 * À supprimer avec le reste des endpoints de test en production.
 */
public record DemoRequest(

        @NotBlank(message = "Le champ name est obligatoire")
        String name
) {}