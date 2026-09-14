import 'package:dio/dio.dart';

import '../auth/token_storage.dart';
import '../config/env.dart';
import 'app_exception.dart';

/// The single Dio instance the whole app uses. Screens/repositories should
/// never construct their own Dio - always go through this so auth
/// attachment, token refresh, and error normalization happen consistently
/// everywhere instead of being copy-pasted per feature.
class ApiClient {
  ApiClient(this._tokenStorage) {
    _dio = Dio(BaseOptions(
      baseUrl: Env.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
    ));

    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await _tokenStorage.getAccessToken();
        if (token != null && !options.path.contains('/auth/')) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        final isAuthEndpoint = error.requestOptions.path.contains('/auth/');
        final isUnauthorized = error.response?.statusCode == 401;

        if (isUnauthorized && !isAuthEndpoint && !_isRetry(error.requestOptions)) {
          final refreshed = await _tryRefreshToken();
          if (refreshed) {
            final retryResponse = await _retry(error.requestOptions);
            handler.resolve(retryResponse);
            return;
          }
          // Refresh failed - the caller (AuthProvider) is responsible for
          // noticing the tokens are gone and routing back to login. We do
          // not force navigation from inside the API client.
          await _tokenStorage.clear();
        }

        handler.next(error);
      },
    ));
  }

  late final Dio _dio;
  final TokenStorage _tokenStorage;

  Dio get raw => _dio;

  bool _isRetry(RequestOptions options) => options.extra['isRetry'] == true;

  Future<bool> _tryRefreshToken() async {
    final refreshToken = await _tokenStorage.getRefreshToken();
    if (refreshToken == null) return false;

    try {
      final response = await _dio.post('/auth/refresh', data: {'refreshToken': refreshToken});
      await _tokenStorage.saveTokens(
        accessToken: response.data['accessToken'] as String,
        refreshToken: response.data['refreshToken'] as String,
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<Response<dynamic>> _retry(RequestOptions requestOptions) {
    final token = _tokenStorage.getAccessToken();
    requestOptions.extra['isRetry'] = true;
    return token.then((t) {
      requestOptions.headers['Authorization'] = 'Bearer $t';
      return _dio.fetch(requestOptions);
    });
  }

  /// Converts any Dio error into the app-wide AppException shape. Call this
  /// from repository catch blocks so screens only ever deal with one error
  /// type, never raw DioException.
  static AppException toAppException(Object error) {
    if (error is DioException) {
      if (error.type == DioExceptionType.connectionError ||
          error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.receiveTimeout) {
        return AppException.network();
      }
      final data = error.response?.data;
      if (data is Map<String, dynamic>) {
        return AppException(
          code: data['code'] as String? ?? 'UNKNOWN_ERROR',
          message: data['message'] as String? ?? 'Something went wrong.',
          status: error.response?.statusCode,
          traceId: data['traceId'] as String?,
        );
      }
    }
    return AppException.unknown();
  }
}
