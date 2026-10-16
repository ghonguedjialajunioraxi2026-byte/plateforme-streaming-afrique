package com.streamingafrique.backend.subscription.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "SubscriptionPlan" → table {@code subscription_plans}.
 *
 * RÔLE : le CATALOGUE des formules d'abonnement payant (ex. Tier 1/2/3).
 * Évite de dupliquer prix/durée/avantages dans chaque abonnement : la
 * formule est figée par référence ({@code plan_id}), son prix en coins
 * est la vérité au moment de la souscription (le fiat vit dans payments).
 *
 * Une formule ne se supprime jamais si un abonnement l'a utilisée :
 * on la désactive ({@code active = false}).
 */
@Entity
@Table(name = "subscription_plans", indexes = {
        @Index(name = "uk_subscription_plans_name", columnList = "name", unique = true)
})
public class SubscriptionPlan extends BaseEntity {

    /** Nom commercial de la formule (ex. "Tier 1"). */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Prix par période, en coins (plus petite unité, jamais de flottant). */
    @Column(nullable = false)
    private long priceCoins;

    /** Durée d'une période en jours (ex. 30). */
    @Column(nullable = false)
    private int durationDays;

    /** Avantages inclus (texte libre : badge, salon réservé, VOD anticipée…). */
    @Column(length = 2000)
    private String perks;

    /** Formule proposable à la vente. */
    @Column(nullable = false)
    private boolean active = true;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected SubscriptionPlan() {
    }

    /** Fabrique : prix et durée strictement positifs. */
    public static SubscriptionPlan create(String name, long priceCoins, int durationDays, String perks) {
        if (priceCoins <= 0) {
            throw new IllegalArgumentException("Le prix en coins doit être strictement positif");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("La durée en jours doit être strictement positive");
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.name = name;
        plan.priceCoins = priceCoins;
        plan.durationDays = durationDays;
        plan.perks = perks;
        plan.active = true;
        return plan;
    }

    /** Retrait de la vente (les abonnements existants restent valides). */
    public void deactivate() {
        this.active = false;
    }

    /** Remise en vente. */
    public void reactivate() {
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public long getPriceCoins() {
        return priceCoins;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getPerks() {
        return perks;
    }

    public boolean isActive() {
        return active;
    }
}
