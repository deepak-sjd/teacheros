import '../../../core/api/api_client.dart';

class AuthApi {
  AuthApi(this._client);

  final ApiClient _client;

  Future<void> requestOtp(String phoneNumber) async {
    try {
      await _client.raw.post('/auth/otp/request', data: {'phoneNumber': phoneNumber});
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// [displayName] and [tenantName] are only required the first time a
  /// phone number logs in (signup) - the backend ignores them for an
  /// existing account, so the UI can always send whatever it has.
  Future<AuthTokens> verifyOtp({
    required String phoneNumber,
    required String code,
    String? displayName,
    String? tenantName,
  }) async {
    try {
      final response = await _client.raw.post('/auth/otp/verify', data: {
        'phoneNumber': phoneNumber,
        'code': code,
        if (displayName != null) 'displayName': displayName,
        if (tenantName != null) 'tenantName': tenantName,
      });
      return AuthTokens.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }
}

class AuthTokens {
  AuthTokens({required this.accessToken, required this.refreshToken, required this.expiresInSeconds});

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        expiresInSeconds: json['expiresInSeconds'] as int,
      );

  final String accessToken;
  final String refreshToken;
  final int expiresInSeconds;
}
