package com.streamingafrique.backend.stats.dto;

import java.time.Instant;

/**
 * DTO de SORTIE — statistique « directs en cours ».
 *
 * {@code measuredAt} date à laquelle la valeur a été calculée (depuis la
 * base) : affichée telle quelle même si la valeur provient du cache, pour
 * que le frontend sache l'âge du chiffre.
 */
public record LiveStreamsCountDto(long liveStreams, Instant measuredAt) {
}