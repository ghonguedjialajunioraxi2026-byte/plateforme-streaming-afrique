package com.streamingafrique.backend.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "GiftSend" → table {@code gift_sends}.
 *
 * RÔLE : l'ENVOI d'un cadeau pendant un live (acte monétaire immuable).
 * Coût total et part créateur figés en coins BIGINT au moment de l'envoi
 * (taux du jour, pas de recalcul ultérieur). Soldé par des lignes de
 * {@link WalletTransaction} (débit donateur, crédit créateur, frais).
 */
@Entity
@Table(name = "gift_sends", indexes = {
        @Index(name = "idx_giftsends_stream", columnList = "stream_id,id"),
        @Index(name = "idx_giftsends_sender", columnList = "sender_id")
})
public class GiftSend extends BaseEntity {

    /** Le cadeau du catalogue — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_id", nullable = false)
    private GiftItem gift;

    /** Le donateur — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Le live où le cadeau est offert — jamais null (chaîne implicite). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id", nullable = false)
    private Stream stream;

    /** Quantité offerte (strictement positive). */
    @Column(nullable = false)
    private int quantity = 1;

    /** Coût total débité (= prix × quantité, figé). */
    @Column(nullable = false)
    private long totalCostCoins;

    /** Part reversée au créateur (figée, <= coût total). */
    @Column(nullable = false)
    private long creatorShareCoins;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected GiftSend() {
    }

    /** Fabrique : cohérence prix × quantité et part <= total vérifiées. */
    public static GiftSend send(GiftItem gift, User sender, Stream stream,
                                int quantity, long creatorShareCoins) {
        if (gift == null || sender == null || stream == null) {
            throw new IllegalArgumentException("Cadeau, donateur et stream sont obligatoires");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive");
        }
        long total = gift.getPriceCoins() * quantity;
        if (creatorShareCoins < 0 || creatorShareCoins > total) {
            throw new IllegalArgumentException("La part créateur doit être entre 0 et le coût total");
        }
        GiftSend send = new GiftSend();
        send.gift = gift;
        send.sender = sender;
        send.stream = stream;
        send.quantity = quantity;
        send.totalCostCoins = total;
        send.creatorShareCoins = creatorShareCoins;
        return send;
    }

    public GiftItem getGift() {
        return gift;
    }

    public User getSender() {
        return sender;
    }

    public Stream getStream() {
        return stream;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getTotalCostCoins() {
        return totalCostCoins;
    }

    public long getCreatorShareCoins() {
        return creatorShareCoins;
    }
}
