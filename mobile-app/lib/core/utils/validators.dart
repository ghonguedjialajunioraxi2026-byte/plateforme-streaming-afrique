/// Validateurs côté client (miroir des règles backend — le serveur tranche).
String? validateEmail(String? value) {
  final v = (value ?? '').trim();
  if (v.isEmpty) return 'L\u2019email est obligatoire';
  if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v)) {
    return 'L\u2019email doit être valide';
  }
  return null;
}

String? validateUsername(String? value) {
  final v = (value ?? '').trim();
  if (v.isEmpty) return 'Le nom d\u2019utilisateur est obligatoire';
  if (v.length < 3 || v.length > 30) {
    return 'Entre 3 et 30 caractères';
  }
  if (!RegExp(r'^[a-zA-Z0-9._-]+$').hasMatch(v)) {
    return 'Lettres, chiffres, points, tirets et underscores';
  }
  return null;
}

String? validatePassword(String? value) {
  final v = value ?? '';
  if (v.isEmpty) return 'Le mot de passe est obligatoire';
  if (v.length < 8) return '8 caractères minimum';
  if (!RegExp(r'[A-Za-z]').hasMatch(v) || !RegExp(r'[0-9]').hasMatch(v)) {
    return 'Lettre + chiffre requis';
  }
  return null;
}

String? validateRequired(String? value, String field) {
  if ((value ?? '').trim().isEmpty) return '$field obligatoire';
  return null;
}
