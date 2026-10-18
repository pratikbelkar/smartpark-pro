import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'config.dart' show ServerConfig;
import 'models.dart';

class AuthState extends ChangeNotifier {
  static const _accessKey = 'sp_access';
  static const _refreshKey = 'sp_refresh';

  final _storage = const FlutterSecureStorage();
  User? user;
  bool loading = true;

  Future<void> init() async {
    final refresh = await _storage.read(key: _refreshKey);
    if (refresh == null) {
      loading = false;
      notifyListeners();
      return;
    }
    try {
      final access = await _storage.read(key: _accessKey);
      final res = await http.get(
        Uri.parse('${ServerConfig.instance.baseUrl}/api/v1/auth/me'),
        headers: {'Authorization': 'Bearer $access'},
      );
      if (res.statusCode == 200) {
        user = User.fromJson(jsonDecode(res.body)['data']);
      } else {
        await logout(silent: true);
      }
    } catch (_) {
      // Offline on launch: stay signed out rather than crash.
    }
    loading = false;
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final res = await http.post(
      Uri.parse('${ServerConfig.instance.baseUrl}/api/v1/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    final body = jsonDecode(res.body);
    if (res.statusCode != 200) {
      throw Exception(body['message'] ?? 'Sign-in failed.');
    }
    await _saveSession(body['data']);
  }

  Future<void> register(String fullName, String email, String password) async {
    final res = await http.post(
      Uri.parse('${ServerConfig.instance.baseUrl}/api/v1/auth/register'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'fullName': fullName, 'email': email, 'password': password}),
    );
    final body = jsonDecode(res.body);
    if (res.statusCode != 200) {
      final fields = body['fieldErrors'];
      throw Exception(fields is Map && fields.isNotEmpty
          ? fields.values.join('\n')
          : (body['message'] ?? 'Registration failed.'));
    }
    await _saveSession(body['data']);
  }

  Future<void> _saveSession(dynamic data) async {
    final tokens = data['tokens'];
    await _storage.write(key: _accessKey, value: '${tokens['accessToken']}');
    await _storage.write(key: _refreshKey, value: '${tokens['refreshToken']}');
    user = User.fromJson(data['user']);
    notifyListeners();
  }

  Future<String?> accessToken() => _storage.read(key: _accessKey);

  Future<void> logout({bool silent = false}) async {
    try {
      final refresh = await _storage.read(key: _refreshKey);
      if (refresh != null && !silent) {
        await http.post(
          Uri.parse('${ServerConfig.instance.baseUrl}/api/v1/auth/logout'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'refreshToken': refresh}),
        );
      }
    } catch (_) {
      // Best effort.
    }
    await _storage.deleteAll();
    user = null;
    notifyListeners();
  }
}
