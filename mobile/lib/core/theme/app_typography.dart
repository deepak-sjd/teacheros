import 'package:flutter/material.dart';

import 'app_colors.dart';

/// A deliberate type scale (loosely modular, ~1.2 ratio) with real weight
/// contrast, rather than accepting Flutter's default Material text theme.
/// Uses the platform's native system font (Roboto/San Francisco) - no
/// custom font asset to keep the app lightweight and avoid a licensing/
/// bundling decision that hasn't been made yet.
class AppTypography {
  AppTypography._();

  static const _base = TextStyle(color: AppColors.ink, height: 1.3);

  static TextTheme textTheme() {
    return TextTheme(
      displaySmall: _base.copyWith(fontSize: 32, fontWeight: FontWeight.w700, letterSpacing: -0.5),
      headlineMedium: _base.copyWith(fontSize: 24, fontWeight: FontWeight.w700, letterSpacing: -0.3),
      headlineSmall: _base.copyWith(fontSize: 20, fontWeight: FontWeight.w600),
      titleLarge: _base.copyWith(fontSize: 17, fontWeight: FontWeight.w600),
      titleMedium: _base.copyWith(fontSize: 15, fontWeight: FontWeight.w600),
      bodyLarge: _base.copyWith(fontSize: 16, fontWeight: FontWeight.w400, height: 1.45),
      bodyMedium: _base.copyWith(fontSize: 14, fontWeight: FontWeight.w400, height: 1.45),
      bodySmall: _base.copyWith(fontSize: 13, fontWeight: FontWeight.w400, color: AppColors.inkMuted),
      labelLarge: _base.copyWith(fontSize: 15, fontWeight: FontWeight.w600),
      labelMedium: _base.copyWith(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.inkMuted),
      labelSmall: _base.copyWith(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.inkMuted),
    );
  }
}
