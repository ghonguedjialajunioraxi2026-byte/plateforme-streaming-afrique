package com.streamingafrique.backend.beta.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.beta.model.BetaFeedback;

public interface BetaFeedbackRepository extends JpaRepository<BetaFeedback, Long> {

    Page<BetaFeedback> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    Page<BetaFeedback> findByStatusOrderByCreatedAtDesc(BetaFeedback.Status status, Pageable pageable);
}
