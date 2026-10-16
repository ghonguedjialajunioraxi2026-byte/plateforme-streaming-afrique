import 'package:flutter/material.dart';

import '../../core/constants/branding.dart';

/// Logo officiel FLICK — SEUL widget affichant la marque.
/// Source : assets/branding/FLICK.png. Proportions conservées.
class FlickLogo extends StatelessWidget {
  const FlickLogo({super.key, this.height = 32, this.semanticLabel});

  final double height;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel ?? Branding.logoSemantics,
      image: true,
      child: Image.asset(
        Branding.logoAsset,
        height: height,
        fit: BoxFit.contain,
        filterQuality: FilterQuality.medium,
        errorBuilder: (_, _, _) => Text(
          Branding.name,
          semanticsLabel: Branding.logoSemantics,
          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
        ),
      ),
    );
  }
}
