/// Erreur API — miroir de l'enveloppe backend.
/// Succès : {success:true, message, data} · Erreur : {success:false, status,
/// errorCode, message, errors?, path}. Jamais de secret dans les messages.
class ApiException implements Exception {
  ApiException(this.status, this.message, {this.errorCode, this.fields});

  final int status;
  final String message;
  final String? errorCode;
  final Map<String, String>? fields;

  bool get isAuth => status == 401;
  bool get isForbidden => status == 403;
  bool get isNotFound => status == 404;
  bool get isRateLimited => status == 429;
  bool get isServer => status >= 500;

  /// Session expirée et non renouvelable → déconnexion propre.
  bool get isSessionExpired => isAuth && errorCode != null;

  @override
  String toString() => 'ApiException($status): $message';

  static ApiException fromJson(int status, Map<String, dynamic> json) {
    final fields = json['errors'];
    return ApiException(
      status,
      (json['message'] ?? 'Erreur inattendue').toString(),
      errorCode: json['errorCode']?.toString(),
      fields: fields is Map
          ? fields.map((k, v) => MapEntry(k.toString(), v.toString()))
          : null,
    );
  }

  static ApiException network() => ApiException(-1, 'Connexion impossible');
  static ApiException timeout() => ApiException(-2, 'Délai dépassé');
}
