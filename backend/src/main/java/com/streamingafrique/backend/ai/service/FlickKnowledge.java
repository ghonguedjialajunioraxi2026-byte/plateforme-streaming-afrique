package com.streamingafrique.backend.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base de connaissances officielle de Flick (étape 18).
 *
 * L'assistant NE DOIT PAS inventer les règles de la plateforme : il
 * répond depuis CES fiches. Recherche par mots-clés (sans LLM, sans
 * coût) → les fiches pertinentes deviennent le contexte du provider.
 */
public final class FlickKnowledge {

    public record Entry(String id, List<String> keywords, String fr, String en) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("follow", List.of("suivre", "follow", "suivi", "chaîne", "channel", "notifications"),
                    "Suivre une chaîne : ouvre la page de la chaîne puis clique « Suivre ». "
                            + "Tu retrouves tes chaînes dans « Chaînes suivies » et tu peux activer "
                            + "les notifications de live.",
                    "Follow a channel: open the channel page and click \"Follow\". "
                            + "Find your channels under \"Following\" and enable live notifications."),
            new Entry("creator", List.of("créateur", "creator", "chaîne", "channel", "devenir", "become", "streamer", "live", "direct", "lancer", "start", "obs"),
                    "Devenir créateur : ouvre ta chaîne depuis ton profil, puis lance un live "
                            + "depuis le Studio (Stream Manager) avec ton logiciel (OBS) et ta clé "
                            + "de stream — garde-la secrète, ne la partage jamais.",
                    "Become a creator: open your channel from your profile, then go live "
                            + "from the Studio (Stream Manager) with your software (OBS) and your "
                            + "stream key — keep it secret, never share it."),
            new Entry("coins", List.of("coin", "pièce", "acheter", "buy", "portefeuille", "wallet", "paiement", "payment", "cadeau", "gift"),
                    "Coins et cadeaux : achète des coins dans « Portefeuille » (paiement "
                            + "sécurisé via nos prestataires), puis offre des cadeaux pendant les "
                            + "lives pour soutenir les créateurs. L'historique est dans « Transactions ».",
                    "Coins and gifts: buy coins in \"Wallet\" (secure payment through our "
                            + "providers), then send gifts during lives to support creators. "
                            + "History is under \"Transactions\"."),
            new Entry("subscription", List.of("abonnement", "subscription", "subscribe", "soutenir", "support"),
                    "Abonnements : abonne-toi à une chaîne pour la soutenir et débloquer ses "
                            + "avantages. Gère tes abonnements depuis « Abonnements ».",
                    "Subscriptions: subscribe to a channel to support it and unlock its "
                            + "perks. Manage them from \"Subscriptions\"."),
            new Entry("vod", List.of("vod", "replay", "revoir", "vidéo", "video", "clip", "extrait"),
                    "VOD et clips : les lives peuvent devenir des replays (VOD). Crée des "
                            + "extraits courts (clips) depuis une VOD et partage-les.",
                    "VODs and clips: lives can become replays (VODs). Create short "
                            + "excerpts (clips) from a VOD and share them."),
            new Entry("moderation", List.of("modération", "moderation", "signaler", "report", "signalement", "ban", "bannir", "spam", "harcèlement", "harassment", "règle", "rule"),
                    "Règles et modération : le spam, le harcèlement, les discours haineux, "
                            + "les arnaques et les contenus violents ou sexuels sont interdits. "
                            + "Signale un contenu via le bouton « Signaler » — un modérateur humain "
                            + "examine chaque signalement.",
                    "Rules and moderation: spam, harassment, hate speech, scams, and "
                            + "violent or sexual content are forbidden. Report content with the "
                            + "\"Report\" button — a human moderator reviews every report."),
            new Entry("account", List.of("compte", "account", "profil", "profile", "mot de passe", "password", "email", "connexion", "login", "inscription", "register"),
                    "Compte : inscris-toi avec ton email, vérifie-le, puis personnalise ton "
                            + "profil. En cas de mot de passe oublié, utilise « Mot de passe oublié ». "
                            + "Ne partage jamais ton mot de passe.",
                    "Account: register with your email, verify it, then customize your "
                            + "profile. If you forget your password, use \"Forgot password\". "
                            + "Never share your password."));

    private FlickKnowledge() {
    }

    /** Fiches pertinentes (max 3) pour une question — recherche locale. */
    public static List<Entry> relevant(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String q = question.toLowerCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            int hits = 0;
            for (String keyword : entry.keywords()) {
                if (q.contains(keyword.toLowerCase(Locale.ROOT))) {
                    hits++;
                }
            }
            if (hits > 0) {
                scored.add(new Scored(entry, hits));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.hits(), a.hits()));
        return scored.stream().limit(3).map(Scored::entry).toList();
    }

    /** Contexte injecté dans la consigne (langue demandée). */
    public static String context(String question, String language) {
        List<Entry> found = relevant(question);
        boolean en = "en".equalsIgnoreCase(language);
        if (found.isEmpty()) {
            return en ? "No official Flick help sheet matches this question."
                    : "Aucune fiche d'aide officielle Flick ne correspond à cette question.";
        }
        StringBuilder context = new StringBuilder(en
                ? "Official Flick help (use ONLY this to answer Flick questions):\n"
                : "Aide officielle Flick (utilise UNIQUEMENT ceci pour répondre sur Flick) :\n");
        for (Entry entry : found) {
            context.append("- [").append(entry.id()).append("] ")
                    .append(en ? entry.en() : entry.fr()).append("\n");
        }
        return context.toString();
    }

    /** Identifiants des fiches utilisées (traçabilité côté client). */
    public static List<String> sources(String question) {
        return relevant(question).stream().map(Entry::id).toList();
    }

    private record Scored(Entry entry, int hits) {
    }
}
