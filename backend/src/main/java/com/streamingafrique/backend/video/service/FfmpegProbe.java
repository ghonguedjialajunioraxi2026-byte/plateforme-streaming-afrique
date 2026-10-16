package com.streamingafrique.backend.video.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.streamingafrique.backend.config.StorageProperties;
import com.streamingafrique.backend.video.config.VideoProperties;

/**
 * Sonde FFmpeg (étape 15, §15) — INACTIVE par défaut
 * ({@code app.video.ffmpeg-enabled=false}).
 *
 * Câblage futur : installer le binaire, activer la propriété, et le worker
 * l'utilisera pour la durée réelle + la miniature + la découpe serveur.
 * AUCUNE commande shell : argv validés (chemins issus du stockage UNIQUEMENT,
 * position clampée), timeouts stricts, stderr borné (jamais journalisé en
 * entier). Toute invocation avec un chemin hors racine ou des arguments
 * suspects est REFUSÉE avant exécution (anti injection, §54).
 */
@Component
@ConditionalOnProperty(name = "app.video.ffmpeg-enabled", havingValue = "true")
public class FfmpegProbe {

    private static final Logger log = LoggerFactory.getLogger(FfmpegProbe.class);

    private final Path baseDirectory;

    public FfmpegProbe(StorageProperties storage, VideoProperties properties) {
        this.baseDirectory = Paths.get(storage.getLocation()).toAbsolutePath().normalize();
        if (!isBinaryAvailable()) {
            log.warn("FFmpeg activé (app.video.ffmpeg-enabled) mais binaire introuvable : "
                    + "sonde désactivée en pratique (les traitements restent déclaratifs)");
        }
    }

    /** Durée réelle (s) via ffprobe — vide si indisponible/illisible. */
    public Optional<Integer> probeDurationSeconds(String storageKey) {
        Path target = confined(storageKey).orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        List<String> command = List.of("ffprobe", "-v", "error",
                "-show_entries", "format=duration", "-of", "csv=p=0",
                target.toString());
        Optional<String> output = run(command, 60);
        if (output.isEmpty()) {
            return Optional.empty();
        }
        try {
            double seconds = Double.parseDouble(output.get().trim());
            if (seconds <= 0 || seconds > 24 * 3600) {
                return Optional.empty();
            }
            return Optional.of((int) seconds);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Miniature JPEG à {@code atSeconds} (clampée à [1, durée-1]) — true si
     * le fichier cible existe après exécution.
     */
    public boolean extractThumbnail(String storageKey, Path target, int atSeconds, int durationSeconds) {
        Path source = confined(storageKey).orElse(null);
        if (source == null || target == null) {
            return false;
        }
        int position = Math.min(Math.max(1, atSeconds),
                Math.max(1, durationSeconds - 1));
        List<String> command = List.of("ffmpeg", "-y", "-v", "error",
                "-ss", String.valueOf(position), "-i", source.toString(),
                "-frames:v", "1", "-q:v", "4", target.toString());
        if (run(command, 60).isEmpty()) {
            return false;
        }
        return Files.isRegularFile(target);
    }

    private Optional<Path> confined(String storageKey) {
        if (storageKey == null || storageKey.isBlank()
                || storageKey.contains("..") || storageKey.startsWith("/")) {
            log.warn("FFmpeg : clé de stockage refusée");
            return Optional.empty();
        }
        Path path = baseDirectory.resolve(storageKey).normalize();
        if (!path.startsWith(baseDirectory) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private Optional<String> run(List<String> command, long timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("FFmpeg : délai dépassé (commande tuée)");
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readNBytes(4096));
            if (process.exitValue() != 0) {
                log.warn("FFmpeg : échec (code {})", process.exitValue());
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (Exception e) {
            log.warn("FFmpeg : exécution impossible");
            return Optional.empty();
        }
    }

    private static boolean isBinaryAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
