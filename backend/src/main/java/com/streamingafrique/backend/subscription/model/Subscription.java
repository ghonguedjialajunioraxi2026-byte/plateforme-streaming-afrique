package com.streamingafrique.backend.subscription.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "Subscription" → table {@code subscriptions}.
 *
 * RÔLE : l'abonnement PAYANT d'un utilisateur à une chaîne — à ne JAMAIS
 * confondre avec {@code follows} (suivi GRATUIT, table {@code follows}) :
 * suivre ne coûte rien et ne donne aucun avantage monétisé ; s'abonner
 * paie une formule ({@link SubscriptionPlan}) pour une période, avec
 * renouvellement et expiration. Les deux peuvent coexister
 * indépendamment (abonné sans follow, suiveur sans abonnement).
 *
 * Cycle de vie par statuts (jamais de DELETE) : ACTIVE → CANCELLED
 * (résilié, court jusqu'au terme) / EXPIRED (terme échu). Un réabonnement
 * = une NOUVELLE ligne (historique complet). L'unicité « un seul ACTIVE
 * par (abonné, chaîne) » est garantie par le service + un futur index
 * partiel DDL (impossible en pur JPA) — pas de contrainte UNIQUE globale
 * qui interdirait l'historique.
 *
 * Paiements futurs : chaque échéance sera soldée par des lignes de
 * {@code transactions} référençant cet abonnement (pas de FK monétaire
 * ici : cette table porte le droit, pas l'argent).
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_subscriber", columnList = "subscriber_id"),
        @Index(name = "idx_subscriptions_subscriber_status", columnList = "subscriber_id,status"),
        @Index(name = "idx_subscriptions_creator", columnList = "creator_id"),
        @Index(name = "idx_subscriptions_creator_status", columnList = "creator_id,status")
})
public class Subscription extends BaseEntity {

    /** Statut du droit (voir cycle de vie ci-dessus). */
    public enum Status {
        ACTIVE,
        CANCELLED,
        EXPIRED
    }

    /** L'abonné payeur — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;

    /** La chaîne soutenue — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    /** La formule souscrite (prix figé par référence) — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    /** Statut par défaut : actif. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Période en cours (fin strictement après début). */
    @Column(nullable = false)
    private Instant currentPeriodStart;

    @Column(nullable = false)
    private Instant currentPeriodEnd;

    /** Reconduction automatique à l'échéance. */
    @Column(nullable = false)
    private boolean autoRenew = true;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Subscription() {
    }

    /** Fabrique : période ordonnée obligatoire. */
    public static Subscription subscribe(User subscriber, CreatorProfile creator, SubscriptionPlan plan,
                                         Instant periodStart, Instant periodEnd, boolean autoRenew) {
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("La fin de période doit être strictement après le début");
        }
        Subscription subscription = new Subscription();
        subscription.subscriber = subscriber;
        subscription.creator = creator;
        subscription.plan = plan;
        subscription.currentPeriodStart = periodStart;
        subscription.currentPeriodEnd = periodEnd;
        subscription.autoRenew = autoRenew;
        subscription.status = Status.ACTIVE;
        return subscription;
    }

    /** Résiliation (ACTIVE → CANCELLED ; le droit court jusqu'au terme). */
    public void cancel() {
        if (this.status == Status.ACTIVE) {
            this.status = Status.CANCELLED;
        }
    }

    /** Constat d'échéance (ACTIVE → EXPIRED). */
    public void markExpired() {
        if (this.status == Status.ACTIVE) {
            this.status = Status.EXPIRED;
        }
    }

    public User getSubscriber() {
        return subscriber;
    }

    public CreatorProfile getCreator() {
        return creator;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }
}
