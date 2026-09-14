/// Mirrors the backend's ErrorResponse envelope (see GlobalExceptionHandler
/// on the backend) so every screen can handle errors the same way instead
/// of parsing ad-hoc messages per endpoint.
class AppException implements Exception {
  AppException({
    required this.code,
    required this.message,
    this.status,
    this.traceId,
  });

  /// Thrown when the server is unreachable at all (no response received) -
  /// distinct from a real error response, since the UI copy should be
  /// "check your connection" rather than a server-provided message.
  factory AppException.network() => AppException(
        code: 'NETWORK_ERROR',
        message: 'Unable to reach the server. Check your connection and try again.',
      );

  factory AppException.unknown() => AppException(
        code: 'UNKNOWN_ERROR',
        message: 'Something went wrong. Please try again.',
      );

  final String code;
  final String message;
  final int? status;
  final String? traceId;

  @override
  String toString() => 'AppException(code: $code, message: $message, traceId: $traceId)';
}
