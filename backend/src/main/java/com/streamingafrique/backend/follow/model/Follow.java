package com.streamingafrique.backend.follow.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "Follow" → table {@code follows}.
 *
 * RÔLE : le SUIVI social pur — "je suis ce créateur, préviens-moi de ses
 * prochains directs". Aucune notion d'argent ici : les abonnements payants
 * (Subscription) et les cadeaux sont HORS SCOPE pour l'instant.
 *
 * Contraintes :
 *   - unique (follower_id, followed_id) : un utilisateur ne peut suivre
 *     le même créateur qu'UNE seule fois ;
 *   - index sur chacun des deux sens : "qui suis-je ?" (list follower_id)
 *     et "qui me suit ?" (list followed_id) sont les deux interrogations
 *     fréquentes — les deux colonnes sont indexées.
 *
 * Relation 1-N UNIDIRECTIONNELLE (liaison pure, sans collection inverse).
 */
@Entity
@Table(name = "follows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_follows_follower_followed", columnNames = {"follower_id", "followed_id"})
}, indexes = {
        @Index(name = "idx_follows_follower", columnList = "follower_id"),
        @Index(name = "idx_follows_followed", columnList = "followed_id")
})
public class Follow extends BaseEntity {

    /** Le spectateur qui suit — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** Le créateur suivi — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followed_id", nullable = false)
    private CreatorProfile followed;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Follow() {
    }

    /** Fabrique : la création passe TOUJOURS par ici pour garantir la cohérence. */
    public static Follow create(User follower, CreatorProfile followed) {
        Follow follow = new Follow();
        follow.follower = follower;
        follow.followed = followed;
        return follow;
    }

    public User getFollower() {
        return follower;
    }

    public CreatorProfile getFollowed() {
        return followed;
    }
}