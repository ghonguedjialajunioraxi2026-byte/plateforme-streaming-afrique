/// Formatage compact des compteurs (même convention que le web).
String formatCount(num value) {
  if (value >= 1000000) {
    return '${_trim(value / 1000000)} M';
  }
  if (value >= 1000) {
    return '${_trim(value / 1000)} k';
  }
  return value.toString();
}

String _trim(double v) {
  final s = v.toStringAsFixed(1);
  return s.endsWith('.0') ? s.substring(0, s.length - 2) : s;
}

/// Durée secondes → "12:34".
String formatDuration(int totalSeconds) {
  final m = totalSeconds ~/ 60;
  final s = totalSeconds % 60;
  return '$m:${s.toString().padLeft(2, '0')}';
}
