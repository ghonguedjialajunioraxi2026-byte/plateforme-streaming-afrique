package com.streamingafrique.backend.category.dto;

/**
 * DTO de SORTIE — ce que l'API renvoie au client.
 * Jamais d'entité JPA exposée directement.
 */
public record CategoryDto(Long id, String name, String slug, String description) {}