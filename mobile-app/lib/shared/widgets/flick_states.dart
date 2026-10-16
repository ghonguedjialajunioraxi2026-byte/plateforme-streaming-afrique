import 'package:flutter/material.dart';

import '../../core/theme/flick_colors.dart';
import 'flick_inputs.dart';

/// États d'interface : JAMAIS d'écran blanc.
/// Loading / vide / erreur (+ retry) / hors-ligne (bandeau).
class FlickLoading extends StatelessWidget {
  const FlickLoading({super.key, this.label});

  final String? label;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          if (label != null) ...[
            const SizedBox(height: FlickSpacing.md),
            Text(label!, style: const TextStyle(color: FlickColors.muted)),
          ],
        ],
      ),
    );
  }
}

class FlickEmptyState extends StatelessWidget {
  const FlickEmptyState({super.key, required this.message, this.icon = Icons.inbox_outlined});

  final String message;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(FlickSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: FlickColors.muted),
            const SizedBox(height: FlickSpacing.md),
            Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: FlickColors.muted)),
          ],
        ),
      ),
    );
  }
}

class FlickErrorState extends StatelessWidget {
  const FlickErrorState({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(FlickSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline,
                size: 48, color: FlickColors.danger),
            const SizedBox(height: FlickSpacing.md),
            Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: FlickColors.muted)),
            if (onRetry != null) ...[
              const SizedBox(height: FlickSpacing.md),
              FlickButton(label: 'Réessayer', onPressed: onRetry, secondary: true),
            ],
          ],
        ),
      ),
    );
  }
}

/// Bandeau hors-ligne affiché en haut des écrans.
class FlickOfflineBar extends StatelessWidget {
  const FlickOfflineBar({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
          horizontal: FlickSpacing.md, vertical: FlickSpacing.sm),
      color: FlickColors.warning.withValues(alpha: 0.15),
      child: Row(
        children: [
          const Icon(Icons.wifi_off, size: 16, color: FlickColors.warning),
          const SizedBox(width: FlickSpacing.sm),
          Expanded(
            child: Text(message,
                style: const TextStyle(
                    color: FlickColors.warning, fontSize: 13)),
          ),
        ],
      ),
    );
  }
}

/// Squelette de chargement (listes) — évite les sauts de layout.
class FlickSkeletonList extends StatelessWidget {
  const FlickSkeletonList({super.key, this.items = 5});

  final int items;

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: items,
      separatorBuilder: (_, _) =>
          const SizedBox(height: FlickSpacing.sm),
      itemBuilder: (_, _) => Container(
        height: 72,
        decoration: BoxDecoration(
          color: FlickColors.surface2,
          borderRadius: BorderRadius.circular(FlickRadius.md),
        ),
      ),
    );
  }
}
