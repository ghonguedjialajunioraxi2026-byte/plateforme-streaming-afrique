import 'package:flutter/material.dart';
import 'flick_colors.dart';

/// Typographie FLICK (polices système, hiérarchie display/body/mono).
class FlickTypography {
  const FlickTypography._();

  static const TextStyle displayLarge = TextStyle(
    fontSize: 28, fontWeight: FontWeight.w800, height: 1.15,
  );
  static const TextStyle display = TextStyle(
    fontSize: 22, fontWeight: FontWeight.w800, height: 1.2,
  );
  static const TextStyle title = TextStyle(
    fontSize: 17, fontWeight: FontWeight.w700, height: 1.3,
  );
  static const TextStyle body = TextStyle(fontSize: 15, height: 1.45);
  static const TextStyle bodySmall = TextStyle(fontSize: 13, height: 1.4);
  static const TextStyle caption = TextStyle(fontSize: 12, height: 1.35);
  static const TextStyle mono = TextStyle(
    fontFamily: 'monospace', fontSize: 13, height: 1.4,
  );
}

/// Thème FLICK — sombre par défaut (architecture prête pour un clair futur).
class FlickTheme {
  const FlickTheme._();

  static ThemeData get dark {
    const scheme = ColorScheme.dark(
      primary: FlickColors.green,
      secondary: FlickColors.greenHover,
      surface: FlickColors.surface,
      error: FlickColors.danger,
      onPrimary: FlickColors.black,
      onSurface: FlickColors.white,
    );
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: FlickColors.black,
      appBarTheme: const AppBarTheme(
        backgroundColor: FlickColors.black,
        foregroundColor: FlickColors.white,
        elevation: 0,
        centerTitle: false,
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: FlickColors.surface,
        selectedItemColor: FlickColors.green,
        unselectedItemColor: FlickColors.muted,
        type: BottomNavigationBarType.fixed,
      ),
      cardTheme: CardThemeData(
        color: FlickColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(FlickRadius.md),
          side: const BorderSide(color: FlickColors.border),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: FlickColors.surface2,
        labelStyle: FlickTypography.bodySmall.copyWith(color: FlickColors.white),
        side: const BorderSide(color: FlickColors.border),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(FlickRadius.pill),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: FlickColors.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(FlickRadius.md),
          borderSide: const BorderSide(color: FlickColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(FlickRadius.md),
          borderSide: const BorderSide(color: FlickColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(FlickRadius.md),
          borderSide: const BorderSide(color: FlickColors.green, width: 1.5),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: FlickColors.green,
          foregroundColor: FlickColors.black,
          minimumSize: const Size(48, 48),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(FlickRadius.md),
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: FlickColors.green,
          minimumSize: const Size(48, 44),
        ),
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: FlickColors.surface2,
        contentTextStyle: TextStyle(color: FlickColors.white),
      ),
      dividerColor: FlickColors.border,
      // Contrastes tactiles : cibles >= 48px via les thèmes ci-dessus.
    );
  }
}
