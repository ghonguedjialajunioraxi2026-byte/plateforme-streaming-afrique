package com.streamingafrique.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du stockage de fichiers, injectée depuis {@code app.storage.*}
 * (voir application.yml et les profils dev/prod).
 *
 *  - {@code location}           : dossier racine des fichiers (base disque local ;
 *                                 remplacée plus tard par un accès objet S3).
 *  - {@code maxImageSizeMb}     : taille maximale autorisée pour UNE image.
 *  - {@code allowedImageTypes}  : types MIME autorisés (liste blanche),
 *                                 vérifiée sur le CONTENU réel du fichier.
 *
 * Valeurs par défaut sûres, surchargeables par environnement
 * (ex. STORAGE_LOCATION en production).
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String location = "./uploads";

    private long maxImageSizeMb = 5;

    private List<String> allowedImageTypes = new ArrayList<>(
            List.of("image/jpeg", "image/png", "image/gif", "image/webp"));

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getMaxImageSizeMb() {
        return maxImageSizeMb;
    }

    public void setMaxImageSizeMb(long maxImageSizeMb) {
        this.maxImageSizeMb = maxImageSizeMb;
    }

    public List<String> getAllowedImageTypes() {
        return allowedImageTypes;
    }

    public void setAllowedImageTypes(List<String> allowedImageTypes) {
        this.allowedImageTypes = allowedImageTypes;
    }
}