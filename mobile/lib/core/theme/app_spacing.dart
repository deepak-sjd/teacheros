/// A consistent spacing grid. Every padding/gap in the app should be one of
/// these values - resist the urge to write a one-off "18" or "22" inline.
class AppSpace {
  AppSpace._();

  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
  static const xxl = 48.0;
}

class AppRadius {
  AppRadius._();

  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const pill = 999.0;
}

/// Minimum touch target size - teachers are often tapping quickly, one
/// hand, sometimes without looking closely. Never go below this.
const double kMinTouchTarget = 48.0;
