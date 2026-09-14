import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

enum PillTone { positive, attention, negative, neutral }

/// A small colored status label - "Present", "Paid", "Overdue", etc. This
/// is deliberately the only place in the UI that uses saturated fill color;
/// everything else stays quiet so this actually draws the eye when it
/// matters (see design principle: status is color, nowhere else is).
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.label, required this.tone});

  final String label;
  final PillTone tone;

  (Color bg, Color fg) _colors() {
    switch (tone) {
      case PillTone.positive:
        return (AppColors.presentTint, AppColors.present);
      case PillTone.attention:
        return (AppColors.pendingTint, AppColors.pending);
      case PillTone.negative:
        return (AppColors.absentTint, AppColors.absent);
      case PillTone.neutral:
        return (AppColors.divider, AppColors.inkMuted);
    }
  }

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = _colors();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpace.sm, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppRadius.pill)),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(color: fg, fontWeight: FontWeight.w600),
      ),
    );
  }
}
