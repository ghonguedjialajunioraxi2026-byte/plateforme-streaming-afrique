package com.streamingafrique.backend.ai.dto;

/** Proposition textuelle unique (description, résumé…). */
public record TextProposalResponse(String proposal, String provider, String model) {
}
