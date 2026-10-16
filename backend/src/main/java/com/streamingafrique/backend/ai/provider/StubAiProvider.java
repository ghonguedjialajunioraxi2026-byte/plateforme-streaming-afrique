package com.streamingafrique.backend.ai.provider;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.streamingafrique.backend.ai.config.AiProperties;

/**
 * Provider LOCAL déterministe (défaut) — 0 coût, 0 donnée externe.
 *
 * Ce n'est PAS un « faux système » : c'est le gabarit honnête des
 * réponses IA, structuré à partir des DONNÉES RÉELLES fournies par
 * le service (jamais de statistique inventée). En production, le
 * provider {@code http} prend le relais sans changer une ligne métier.
 * Sert aussi de {@code FakeAIProvider} pour les tests.
 */
@Component
public class StubAiProvider implements AiProvider {

    private final AiProperties properties;

    public StubAiProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public AiGeneration generate(AiPrompt prompt) {
        boolean en = "en".equalsIgnoreCase(prompt.language());
        String user = prompt.user() == null ? "" : prompt.user().trim();
        String kind = kindOf(prompt.system());
        return switch (kind) {
            case "titles" -> AiGeneration.of(titles(user, en));
            case "description" -> AiGeneration.of(description(user, en));
            case "tags" -> AiGeneration.of(tags(user, en));
            case "summary" -> AiGeneration.of(summary(user, en));
            case "analytics" -> AiGeneration.of(analytics(user, en));
            case "moderation" -> AiGeneration.of(moderation(user, en));
            case "search" -> AiGeneration.of(search(user, en));
            default -> AiGeneration.of(assistant(user, en));
        };
    }

    /** Le « genre » de tâche est encodé dans la consigne système. */
    private String kindOf(String system) {
        if (system == null) {
            return "assistant";
        }
        String s = system.toLowerCase(Locale.ROOT);
        if (s.contains("[titles]")) {
            return "titles";
        }
        if (s.contains("[description]")) {
            return "description";
        }
        if (s.contains("[tags]")) {
            return "tags";
        }
        if (s.contains("[summary]")) {
            return "summary";
        }
        if (s.contains("[analytics]")) {
            return "analytics";
        }
        if (s.contains("[moderation]")) {
            return "moderation";
        }
        if (s.contains("[search]")) {
            return "search";
        }
        return "assistant";
    }

    /** Chaque ligne = UNE suggestion (le service les découpe). */
    private String titles(String user, boolean en) {
        if (user.isBlank()) {
            return en ? "My live starts now" : "Mon live commence maintenant";
        }
        String base = firstSentence(user);
        if (en) {
            return "LIVE: " + base + "\n" + base + " — come and chat\n"
                    + "Let's talk about " + base + " together";
        }
        return "EN DIRECT : " + base + "\n" + base + " — viens en discuter\n"
                + "On parle de " + base + " ensemble ce soir";
    }

    private String description(String user, boolean en) {
        if (en) {
            return "Welcome to this live about " + firstSentence(user)
                    + ". Drop your questions in the chat — I read everything.";
        }
        return "Bienvenue dans ce live sur " + firstSentence(user)
                + ". Pose tes questions dans le chat — je lis tout.";
    }

    private String tags(String user, boolean en) {
        String[] words = user.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9 ]", " ")
                .split("\\s+");
        StringBuilder tags = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (word.length() >= 4 && count < 8 && !tags.toString().contains(word)) {
                if (count > 0) {
                    tags.append(",");
                }
                tags.append(word);
                count++;
            }
        }
        if (count == 0) {
            tags.append(en ? "live,community" : "live,communauté");
        }
        return tags.toString();
    }

    /** Résumé HONNÊTE : ne reformule que les métadonnées fournies. */
    private String summary(String user, boolean en) {
        if (en) {
            return "Summary (from available metadata only — no audio/video analysis): "
                    + firstSentence(user);
        }
        return "Résumé (métadonnées disponibles uniquement — aucune analyse "
                + "audio/vidéo) : " + firstSentence(user);
    }

    /** L'analyse chiffrée est faite par le SERVICE ; le stub la met en forme. */
    private String analytics(String user, boolean en) {
        if (en) {
            return "Analysis of your real data: " + user
                    + " Tip: keep streaming regularly at your best hours.";
        }
        return "Analyse de tes données réelles : " + user
                + " Conseil : garde un rythme régulier sur tes meilleurs créneaux.";
    }

    private String moderation(String user, boolean en) {
        // Le stub ne reçoit QUE les textes sans règle locale déclenchée
        // (violations évidentes filtrées avant) : verdict prudent par défaut.
        if (en) {
            return "verdict=SAFE|confidence=0.7|category=none|reason=no local rule matched";
        }
        return "verdict=SAFE|confidence=0.7|category=none|reason=aucune règle locale déclenchée";
    }

    private String search(String user, boolean en) {
        if (en) {
            return "keywords=" + user + "|category=|filters=";
        }
        return "keywords=" + user + "|category=|filters=";
    }

    private String assistant(String user, boolean en) {
        if (en) {
            return "Based on Flick's official help: " + firstSentence(user)
                    + " — see the Help section for the full guide.";
        }
        return "D'après l'aide officielle de Flick : " + firstSentence(user)
                + " — voir la rubrique d'aide pour le guide complet.";
    }

    private String firstSentence(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() > 160) {
            clean = clean.substring(0, 160).trim();
        }
        return clean.isEmpty() ? "ton contenu" : clean;
    }

    /** Exposé pour l'admin (quel plafond est configuré). */
    public int configuredMaxOutput() {
        return properties.getMaxOutputTokens();
    }
}
