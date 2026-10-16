import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../core/theme/flick_colors.dart';
import '../../core/utils/format.dart';

/// Avatar avec cache + initiale de repli.
class FlickAvatar extends StatelessWidget {
  const FlickAvatar({super.key, this.name, this.url, this.radius = 20, this.showDot = false});

  final String? name;
  final String? url;
  final double radius;
  final bool showDot;

  @override
  Widget build(BuildContext context) {
    // Jamais de crash si name est null (ex. visiteur déconnecté) : repli « ? ».
    final label = (name ?? '').trim();
    final initial = label.isEmpty ? '?' : label[0].toUpperCase();
    return Stack(
      clipBehavior: Clip.none,
      children: [
        CircleAvatar(
          radius: radius,
          backgroundColor: FlickColors.surface2,
          backgroundImage: url == null || url!.isEmpty
              ? null
              : CachedNetworkImageProvider(url!),
          child: url == null || url!.isEmpty
              ? Text(initial,
                  style: TextStyle(
                      fontWeight: FontWeight.w700, fontSize: radius * 0.8))
              : null,
        ),
        if (showDot)
          Positioned(
            right: 0, bottom: 0,
            child: Container(
              width: 12, height: 12,
              decoration: BoxDecoration(
                color: FlickColors.live,
                shape: BoxShape.circle,
                border: Border.all(color: FlickColors.black, width: 2),
              ),
            ),
          ),
      ],
    );
  }
}

/// Badge générique.
class FlickBadge extends StatelessWidget {
  const FlickBadge({super.key, required this.label, this.color = FlickColors.surface2});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
          horizontal: FlickSpacing.sm, vertical: 3),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(FlickRadius.pill),
      ),
      child: Text(label,
          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
    );
  }
}

/// Badge LIVE rouge.
class FlickLiveBadge extends StatelessWidget {
  const FlickLiveBadge({super.key, this.big = false});

  final bool big;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(
          horizontal: big ? 10 : 7, vertical: big ? 5 : 3),
      decoration: BoxDecoration(
        color: FlickColors.live,
        borderRadius: BorderRadius.circular(FlickRadius.sm),
      ),
      child: Text('LIVE',
          style: TextStyle(
              fontSize: big ? 13 : 11,
              fontWeight: FontWeight.w800,
              color: Colors.white)),
    );
  }
}

/// Compteur de spectateurs (œil + nombre compact).
class FlickViewerCount extends StatelessWidget {
  const FlickViewerCount({super.key, required this.count, this.color});

  final int count;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? FlickColors.muted;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.visibility, size: 14, color: c),
        const SizedBox(width: 4),
        Text(formatCount(count),
            style: TextStyle(fontSize: 12, color: c)),
      ],
    );
  }
}
