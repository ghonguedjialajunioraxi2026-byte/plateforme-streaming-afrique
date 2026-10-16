package com.streamingafrique.backend.discovery.dto;

import java.util.List;

import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.video.dto.VideoDto;

/**
 * Découverte Africa+ : contenus classés par SCORE (pas seulement les
 * viewers) + créateurs émergents. Le score combine statut LIVE,
 * audience, récence et activité — jamais de biais artificiel.
 */
public record TrendingResponse(
        List<ScoredStream> streams,
        List<ScoredVideo> videos,
        List<CreatorProfileDto> emergingCreators) {

    public record ScoredStream(StreamDto stream, double score) {
    }

    public record ScoredVideo(VideoDto video, double score) {
    }
}
