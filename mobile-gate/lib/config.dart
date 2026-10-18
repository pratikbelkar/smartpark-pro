import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Server address resolution:
///   1. In-app override (Profile → Server), stored securely. Always wins.
///   2. --dart-define API_BASE_URL=... when provided.
///   3. Android defaults to 10.0.2.2 (emulator host alias).
///   4. iOS simulator / desktop default to localhost.
String get defaultApiBaseUrl {
  const override = String.fromEnvironment('API_BASE_URL', defaultValue: '');
  if (override.isNotEmpty) return override;
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
}

class ServerConfig extends ChangeNotifier {
  static const _key = 'sp_server';
  static final ServerConfig instance = ServerConfig._();

  ServerConfig._();

  final _storage = const FlutterSecureStorage();
  String? _override;
  bool loaded = false;

  Future<void> load() async {
    try {
      _override = await _storage.read(key: _key);
    } catch (_) {
      _override = null;
    }
    loaded = true;
    notifyListeners();
  }

  String get baseUrl {
    final o = _override;
    if (o != null && o.trim().isNotEmpty) return o.trim();
    return defaultApiBaseUrl;
  }

  bool get isCustom => _override != null && _override!.trim().isNotEmpty;

  Future<void> setOverride(String? url) async {
    final value = url?.trim();
    if (value == null || value.isEmpty) {
      await _storage.delete(key: _key);
      _override = null;
    } else {
      await _storage.write(key: _key, value: value);
      _override = value;
    }
    notifyListeners();
  }
}
