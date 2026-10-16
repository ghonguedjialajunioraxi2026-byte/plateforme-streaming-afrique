import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

/// État réseau : online/offline + qualité présumée.
/// Tous les écrans affichent offline/erreur au lieu d'écrans blancs.
class ConnectivityService extends ChangeNotifier {
  ConnectivityService({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity() {
    _subscription =
        _connectivity.onConnectivityChanged.listen(_onChanged);
    refresh();
  }

  final Connectivity _connectivity;
  late final StreamSubscription<List<ConnectivityResult>> _subscription;

  bool _online = true;
  bool _metered = false;

  bool get isOnline => _online;
  bool get isMetered => _metered;

  Future<void> refresh() async {
    try {
      final results = await _connectivity.checkConnectivity();
      _onChanged(results);
    } catch (_) {
      _online = true;
      notifyListeners();
    }
  }

  void _onChanged(List<ConnectivityResult> results) {
    _online = !results.contains(ConnectivityResult.none);
    _metered = results.contains(ConnectivityResult.mobile);
    notifyListeners();
  }

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}
