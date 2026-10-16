package com.streamingafrique.backend.moderation.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.service.AdminAuditService;
import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.moderation.dto.ChatMessageDto;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.repository.StreamRepository;

/**
 * Modération du chat en direct (étape 17).
 *
 * Historique d'un live + masquage motivé (jamais de suppression physique :
 * le message reste pour dossier, statut HIDDEN). Timeouts et bannissements
 * passent par les sanctions (ReportService.decide) — pas de second système.
 */
@Service
public class ChatModerationService {

    private final ChatMessageRepository messages;
    private final StreamRepository streams;
    private final AdminAuditService audit;

    public ChatModerationService(ChatMessageRepository messages,
                                 StreamRepository streams,
                                 AdminAuditService audit) {
        this.messages = messages;
        this.streams = streams;
        this.audit = audit;
    }

    /** Historique paginé d'un live, anciens d'abord (MODERATOR+). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ChatMessageDto> history(Long streamId, CurrentUser actor,
                                                   Pageable pageable) {
        requireModerator(actor);
        if (!streams.existsById(streamId)) {
            throw ApiException.notFound("Direct introuvable (id=" + streamId + ")");
        }
        var page = messages.findByStreamIdOrderByIdAsc(streamId, pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(ChatMessageDto::from).toList());
    }

    /**
     * Masque un message (motif obligatoire, audité). Idempotent : déjà
     * masqué → succès sans changement.
     */
    @Transactional
    public ChatMessageDto hide(Long messageId, String reason, CurrentUser actor) {
        requireModerator(actor);
        ChatMessage message = messages.findById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message introuvable"));
        if (message.getStatus() != ChatMessage.Status.HIDDEN) {
            message.hide(reason);
            messages.save(message);
            audit.record(actor, "CHAT_MESSAGE_HIDDEN", "CHAT_MESSAGE", message.getId(),
                    ChatMessage.Status.VISIBLE.name(),
                    ChatMessage.Status.HIDDEN.name(), reason);
        }
        return ChatMessageDto.from(message);
    }

    private static void requireModerator(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isModeratorOrAbove()) {
            throw ApiException.forbidden("Modération réservée");
        }
    }
}
