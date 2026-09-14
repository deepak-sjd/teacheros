/// Environment configuration, injected at build time via --dart-define.
///
/// Example:
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
///
/// Never hardcode a production URL here as the default - the dev default
/// below is only correct for the Android emulator talking to a locally
/// running backend.
class Env {
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',
  );
}
