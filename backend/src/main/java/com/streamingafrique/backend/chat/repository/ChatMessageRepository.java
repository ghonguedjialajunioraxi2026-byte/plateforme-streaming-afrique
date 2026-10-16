package com.streamingafrique.backend.chat.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.chat.model.ChatMessage;

/**
 * Accès à l'historique du chat {chat_messages}.
 *
 * Lecture keyset ({@code id > ?}) pour l'historique — jamais d'OFFSET
 * profond sur un salon à fort volume. AUCUNE logique métier ici.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Historique d'un salon, plus anciens d'abord (paginé). */
    Page<ChatMessage> findByStreamIdOrderByIdAsc(Long streamId, Pageable pageable);

    /** Historique PUBLIC d'un salon : messages visibles uniquement. */
    Page<ChatMessage> findByStreamIdAndStatusOrderByIdAsc(Long streamId, ChatMessage.Status status,
                                                          Pageable pageable);

    /** Messages après un curseur (rattrapage temps réel / polling de secours). */
    List<ChatMessage> findByStreamIdAndIdGreaterThanOrderByIdAsc(Long streamId, Long afterId,
                                                                 Pageable pageable);

    /** Volume d'un salon (stats, purge, modération). */
    long countByStreamId(Long streamId);
}
