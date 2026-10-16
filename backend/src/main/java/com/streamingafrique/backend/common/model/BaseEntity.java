package com.streamingafrique.backend.common.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Socle commun à TOUTES les entités persistées de la plateforme.
 *
 * Fournit : l'identifiant généré automatiquement + l'audit des dates
 * (création / dernière modification) remplis automatiquement par
 * {@link AuditingEntityListener} (activé via @EnableJpaAuditing).
 *
 * Nouvelle entité métier ? Étendre cette classe et décrire UNIQUEMENT
 * ses colonnes propres — jamais refaire id/createdAt/updatedAt à la main.
 *
 * @MappedSuperclass : classe NON persistée en soi, mais dont les colonnes
 * sont incorporées dans chaque table des entités qui l'étendent.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rempli automatiquement au premier enregistrement (jamais modifié ensuite). */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Rempli automatiquement à chaque modification de la ligne. */
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected BaseEntity() {
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}