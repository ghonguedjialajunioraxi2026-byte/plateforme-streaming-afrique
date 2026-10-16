package com.streamingafrique.backend.wallet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.wallet.model.GiftItem;

/**
 * Accès au catalogue des cadeaux {gift_items}. AUCUNE logique métier ici.
 */
public interface GiftItemRepository extends JpaRepository<GiftItem, Long> {

    /** Cadeau par nom. */
    Optional<GiftItem> findByName(String name);

    /** Catalogue proposable à l'envoi. */
    List<GiftItem> findByActiveTrue();
}
