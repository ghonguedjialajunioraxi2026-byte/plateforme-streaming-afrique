package com.streamingafrique.backend.db;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.seed.ReferenceDataSeeder;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.subscription.model.Subscription;
import com.streamingafrique.backend.subscription.model.SubscriptionPlan;
import com.streamingafrique.backend.subscription.repository.SubscriptionPlanRepository;
import com.streamingafrique.backend.subscription.repository.SubscriptionRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.wallet.model.GiftItem;
import com.streamingafrique.backend.wallet.model.GiftSend;
import com.streamingafrique.backend.wallet.model.Wallet;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.GiftItemRepository;
import com.streamingafrique.backend.wallet.repository.GiftSendRepository;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOUS-ÉTAPE 7.26 — intégrité sur VRAI PostgreSQL 16 (Testcontainers,
 * image déjà en cache, base scratch "integrity" détruite avec le conteneur).
 *
 * Flyway migre V1..V21 au boot (ddl-auto=validate) : les 21 migrations, les
 * 27 tables et toutes les contraintes testées ici sont CELLES des fichiers
 * SQL, pas du DDL Hibernate. Volontairement : doublons, références
 * inexistantes, montants invalides, relations invalides, colonnes NOT NULL
 * absentes — PostgreSQL doit refuser, l'applicatif ne doit jamais écrire
 * d'incohérence.
 */
