package com.streamingafrique.backend.chat.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.moderation.dto.ChatMessageDto;
import com.streamingafrique.backend.stream.repository.StreamRepository;

/**
 * Historique PUBLIC du chat d'un direct (FINALISATION) : derniers messages
 * VISIBLES, anciens d'abord. Les messages masqués (HIDDEN) n'y apparaissent
 * jamais — ils restent visibles en modération uniquement.
 */
@Service
public class ChatHistoryService {

    private final ChatMessageRepository messages;
    private final StreamRepository streams;

    public ChatHistoryService(ChatMessageRepository messages, StreamRepository streams) {
        this.messages = messages;
        this.streams = streams;
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<ChatMessageDto> recent(Long streamId, int size) {
        if (!streams.existsById(streamId)) {
            throw ApiException.notFound("Direct introuvable (id=" + streamId + ")");
        }
        int pageSize = Math.min(Math.max(size, 1), 50);
        var page = messages.findByStreamIdAndStatusOrderByIdAsc(streamId, ChatMessage.Status.VISIBLE,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.ASC, "id")));
        return ApiPageResponse.from(page,
                page.getContent().stream().map(ChatMessageDto::from).toList());
    }
}
