import 'package:flutter/material.dart';

/// Couleurs officielles FLICK — reprise de l'identité web/PC.
/// AUCUNE couleur en dur ailleurs : tout passe par FlickColors/FlickTheme.
class FlickColors {
  const FlickColors._();

  static const Color green = Color(0xFF00E065);
  static const Color greenHover = Color(0xFF34F27F);
  static const Color black = Color(0xFF050506);
  static const Color surface = Color(0xFF121214);
  static const Color surface2 = Color(0xFF1A1A1E);
  static const Color border = Color(0xFF232328);
  static const Color white = Color(0xFFF4F6F4);
  static const Color muted = Color(0xFF9AA3A7);
  static const Color live = Color(0xFFFF3B5C);
  static const Color danger = Color(0xFFE5484D);
  static const Color warning = Color(0xFFF5A524);
}

/// Espacements centralisés.
class FlickSpacing {
  const FlickSpacing._();

  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Rayons centralisés.
class FlickRadius {
  const FlickRadius._();

  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double pill = 999;
}

/// Ombres centralisées.
class FlickShadows {
  const FlickShadows._();

  static const List<BoxShadow> card = [
    BoxShadow(color: Color(0x40000000), blurRadius: 12, offset: Offset(0, 4)),
  ];
  static const List<BoxShadow> glowGreen = [
    BoxShadow(color: Color(0x5900E065), blurRadius: 16, offset: Offset.zero),
  ];
}
