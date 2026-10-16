import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/config/env.dart';

/// Partage natif Android/iOS avec les URLs publiques FLICK.
Future<void> shareContent(
  BuildContext context, {
  required String kind, // live | video | clip | channel
  required String id,
  required String title,
}) async {
  final path = switch (kind) {
    'live' => '/live/$id',
    'video' => '/video/$id',
    'clip' => '/clip/$id',
    'channel' => '/channel/$id',
    _ => '/',
  };
  await SharePlus.instance.share(
    ShareParams(
      text: '$title — $path'
          .replaceFirst('/', '${Env.publicSiteUrl}/'),
      subject: 'FLICK — $title',
    ),
  );
}
