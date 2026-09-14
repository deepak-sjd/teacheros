import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../data/attendance_api.dart';

/// Tapping cycles Present -> Absent -> Late -> Present. The color and icon
/// animate between states rather than snapping, so the tap reads as a
/// confirmed action rather than a silent toggle - this is the screen's one
/// deliberate motion moment (see design principles: motion answers an
/// action, it doesn't decorate the page).
class AttendanceStatusChip extends StatelessWidget {
  const AttendanceStatusChip({super.key, required this.status, required this.onTap});

  final AttendanceStatus status;
  final VoidCallback onTap;

  (Color bg, Color fg, IconData icon, String label) _visuals() {
    switch (status) {
      case AttendanceStatus.present:
        return (AppColors.presentTint, AppColors.present, Icons.check_rounded, 'Present');
      case AttendanceStatus.absent:
        return (AppColors.absentTint, AppColors.absent, Icons.close_rounded, 'Absent');
      case AttendanceStatus.late:
        return (AppColors.pendingTint, AppColors.pending, Icons.schedule_rounded, 'Late');
    }
  }

  @override
  Widget build(BuildContext context) {
    final (bg, fg, icon, label) = _visuals();

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadius.pill),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        padding: const EdgeInsets.symmetric(horizontal: AppSpace.md, vertical: AppSpace.sm),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppRadius.pill)),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 180),
              transitionBuilder: (child, animation) => ScaleTransition(scale: animation, child: child),
              child: Icon(icon, key: ValueKey(status), color: fg, size: 18),
            ),
            const SizedBox(width: 6),
            Text(label, style: Theme.of(context).textTheme.labelMedium?.copyWith(color: fg)),
          ],
        ),
      ),
    );
  }
}
