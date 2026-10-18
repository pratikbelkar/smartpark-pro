import 'dart:convert';
import 'package:http/http.dart' as http;
import 'config.dart' show ServerConfig;
import 'models.dart';

class ApiException implements Exception {
  final String message;
  final int? status;
  ApiException(this.message, [this.status]);

  @override
  String toString() => message;
}

class ApiClient {
  final Future<String?> Function() tokenProvider;

  ApiClient({required this.tokenProvider});

  Uri _uri(String path, [Map<String, String>? query]) =>
      Uri.parse('${ServerConfig.instance.baseUrl}$path').replace(queryParameters: query);

  Future<dynamic> _decode(http.Response res) {
    final body = res.body.isEmpty ? {} : jsonDecode(res.body);
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return (body is Map ? body['data'] : null) ?? body;
    }
    final message = body is Map
        ? (body['message'] as String? ?? 'Request failed (${res.statusCode}).')
        : 'Request failed (${res.statusCode}).';
    throw ApiException(message, res.statusCode);
  }

  Future<Map<String, String>> _headers({bool auth = true}) async {
    final headers = {'Content-Type': 'application/json'};
    if (auth) {
      final token = await tokenProvider();
      if (token != null) headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  Future<dynamic> get(String path, [Map<String, String>? query]) async {
    try {
      final res = await http.get(_uri(path, query), headers: await _headers()).timeout(
            const Duration(seconds: 15),
          );
      return _decode(res);
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException('Cannot reach the server. Is the backend running?');
    }
  }

  Future<dynamic> post(String path, Map<String, dynamic> body) async {
    try {
      final res = await http
          .post(_uri(path), headers: await _headers(), body: jsonEncode(body))
          .timeout(const Duration(seconds: 20));
      return _decode(res);
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException('Cannot reach the server. Is the backend running?');
    }
  }

  Future<dynamic> put(String path, Map<String, dynamic> body) async {
    try {
      final res = await http
          .put(_uri(path), headers: await _headers(), body: jsonEncode(body))
          .timeout(const Duration(seconds: 15));
      return _decode(res);
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException('Cannot reach the server. Is the backend running?');
    }
  }

  Future<dynamic> delete(String path) async {
    try {
      final res = await http.delete(_uri(path), headers: await _headers()).timeout(
            const Duration(seconds: 15),
          );
      return _decode(res);
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException('Cannot reach the server. Is the backend running?');
    }
  }

  // Convenience wrappers ---------------------------------------------------

  Future<List<LocationCard>> searchLocations({
    String? city,
    String? vehicleType,
    required DateTime start,
    required DateTime end,
  }) async {
    final query = <String, String>{
      if (city != null && city.isNotEmpty) 'city': city,
      if (vehicleType != null && vehicleType.isNotEmpty) 'vehicleType': vehicleType,
      'start': start.toIso8601String().substring(0, 16),
      'end': end.toIso8601String().substring(0, 16),
      'size': '20',
    };
    final data = await get('/api/v1/parking-locations', query);
    final content = (data is Map ? data['content'] : data) as List;
    return content.map((e) => LocationCard.fromJson(e)).toList();
  }

  Future<Availability> availability({
    required String locationId,
    required DateTime start,
    required DateTime end,
    required String vehicleType,
  }) async {
    final data = await get('/api/v1/parking-locations/$locationId/availability', {
      'start': start.toIso8601String().substring(0, 16),
      'end': end.toIso8601String().substring(0, 16),
      'vehicleType': vehicleType,
    });
    return Availability.fromJson(data as Map<String, dynamic>);
  }
}
