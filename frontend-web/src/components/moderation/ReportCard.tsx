import type { ReactNode } from 'react'
import { Badge, Card } from '../ui'
import { formatDate } from '../../utils/format'
import type { Report } from '../../services/api/moderation'

/// Libellé de la cible d'un signalement (un seul id renseigné).
export function reportTargetLabel(report: Report): string {
  switch (report.targetType) {
    case 'USER': return `Compte #${report.targetUserId ?? '?'}`;
    case 'CHAIN': return `Chaîne #${report.targetChainId ?? '?'}`;
    case 'STREAM': return `Direct #${report.targetStreamId ?? '?'}`;
    case 'VIDEO': return `VOD #${report.targetVideoId ?? '?'}`;
    case 'CLIP': return `Clip #${report.targetClipId ?? '?'}`;
    case 'CHAT_MESSAGE': return `Message #${report.targetMessageId ?? '?'}`;
  }
}

function statusBadge(status: Report['status']) {
  if (status === 'OPEN') return <Badge variant="warning">Ouvert</Badge>
  if (status === 'ACCEPTED') return <Badge variant="accent">Retenu</Badge>
  return <Badge variant="neutral">Rejeté</Badge>
}

/// Carte signalement (étape 17) : cible, motif, description, date, statut.
/// Les boutons d'action sont fournis par la page (workflow accept/reject/decide).
export function ReportCard({ report, children }: { report: Report; children?: ReactNode }) {
  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
            Signalement #{report.id}
            {statusBadge(report.status)}
            <Badge variant="neutral">{reportTargetLabel(report)}</Badge>
          </p>
          <p className="mt-1 text-xs text-text-secondary">
            Motif : <span className="font-mono">{report.reason}</span>
            {' · '}par #{report.reporterId} · {formatDate(report.createdAt)}
          </p>
          {report.description !== null && report.description.length > 0 && (
            <p className="mt-1 line-clamp-2 text-xs text-text-secondary">{report.description}</p>
          )}
        </div>
        {children !== undefined && <div className="flex flex-wrap gap-1.5">{children}</div>}
      </div>
    </Card>
  )
}
