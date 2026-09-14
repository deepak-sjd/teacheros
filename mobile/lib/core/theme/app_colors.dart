import 'package:flutter/material.dart';

/// Named color tokens. Never reference a raw hex value outside this file -
/// every screen pulls from here so the palette stays consistent and a
/// future rebrand only touches one place.
///
/// Matches the web app's palette (ink blue + chalk yellow) so the product
/// feels like one system across platforms, not two different apps.
class AppColors {
  AppColors._();

  // Base
  static const ink = Color(0xFF1C2541);
  static const inkMuted = Color(0xFF5C6784);
  static const paper = Color(0xFFFBF9F4);
  static const surface = Color(0xFFFFFFFF);
  static const divider = Color(0xFFE4E0D4);

  // Brand - "chalkboard" is the name kept for the primary action color
  // (buttons, active nav, banners) so every screen that already references
  // AppColors.chalkboard picks up the new palette automatically.
  static const chalkboard = Color(0xFF1C2541); // = ink
  static const chalkboardDark = Color(0xFF141B33);
  static const chalkboardTint = Color(0xFFE9EBF2);

  // Accent - the sparing highlight color, used the way the web app uses it
  // (small touches only: an icon, a highlight bar - never large fills).
  static const chalkYellow = Color(0xFFF2B705);
  static const chalkYellowTint = Color(0xFFFDF3D8);

  // Status - the only place saturated color is used outside brand actions
  static const present = Color(0xFF2F9E5B);
  static const presentTint = Color(0xFFE4F5EA);
  static const pending = Color(0xFFB8860B);
  static const pendingTint = Color(0xFFFBF0D9);
  static const absent = Color(0xFFD64545);
  static const absentTint = Color(0xFFFBE7E7);

  // Feedback
  static const error = Color(0xFFD64545);
  static const success = Color(0xFF2F9E5B);
}
