import 'package:flutter/material.dart';

import '../../core/theme/flick_colors.dart';
import '../../core/theme/flick_theme.dart';
import 'flick_logo.dart';

/// Carte sombre FLICK.
class FlickCard extends StatelessWidget {
  const FlickCard({super.key, required this.child, this.onTap, this.padding});

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(FlickRadius.md),
        child: Padding(
          padding: padding ?? const EdgeInsets.all(FlickSpacing.md),
          child: child,
        ),
      ),
    );
  }
}

/// Titre de section (ex. « En direct », « Catégories »).
class FlickSectionHeader extends StatelessWidget {
  const FlickSectionHeader({super.key, required this.title, this.onSeeAll});

  final String title;
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(title,
              style: FlickTypography.title
                  .copyWith(color: FlickColors.white)),
        ),
        if (onSeeAll != null)
          TextButton(onPressed: onSeeAll, child: const Text('Tout voir')),
      ],
    );
  }
}

/// AppBar mobile compacte : logo + recherche + notifications (+ avatar).
class FlickAppBar extends StatelessWidget implements PreferredSizeWidget {
  const FlickAppBar({
    super.key,
    this.onSearch,
    this.onNotifications,
    this.unread = 0,
    this.avatar,
    this.onAvatar,
  });

  final VoidCallback? onSearch;
  final VoidCallback? onNotifications;
  final int unread;
  final Widget? avatar;
  final VoidCallback? onAvatar;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      title: const FlickLogo(height: 30),
      actions: [
        IconButton(
          tooltip: 'Recherche',
          icon: const Icon(Icons.search),
          onPressed: onSearch,
        ),
        Stack(
          children: [
            IconButton(
              tooltip: 'Notifications',
              icon: const Icon(Icons.notifications_outlined),
              onPressed: onNotifications,
            ),
            if (unread > 0)
              Positioned(
                right: 8, top: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                  decoration: BoxDecoration(
                    color: FlickColors.danger,
                    borderRadius: BorderRadius.circular(FlickRadius.pill),
                  ),
                  child: Text(unread > 9 ? '9+' : '$unread',
                      style: const TextStyle(
                          fontSize: 10, fontWeight: FontWeight.w800)),
                ),
              ),
          ],
        ),
        if (avatar != null)
          Padding(
            padding: const EdgeInsets.only(right: FlickSpacing.sm),
            child: GestureDetector(onTap: onAvatar, child: avatar),
          ),
      ],
    );
  }
}
