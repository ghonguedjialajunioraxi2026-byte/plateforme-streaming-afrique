import 'package:shared_preferences/shared_preferences.dart';

/// Préférences locales NON sensibles (jamais de token ici) :
/// data saver, langue, pays, cache léger (catégories…).
class AppPrefs {
  AppPrefs._(this._prefs);

  final SharedPreferences _prefs;

  static const _kDataSaver = 'data_saver'; // auto|eco|standard|high
  static const _kLocale = 'locale'; // fr|en
  static const _kCountry = 'country'; // ex. CM
  static const _kCurrency = 'currency'; // ex. XAF (affichage, serveur = vérité)
  static const _kCategoriesCache = 'cache_categories';
  static const _kCategoriesAt = 'cache_categories_at';

  static Future<AppPrefs> load() async =>
      AppPrefs._(await SharedPreferences.getInstance());

  /// Niveau d'économie de données : auto/eco/standard/high.
  String get dataSaver => _prefs.getString(_kDataSaver) ?? 'auto';
  Future<void> setDataSaver(String v) => _prefs.setString(_kDataSaver, v);

  String get localeCode => _prefs.getString(_kLocale) ?? 'fr';
  Future<void> setLocaleCode(String v) => _prefs.setString(_kLocale, v);

  String get country => _prefs.getString(_kCountry) ?? 'CM';
  Future<void> setCountry(String v) => _prefs.setString(_kCountry, v);

  String get currency => _prefs.getString(_kCurrency) ?? 'XAF';
  Future<void> setCurrency(String v) => _prefs.setString(_kCurrency, v);

  /// Cache catégories (JSON brut + horodatage, TTL géré par l'appelant).
  String? get categoriesCache => _prefs.getString(_kCategoriesCache);
  int get categoriesCachedAt => _prefs.getInt(_kCategoriesAt) ?? 0;
  Future<void> setCategoriesCache(String json) async {
    await _prefs.setString(_kCategoriesCache, json);
    await _prefs.setInt(
        _kCategoriesAt, DateTime.now().millisecondsSinceEpoch);
  }
}
