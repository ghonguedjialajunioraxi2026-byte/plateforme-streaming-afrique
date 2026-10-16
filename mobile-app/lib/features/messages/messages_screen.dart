import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/social.dart';
import '../../../shared/widgets/widgets.dart';
import '../social/social_repository.dart';

/// Messages privés — le backend n'expose pas encore de REST dédié :
/// état assumé (pas d'endpoint inventé), prêt pour le câblage.
class MessagesScreen extends StatelessWidget {
  const MessagesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Conversation>>(
      future: context.read<MessagesRepository>().conversations(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Padding(
            padding: EdgeInsets.all(FlickSpacing.md),
            child: FlickSkeletonList(),
          );
        }
        final items = snap.data ?? [];
        if (items.isEmpty) {
          return const FlickEmptyState(
              icon: Icons.mail_outline,
              message: 'Aucune conversation pour le moment');
        }
        return ListView.separated(
          padding: const EdgeInsets.all(FlickSpacing.md),
          itemCount: items.length,
          separatorBuilder: (_, _) =>
              const SizedBox(height: FlickSpacing.sm),
          itemBuilder: (_, i) => FlickCard(
            onTap: () => Navigator.of(context)
                .pushNamed('/conversation', arguments: items[i].id),
            child: Text(items[i].peer ?? 'Conversation',
                style: FlickTypography.bodySmall),
          ),
        );
      },
    );
  }
}

/// Conversation (écran de détail, câblé quand l'API existe).
class ConversationScreen extends StatelessWidget {
  const ConversationScreen({super.key, required this.conversationId});

  final dynamic conversationId;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Conversation')),
      body: const FlickEmptyState(
          icon: Icons.mail_outline,
          message: 'Historique indisponible (API en cours côté backend)'),
    );
  }
}