@Testcontainers
@SpringBootTest
@Transactional
class PostgresIntegrityTest {

    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "integrity")
            .withEnv("POSTGRES_USER", "integrity")
            .withEnv("POSTGRES_PASSWORD", "integrity-test-only")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));

    /** Prioritaire sur la propriété système surefire (flyway désactivé en tests H2). */
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        String host = POSTGRES.getHost();
        Integer port = POSTGRES.getMappedPort(5432);
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + host + ":" + port + "/integrity");
        registry.add("spring.datasource.username", () -> "integrity");
        registry.add("spring.datasource.password", () -> "integrity-test-only");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ClipRepository clips;
    @Autowired
    private FollowRepository follows;
    @Autowired
    private SubscriptionPlanRepository plans;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private WalletTransactionRepository transactions;
    @Autowired
    private GiftItemRepository gifts;
    @Autowired
    private GiftSendRepository sends;
    @Autowired
    private AppConfigRepository configs;
    @Autowired
    private com.streamingafrique.backend.payment.repository.CoinPackageRepository packs;

    private User compte(String email) {
        return users.save(User.create(email, email.split("@")[0], "hash"));
    }

    private CreatorProfile chaine(String email, String slug) {
        return channels.save(CreatorProfile.create(compte(email), slug + " TV", slug));
    }

    // ---------- Migrations & structure ----------

    @Test
    void migrations_26_appliquees_34_tables_creees() {
        Integer migrees = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertEquals(26, migrees);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                Integer.class);
        assertEquals(34, tables);
    }

    @Test
    void cles_primaires_et_etrangeres_presentes() {
        Integer pk = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'p'", Integer.class);
        assertTrue(pk >= 24, "une PK par table, or " + pk);
        Integer fkStreams = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = 'streams'::regclass AND contype = 'f'",
                Integer.class);
        assertEquals(2, fkStreams, "streams : creator + category");
        Integer fkVideos = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = 'videos'::regclass AND contype = 'f'",
                Integer.class);
        assertEquals(3, fkVideos, "videos : creator + category + origin_stream");
    }

    @Test
    void index_cles_presents() {
        List<String> index = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname IN "
                        + "('uk_users_email', 'idx_chat_stream_id', 'idx_tx_wallet_id', "
                        + "'idx_notifications_recipient_read', 'uk_tx_idempotency', "
                        + "'gin_videos_title_trgm')",
                String.class);
        assertEquals(6, index.size(), "index manquants : " + index);
    }

    @Test
    void auth_tables_presentes_et_email_non_verifie_par_defaut() {
        Integer colonnes = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name IN "
                        + "('auth_sessions', 'email_tokens')",
                Integer.class);
        assertTrue(colonnes >= 15, "colonnes auth manquantes : " + colonnes);
        // email_verified = false par défaut, même en insertion SQL brute.
        jdbc.update("INSERT INTO users (created_at, updated_at, email, username, display_name, role, status)"
                + " VALUES (now(), now(), 'brut@example.com', 'brut', 'Brut', 'USER', 'ACTIVE')");
        Boolean verified = jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE email = 'brut@example.com'", Boolean.class);
        assertEquals(Boolean.FALSE, verified);
    }

    // ---------- Négatifs : PostgreSQL refuse ----------

    @Test
    void unique_refuse_doublon_email() {
        compte("dup@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(User.create("dup@example.com", "x", "h")));
    }

    // Note PostgreSQL : une erreur SQL aborte TOUTE la transaction — chaque test
    // négatif ci-dessous place donc SA violation en DERNIER (rollback en fin de test).

    @Test
    void unique_refuse_doublon_follow() {
        User fan = compte("fan-int@example.com");
        CreatorProfile chaine = chaine("star-int@example.com", "star-int");
        follows.saveAndFlush(Follow.create(fan, chaine));
        assertThrows(DataIntegrityViolationException.class,
                () -> follows.saveAndFlush(Follow.create(fan, chaine)));
    }

    @Test
    void auth_refuse_hash_session_duplique_et_type_token_invalide() {
        User fan = compte("sess-int@example.com");
        jdbc.update("INSERT INTO auth_sessions (created_at, updated_at, user_id, token_hash,"
                        + " last_used_at, expires_at) VALUES (now(), now(), ?, ?, now(), now() + interval '7 days')",
                fan.getId(), "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        // Même empreinte → UNIQUE violée (pas deux sessions sur un hash).
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO auth_sessions (created_at, updated_at, user_id, token_hash,"
                        + " last_used_at, expires_at) VALUES (now(), now(), ?, ?, now(), now() + interval '7 days')",
                fan.getId(), "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"));
    }

    @Test
    void auth_refuse_type_token_inconnu() {
        User fan = compte("tok-int@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO email_tokens (created_at, updated_at, user_id, token_hash, type, expires_at)"
                        + " VALUES (now(), now(), ?, ?, 'MAGIC_LINK', now() + interval '1 hour')",
                fan.getId(), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    }

    @Test
    void unique_refuse_doublon_idempotence() {
        User fan = compte("fan-idem@example.com");
        Wallet wallet = wallets.save(Wallet.create(fan));
        transactions.saveAndFlush(WalletTransaction.record("int-1", wallet,
                WalletTransaction.Direction.CREDIT, 100, null, null, 0, 100,
                WalletTransaction.Kind.COIN_PURCHASE, null));
        assertThrows(DataIntegrityViolationException.class, () -> transactions.saveAndFlush(
                WalletTransaction.record("int-1", wallet, WalletTransaction.Direction.CREDIT, 100,
                        null, null, 0, 100, WalletTransaction.Kind.COIN_PURCHASE, null)));
    }

    @Test
    void notnull_refuse_email_absent() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO users (created_at, updated_at, username, display_name, role, status)"
                        + " VALUES (now(), now(), 'sansmail', 'Sans Mail', 'USER', 'ACTIVE')"));
    }

    @Test
    void fk_refuse_stream_vers_chaine_inexistante() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO streams (created_at, updated_at, creator_id, title, status, visibility,"
                        + " peak_viewers, deleted) VALUES (now(), now(), 999999, 'Fantôme', 'SCHEDULED', 'PUBLIC', 0, false)"));
    }

    @Test
    void check_refuse_clip_sans_source() {
        CreatorProfile chaine = chaine("clip-int@example.com", "clip-int");
        // XOR : aucune source.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO clips (created_at, updated_at, creator_id, title, clip_url, starts_at_seconds,"
                        + " duration_seconds, visibility, processing_status, deleted, share_slug)"
                        + " VALUES (now(), now(), ?, 'Orphelin', 'https://cdn.example.com/o.mp4', 0, 10,"
                        + " 'PUBLIC', 'READY', false, 'orphelin01')",
                chaine.getId()));
    }

    @Test
    void check_refuse_solde_negatif() {
        // Solde jamais négatif (CHECK base, au-delà du garde entité).
        User pauvre = compte("pauvre-int@example.com");
        Wallet wallet = wallets.save(Wallet.create(pauvre));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE wallets SET balance_coins = -5 WHERE id = ?", wallet.getId()));
    }

    @Test
    void check_refuse_montant_nul() {
        User pauvre = compte("pauvre2-int@example.com");
        Wallet wallet = wallets.save(Wallet.create(pauvre));
        // Montant strictement positif.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO transactions (created_at, updated_at, idempotency_key, wallet_id, direction,"
                        + " amount_coins, balance_before, balance_after, kind, status)"
                        + " VALUES (now(), now(), 'int-zero', ?, 'CREDIT', 0, 0, 0, 'COIN_PURCHASE', 'PENDING')",
                wallet.getId()));
    }

    @Test
    void restrict_refuse_suppression_compte_avec_chaine() {
        User proprietaire = compte("prop-int@example.com");
        channels.save(CreatorProfile.create(proprietaire, "Prop TV", "prop-int"));
        // Compte avec chaîne : suppression refusée (données critiques conservées).
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM users WHERE id = ?", proprietaire.getId()));
    }

    @Test
    void restrict_refuse_suppression_categorie_utilisee() {
        Category jeux = categories.save(Category.create("JeuxInt", "jeux-int", "Jeux"));
        Stream live = streams.save(Stream.schedule(
                channels.save(CreatorProfile.create(compte("host-int@example.com"), "Host TV", "host-int")),
                "Live", jeux, Instant.now()));
        streams.saveAndFlush(live);
        // Catégorie utilisée : suppression refusée (archiver, pas détruire).
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM categories WHERE id = ?", jeux.getId()));
    }

    @Test
    void abonnement_periode_inversee_refusee() {
        User fan = compte("abo-int@example.com");
        CreatorProfile chaine = chaine("vedette-int@example.com", "vedette-int");
        SubscriptionPlan plan = plans.save(SubscriptionPlan.create("TierInt", 100, 30, null));
        Instant debut = Instant.now();
        // Fin avant début : CHECK base (la fabrique le refuse aussi).
        // Timestamp JDBC : Instant n'est pas liable en brut, conversion explicite.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO subscriptions (created_at, updated_at, subscriber_id, creator_id, plan_id,"
                        + " status, current_period_start, current_period_end, auto_renew)"
                        + " VALUES (now(), now(), ?, ?, ?, 'ACTIVE', ?, ?, true)",
                fan.getId(), chaine.getId(), plan.getId(),
                java.sql.Timestamp.from(debut), java.sql.Timestamp.from(debut.minusSeconds(60))));
    }

    // ---------- Positifs : relations, finance, seed ----------

    @Test
    void graphe_relationnel_bout_en_bout() {
        User fan = compte("graphe-fan@example.com");
        CreatorProfile chaine = chaine("graphe-star@example.com", "graphe-star");
        Category tech = categories.save(Category.create("TechInt", "tech-int", "Tech"));
        Stream live = streams.save(Stream.schedule(chaine, "Live", tech, Instant.now()));
        Video replay = videos.save(Video.fromStream(chaine, live, "Replay", null,
                "https://cdn.example.com/r.mp4", 3600));
        Clip clip = clips.save(Clip.fromVideo(chaine, replay, "Extrait",
                "https://cdn.example.com/c.mp4", 45));

        follows.save(Follow.create(fan, chaine));
        SubscriptionPlan plan = plans.save(SubscriptionPlan.create("TierG", 200, 30, null));
        Instant debut = Instant.now();
        subscriptions.save(Subscription.subscribe(fan, chaine, plan, debut, debut.plusSeconds(30L * 86400), true));
        messages.save(ChatMessage.userMessage(live, fan, "Fan", "Génial !", "Génial !"));
        GiftItem rose = gifts.save(GiftItem.create("RoseInt", 10, null));
        sends.save(GiftSend.send(rose, fan, live, 2, 16));

        assertEquals(live.getId(), videos.findById(replay.getId()).orElseThrow().getOriginStream().getId());
        assertEquals(replay.getId(), clips.findById(clip.getId()).orElseThrow().getVideo().getId());
        assertTrue(follows.existsByFollowerIdAndFollowedId(fan.getId(), chaine.getId()));
        assertTrue(subscriptions.existsBySubscriberIdAndCreatorIdAndStatus(
                fan.getId(), chaine.getId(), Subscription.Status.ACTIVE));
        assertEquals(1, messages.countByStreamId(live.getId()));
        assertEquals(1, sends.findByStreamIdOrderByCreatedAtAsc(live.getId()).size());
    }

    @Test
    void coherence_financiere_arithmetique_entiere_et_taux_exact() {
        User user = compte("finance-int@example.com");
        Wallet wallet = wallets.save(Wallet.create(user));

        // 199 + 99 − 98 = 200 EXACT (BIGINT, jamais de flottant).
        wallet.credit(199);
        wallet.credit(99);
        wallet.debit(98);
        wallets.saveAndFlush(wallet);
        assertEquals(200, wallets.findById(wallet.getId()).orElseThrow().getBalanceCoins());

        // Journal chaîné avant/après + taux NUMERIC exact (500.0000, pas 499.9999).
        WalletTransaction t1 = transactions.save(WalletTransaction.record("fin-1", wallet,
                WalletTransaction.Direction.CREDIT, 298, new BigDecimal("149.0000"), "XOF",
                0, 298, WalletTransaction.Kind.COIN_PURCHASE, "prov-1"));
        t1.confirm();
        WalletTransaction t2 = transactions.save(WalletTransaction.record("fin-2", wallet,
                WalletTransaction.Direction.DEBIT, 98, null, null,
                298, 200, WalletTransaction.Kind.GIFT_SEND, "send-1"));
        t2.confirm();
        transactions.saveAndFlush(t1);
        transactions.saveAndFlush(t2);

        List<WalletTransaction> releve = transactions.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
        assertEquals(2, releve.size());
        assertEquals(new BigDecimal("149.0000"), releve.stream()
                .filter(t -> t.getIdempotencyKey().equals("fin-1")).findFirst().orElseThrow()
                .getFiatReferenceAmount());
        assertTrue(releve.stream().allMatch(t -> t.getBalanceAfter() >= 0));
    }

    @Test
    void seed_reference_idempotent_sans_utilisateur() {
        ReferenceDataSeeder seeder = new ReferenceDataSeeder(categories, configs, packs);
        seeder.seed();
        seeder.seed();
        assertEquals(12, categories.count());
        assertEquals(3, configs.count());
    }
}
