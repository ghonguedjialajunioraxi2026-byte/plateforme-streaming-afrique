package com.streamingafrique.backend.beta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.beta.model.BetaInvitation;

public interface BetaInvitationRepository extends JpaRepository<BetaInvitation, Long> {

    Optional<BetaInvitation> findByCodeHash(String codeHash);

    List<BetaInvitation> findByEmailOrderByCreatedAtDesc(String email);

    Page<BetaInvitation> findByStatusOrderByCreatedAtDesc(BetaInvitation.Status status, Pageable pageable);
}
