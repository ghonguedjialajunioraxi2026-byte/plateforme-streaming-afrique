package com.streamingafrique.backend.beta.model;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Retour bêta-testeur (étape 24) : BUG, UX, SUGGESTION, PERFORMANCE,
 * STREAMING, CHAT, PAYMENT, OTHER — workflow OPEN → TRIAGED →
 * IN_PROGRESS → FIXED → VERIFIED → CLOSED, priorité P0..P3 posée par
 * l'équipe (jamais par le rapporteur).
 *
 * Contexte technique MINIMAL et non sensible : route, version
 * applicative, appareil — jamais mot de passe, token ni contenu privé.
 */
@Entity
@Table(name = "beta_feedback")
public class BetaFeedback extends BaseEntity {

    public enum Category {
        BUG, UX, SUGGESTION, PERFORMANCE, STREAMING, CHAT, PAYMENT, OTHER
    }

    public enum Status {
        OPEN, TRIAGED, IN_PROGRESS, FIXED, VERIFIED, CLOSED
    }

    public enum Priority {
        P0, P1, P2, P3
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(length = 200)
    private String route;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(length = 120)
    private String device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Priority priority;

    protected BetaFeedback() {
    }

    private BetaFeedback(User reporter, Category category, String title, String description,
                         String route, String appVersion, String device) {
        this.reporter = reporter;
        this.category = category;
        this.title = title;
        this.description = description;
        this.route = route;
        this.appVersion = appVersion;
        this.device = device;
    }

    public static BetaFeedback submit(User reporter, Category category, String title,
                                      String description, String route,
                                      String appVersion, String device) {
        return new BetaFeedback(reporter, category, title, description, route, appVersion, device);
    }

    public void triage(Status status, Priority priority) {
        this.status = status;
        this.priority = priority;
    }

    public User getReporter() {
        return reporter;
    }

    public Category getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRoute() {
        return route;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getDevice() {
        return device;
    }

    public Status getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }
}
