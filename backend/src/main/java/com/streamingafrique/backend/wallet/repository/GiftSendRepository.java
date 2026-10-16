package com.streamingafrique.backend.wallet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.wallet.model.GiftSend;

/**
 * Accès aux envois de cadeaux {gift_sends}. AUCUNE logique métier ici.
 */
public interface GiftSendRepository extends JpaRepository<GiftSend, Long> {

    /** Cadeaux offerts pendant un live (ordre d'envoi). */
    List<GiftSend> findByStreamIdOrderByCreatedAtAsc(Long streamId);

    /** Cadeaux offerts par un utilisateur. */
    List<GiftSend> findBySenderId(Long senderId);

    /**
     * Nombre de cadeaux reçus sur les lives d'une chaîne (dashboard créateur —
     * la chaîne est implicite via le live).
     */
    long countByStreamCreatorId(Long creatorId);

    /** Part totale reversée à la chaîne (coins, figée à l'envoi). */
    @Query("""
            select coalesce(sum(g.creatorShareCoins), 0)
              from GiftSend g
             where g.stream.creator.id = :creatorId
            """)
    long sumShareByStreamCreatorId(@Param("creatorId") Long creatorId);
}
