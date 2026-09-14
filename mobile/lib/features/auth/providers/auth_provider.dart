import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/app_exception.dart';
import '../../../core/auth/jwt_decoder.dart';
import '../../../core/auth/token_storage.dart';
import '../../../core/auth/user_role.dart';
import '../../../core/providers.dart';
import '../data/auth_api.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

class AuthState {
  const AuthState({required this.status, this.role, this.error});

  final AuthStatus status;

  /// Null while status is unknown/unauthenticated, or if the token somehow
  /// carries a role this app doesn't recognize (treated as "can't route
  /// safely" rather than guessed at).
  final UserRole? role;

  final AppException? error;

  static const initial = AuthState(status: AuthStatus.unknown);
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._authApi, this._tokenStorage) : super(AuthState.initial) {
    _checkExistingSession();
  }

  final AuthApi _authApi;
  final TokenStorage _tokenStorage;

  Future<void> _checkExistingSession() async {
    final refreshToken = await _tokenStorage.getRefreshToken();
    final accessToken = await _tokenStorage.getAccessToken();

    if (refreshToken == null || accessToken == null) {
      state = const AuthState(status: AuthStatus.unauthenticated);
      return;
    }

    state = AuthState(status: AuthStatus.authenticated, role: _decodeRole(accessToken));
  }

  Future<bool> requestOtp(String phoneNumber) async {
    try {
      await _authApi.requestOtp(phoneNumber);
      return true;
    } on AppException catch (e) {
      state = AuthState(status: state.status, role: state.role, error: e);
      return false;
    }
  }

  Future<bool> verifyOtp({
    required String phoneNumber,
    required String code,
    String? displayName,
    String? tenantName,
  }) async {
    try {
      final tokens = await _authApi.verifyOtp(
        phoneNumber: phoneNumber,
        code: code,
        displayName: displayName,
        tenantName: tenantName,
      );
      await _tokenStorage.saveTokens(accessToken: tokens.accessToken, refreshToken: tokens.refreshToken);
      state = AuthState(status: AuthStatus.authenticated, role: _decodeRole(tokens.accessToken));
      return true;
    } on AppException catch (e) {
      state = AuthState(status: AuthStatus.unauthenticated, error: e);
      return false;
    }
  }

  Future<void> logout() async {
    await _tokenStorage.clear();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  UserRole? _decodeRole(String accessToken) {
    final payload = decodeJwtPayload(accessToken);
    return userRoleFromJson(payload?['role'] as String?);
  }
}

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(apiClientProvider)));

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.watch(authApiProvider), ref.watch(tokenStorageProvider));
});
