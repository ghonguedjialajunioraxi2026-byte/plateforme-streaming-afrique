import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/social.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth/auth_controller.dart';
import '../social/social_repository.dart';

/// Notifications : liste serveur + compteur non-lus + marquage lu.
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  late Future<List<NotificationItem>> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<List<NotificationItem>> _load() {
    final userId = context.read<AuthController>().user?.id;
    return context.read<NotificationsRepository>().list(recipientId: userId);
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async =>
          setState(() => _future = _load()),
      child: FutureBuilder<List<NotificationItem>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Padding(
              padding: EdgeInsets.all(FlickSpacing.md),
              child: FlickSkeletonList(),
            );
          }
          if (snap.hasError) {
            return FlickErrorState(
              message: 'Notifications indisponibles : ${snap.error}',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final items = snap.data!;
          if (items.isEmpty) {
            return const FlickEmptyState(
                icon: Icons.notifications_outlined,
                message: 'Aucune notification');
          }
          return ListView.separated(
            padding: const EdgeInsets.all(FlickSpacing.md),
            itemCount: items.length,
            separatorBuilder: (_, _) =>
                const SizedBox(height: FlickSpacing.sm),
            itemBuilder: (_, i) {
              final n = items[i];
              return FlickCard(
                onTap: n.read
                    ? null
                    : () {
                        context
                            .read<NotificationsRepository>()
                            .markRead(n.id);
                        setState(() => items[i] = NotificationItem(
                            id: n.id,
                            type: n.type,
                            title: n.title,
                            body: n.body,
                            read: true,
                            at: n.at));
                      },
                child: Row(
                  children: [
                    Icon(
                      n.read
                          ? Icons.notifications_outlined
                          : Icons.notifications,
                      color: n.read
                          ? FlickColors.muted
                          : FlickColors.green,
                    ),
                    const SizedBox(width: FlickSpacing.sm),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(n.title ?? n.type ?? 'Notification',
                              style: FlickTypography.bodySmall.copyWith(
                                  fontWeight: n.read
                                      ? FontWeight.normal
                                      : FontWeight.w700)),
                          if (n.body != null)
                            Text(n.body!,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: FlickTypography.caption.copyWith(
                                    color: FlickColors.muted)),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
