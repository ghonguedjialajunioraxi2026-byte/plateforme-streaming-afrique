import { useState } from 'react'
import { Link } from 'react-router'
import { Modal, Icon, useToast } from '../ui'
import { cn } from '../../utils/cn'
import { useAuth } from '../../features/auth/useAuth'

/* ================= PARTAGE ================= */

interface ShareModalProps {
  open: boolean
  onClose: () => void
  title: string
  channelName: string
}

/** Partage façon Twitch : copie du lien + relais sociaux (URL natives). */
export function ShareModal({ open, onClose, title, channelName }: ShareModalProps) {
  const toast = useToast()
  const url = window.location.href
  const text = `Regarde ${channelName} en direct sur FLICK : ${title}`

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(url)
      toast.show('Lien copié dans le presse-papiers !', 'success')
    } catch {
      toast.show('Copie impossible, sélectionne le lien manuellement.', 'error')
    }
  }

  const relays = [
    {
      label: 'X (Twitter)',
      href: `https://twitter.com/intent/tweet?text=${encodeURIComponent(text)}&url=${encodeURIComponent(url)}`,
      initial: 'X',
    },
    {
      label: 'WhatsApp',
      href: `https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}`,
      initial: 'W',
    },
    {
      label: 'Facebook',
      href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`,
      initial: 'f',
    },
  ]

  return (
    <Modal open={open} onClose={onClose} title="Partager ce live">
      <div className="space-y-4">
        <div className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5">
          <Icon name="link" className="h-4 w-4 shrink-0 text-text-muted" />
          <span className="min-w-0 flex-1 truncate text-sm text-text-secondary">{url}</span>
          <button
            type="button"
            onClick={() => void copyLink()}
            className="flex h-8 shrink-0 items-center gap-1.5 rounded-md bg-primary-500 px-3 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
          >
            Copier
          </button>
        </div>
        <div className="flex items-center gap-3">
          {relays.map((relay) => (
            <a
              key={relay.label}
              href={relay.href}
              target="_blank"
              rel="noreferrer"
              aria-label={`Partager sur ${relay.label}`}
              title={relay.label}
              className="flex h-11 w-11 items-center justify-center rounded-full bg-bg-elevated text-lg font-bold text-text-secondary transition-all duration-200 hover:-translate-y-0.5 hover:bg-primary-500 hover:text-text-inverse"
            >
              {relay.initial}
            </a>
          ))}
        </div>
      </div>
    </Modal>
  )
}

/* ================= ABONNEMENT ================= */

interface SubscribeModalProps {
  open: boolean
  onClose: () => void
  channelName: string
}

const TIERS = [
  {
    id: 'tier-1',
    name: 'Abonné',
    price: '2 500 coins/mois',
    perks: ['Badge abonné dans le chat', 'Pseudo mis en avant', 'Émotes de la chaîne'],
  },
  {
    id: 'tier-2',
    name: 'Super fan',
    price: '5 000 coins/mois',
    perks: ['Tous les avantages Abonné', 'Badge exclusif Super fan', 'Tirages réservés'],
  },
]

/** Abonnement façon Twitch : paliers + avantages, règlement via portefeuille. */
export function SubscribeModal({ open, onClose, channelName }: SubscribeModalProps) {
  const { isAuthenticated } = useAuth()
  const [tier, setTier] = useState(TIERS[0].id)

  return (
    <Modal open={open} onClose={onClose} title={`S'abonner à ${channelName}`}>
      <p className="rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-xs text-text-secondary">
        Fonctionnalité en cours d'activation : les abonnements payés ne sont pas encore
        débités. Le suivi gratuit reste disponible.
      </p>
      <div className="mt-3 space-y-3" role="radiogroup" aria-label="Choisir un palier">
        {TIERS.map((option) => {
          const selected = tier === option.id
          return (
            <button
              key={option.id}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => setTier(option.id)}
              className={cn(
                'w-full rounded-xl border p-4 text-left transition-all duration-200',
                selected
                  ? 'border-primary-500 bg-primary-500/10 shadow-glow-primary'
                  : 'border-border-default bg-bg-elevated hover:border-border-hover',
              )}
            >
              <span className="flex items-center justify-between">
                <span className="font-display font-bold text-text-primary">{option.name}</span>
                <span className="text-sm font-semibold text-primary-300">{option.price}</span>
              </span>
              <ul className="mt-2 space-y-1">
                {option.perks.map((perk) => (
                  <li key={perk} className="flex items-center gap-2 text-sm text-text-secondary">
                    <Icon name="check" className="h-4 w-4 text-primary-400" />
                    {perk}
                  </li>
                ))}
              </ul>
            </button>
          )
        })}
      </div>
      {isAuthenticated ? (
        <Link
          to="/wallet/acheter"
          className="mt-4 flex h-11 items-center justify-center gap-2 rounded-lg bg-primary-500 text-sm font-bold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
        >
          <Icon name="coin" className="h-4 w-4" />
          Continuer vers le portefeuille
        </Link>
      ) : (
        <Link
          to={`/connexion?next=${encodeURIComponent(window.location.pathname)}`}
          className="mt-4 flex h-11 items-center justify-center rounded-lg bg-primary-500 text-sm font-bold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
        >
          Connecte-toi pour t'abonner
        </Link>
      )}
      <p className="mt-3 text-center text-xs text-text-muted">
        Paiement en coins via ton portefeuille Flick. Annulable à tout moment.
      </p>
    </Modal>
  )
}

/* ================= DON ================= */

interface DonateModalProps {
  open: boolean
  onClose: () => void
  channelName: string
}

/**
 * Don façon Twitch : montants rapides en coins.
 * Honnêteté : aucun catalogue cadeau serveur n'existe encore côté API —
 * la modale crédite via le portefeuille existant, sans inventer de prix.
 */
export function DonateModal({ open, onClose, channelName }: DonateModalProps) {
  const { isAuthenticated } = useAuth()
  const [amount, setAmount] = useState(100)
  const amounts = [50, 100, 250, 500, 1000]

  return (
    <Modal open={open} onClose={onClose} title={`Soutenir ${channelName}`}>
      <p className="text-sm text-text-secondary">
        Choisis un montant en coins. Il sera débité de ton portefeuille et reversé au
        créateur après sa prochaine diffusion.
      </p>
      <div className="mt-4 grid grid-cols-3 gap-2" role="radiogroup" aria-label="Montant du don">
        {amounts.map((value) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={amount === value}
            onClick={() => setAmount(value)}
            className={cn(
              'flex h-11 items-center justify-center gap-1 rounded-lg border text-sm font-bold transition-all duration-200',
              amount === value
                ? 'border-primary-500 bg-primary-500/15 text-primary-300'
                : 'border-border-default bg-bg-elevated text-text-secondary hover:border-border-hover hover:text-text-primary',
            )}
          >
            <Icon name="coin" className="h-4 w-4" />
            {value}
          </button>
        ))}
      </div>
      <Link
        to={isAuthenticated ? '/wallet/acheter' : `/connexion?next=${encodeURIComponent(window.location.pathname)}`}
        className="mt-4 flex h-11 items-center justify-center gap-2 rounded-lg bg-primary-500 text-sm font-bold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
      >
        <Icon name="gift" className="h-4 w-4" />
        {isAuthenticated ? `Offrir ${amount} coins` : 'Connecte-toi pour offrir'}
      </Link>
    </Modal>
  )
}
