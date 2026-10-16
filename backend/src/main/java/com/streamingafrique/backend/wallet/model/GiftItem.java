package com.streamingafrique.backend.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "GiftItem" → table {@code gift_items}.
 *
 * RÔLE : le CATALOGUE des cadeaux (stickers animés offerts en live).
 * Prix en coins BIGINT (unités minimales). Désactivation sans suppression
 * si déjà offert (les envois {@link GiftSend} restent valides).
 */
@Entity
@Table(name = "gift_items", indexes = {
        @Index(name = "uk_gift_items_name", columnList = "name", unique = true)
})
public class GiftItem extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Prix unitaire en coins (strictement positif). */
    @Column(nullable = false)
    private long priceCoins;

    /** Référence d'animation (asset front) — NULL = aucune. */
    @Column(length = 200)
    private String animationRef;

    /** Proposable à l'envoi. */
    @Column(nullable = false)
    private boolean active = true;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected GiftItem() {
    }

    /** Fabrique : prix strictement positif. */
    public static GiftItem create(String name, long priceCoins, String animationRef) {
        if (priceCoins <= 0) {
            throw new IllegalArgumentException("Le prix en coins doit être strictement positif");
        }
        GiftItem item = new GiftItem();
        item.name = name;
        item.priceCoins = priceCoins;
        item.animationRef = animationRef;
        item.active = true;
        return item;
    }

    /** Retrait du catalogue (envois passés inchangés). */
    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public long getPriceCoins() {
        return priceCoins;
    }

    public String getAnimationRef() {
        return animationRef;
    }

    public boolean isActive() {
        return active;
    }
}
