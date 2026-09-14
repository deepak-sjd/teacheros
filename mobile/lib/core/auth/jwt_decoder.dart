import 'dart:convert';

/// Decodes a JWT's payload segment without verifying the signature. This is
/// safe here because we never trust this decoded data for authorization -
/// see user_role.dart's doc comment. It's purely so the app can decide
/// which shell to show without an extra network round trip.
Map<String, dynamic>? decodeJwtPayload(String token) {
  final parts = token.split('.');
  if (parts.length != 3) return null;

  try {
    var payload = parts[1];
    // JWT base64url segments omit padding; base64Url.decode requires it.
    payload += '=' * ((4 - payload.length % 4) % 4);
    final decoded = utf8.decode(base64Url.decode(payload));
    return jsonDecode(decoded) as Map<String, dynamic>;
  } catch (_) {
    return null;
  }
}
