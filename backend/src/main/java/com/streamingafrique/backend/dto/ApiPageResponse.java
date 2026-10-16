package com.streamingafrique.backend.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Enveloppe de pagination, utilisée comme {@code data} d'une ApiResponse
 * dès qu'un endpoint renvoie une liste (lives, vidéos, catégories...).
 *
 * Sert aussi de "contrat de page" pour le frontend : la réponse expose
 * page/size courants, totalElements/totalPages, les drapeaux first/last
 * et le nombre d'éléments réellement renvoyés.
 *
 * @param <T> type d'un élément de la page (un DTO)
 */
public record ApiPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements
) {

    /**
     * Construit la page depuis un {@link Page} Spring Data dont le contenu
     * a déjà été mappé en DTO.
     *
     * @param page page Spring Data brute (totalElements + pageNumber + size)
     * @param content éléments de la page courante (déjà convertis en DTO)
     */
    public static <T> ApiPageResponse<T> from(Page<?> page, List<T> content) {
        return of(content, page.getPageable().getPageNumber(),
                page.getPageable().getPageSize(), page.getTotalElements());
    }

    /**
     * Calcule automatiquement totalPages, first, last et numberOfElements.
     *
     * @param content       éléments de la page courante
     * @param page          numéro de page (0 = première, convention Spring Data)
     * @param size          taille d'une page
     * @param totalElements nombre total d'éléments sur toutes les pages
     */
    public static <T> ApiPageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean first = page <= 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new ApiPageResponse<>(content, page, size, totalElements, totalPages,
                first, last, content.size());
    }
}