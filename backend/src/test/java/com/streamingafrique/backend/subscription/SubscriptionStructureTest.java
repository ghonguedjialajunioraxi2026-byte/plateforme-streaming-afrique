package com.streamingafrique.backend.subscription;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.subscription.model.Subscription;
import com.streamingafrique.backend.subscription.model.SubscriptionPlan;
import com.streamingafrique.backend.subscription.repository.SubscriptionPlanRepository;
import com.streamingafrique.backend.subscription.repository.SubscriptionRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.16 : follow GRATUIT vs abonnement PAYANT strictement séparés
 * (H2, transaction annulée). Follow = table follows + paire unique, sans
 * argent. Abonnement = plan + périodes + statuts, historique conservé.
 */
@SpringBootTest
@Transactional
class SubscriptionStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private FollowRepository follows;
    @Autowired
    private SubscriptionPlanRepository plans;
    @Autowired
    private SubscriptionRepository subscriptions;

    private CreatorProfile channel(String email, String slug) {
        User user = users.save(User.create(email, slug, "hash"));
        return channels.save(CreatorProfile.create(user, slug + " TV", slug));
    }

    @Test
    void follow_gratuit_sans_abonnement_possible_et_doublon_refuse() {
        User fan = users.save(User.create("fan@example.com", "fan", "hash"));
        CreatorProfile channel = channel("star@example.com", "star");

        follows.saveAndFlush(Follow.create(fan, channel));
        // Suivre ne crée AUCUN abonnement : les deux concepts sont disjoints.
        assertTrue(subscriptions.findBySubscriberIdAndStatus(fan.getId(), Subscription.Status.ACTIVE).isEmpty());

        assertThrows(DataIntegrityViolationException.class, () ->
                follows.saveAndFlush(Follow.create(fan, channel)));
    }

    @Test
    void abonnement_sans_follow_possible_cycle_resiliation_expiration() {
        User fan = users.save(User.create("soutien@example.com", "soutien", "hash"));
        CreatorProfile channel = channel("vedette@example.com", "vedette");
        SubscriptionPlan tier1 = plans.save(SubscriptionPlan.create("Tier 1", 499, 30, "Badge"));
        Instant debut = Instant.now();
        Instant fin = debut.plusSeconds(30L * 86400);

        Subscription abo = subscriptions.save(
                Subscription.subscribe(fan, channel, tier1, debut, fin, true));
        assertEquals(Subscription.Status.ACTIVE, abo.getStatus());
        assertTrue(subscriptions.existsBySubscriberIdAndCreatorIdAndStatus(
                fan.getId(), channel.getId(), Subscription.Status.ACTIVE));
        // S'abonner ne crée AUCUN follow.
        assertFalse(follows.existsByFollowerIdAndFollowedId(fan.getId(), channel.getId()));

        abo.cancel();
        subscriptions.saveAndFlush(abo);
        assertEquals(Subscription.Status.CANCELLED,
                subscriptions.findById(abo.getId()).orElseThrow().getStatus());

        // Réabonnement = nouvelle ligne (historique), l'ancienne reste.
        Subscription abo2 = subscriptions.save(
                Subscription.subscribe(fan, channel, tier1, fin, fin.plusSeconds(30L * 86400), false));
        abo2.markExpired();
        subscriptions.saveAndFlush(abo2);
        assertEquals(Subscription.Status.EXPIRED,
                subscriptions.findById(abo2.getId()).orElseThrow().getStatus());
        assertEquals(2, subscriptions.findBySubscriberIdAndStatus(
                fan.getId(), Subscription.Status.CANCELLED).size()
                + subscriptions.findBySubscriberIdAndStatus(
                        fan.getId(), Subscription.Status.EXPIRED).size());
    }

    @Test
    void plan_invalide_et_periode_inversee_refuses() {
        assertThrows(IllegalArgumentException.class,
                () -> SubscriptionPlan.create("Gratuit", 0, 30, null));
        assertThrows(IllegalArgumentException.class,
                () -> SubscriptionPlan.create("Négatif", 100, 0, null));

        User fan = users.save(User.create("plan@example.com", "plan", "hash"));
        CreatorProfile channel = channel("planstar@example.com", "planstar");
        SubscriptionPlan tier1 = plans.save(SubscriptionPlan.create("Tier 1", 499, 30, null));
        Instant debut = Instant.now();
        assertThrows(IllegalArgumentException.class, () ->
                Subscription.subscribe(fan, channel, tier1, debut, debut.minusSeconds(60), true));
    }
}
