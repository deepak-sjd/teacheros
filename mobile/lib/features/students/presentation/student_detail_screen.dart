import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/status_pill.dart';
import '../../attendance/data/attendance_provider.dart';
import '../../fees/data/fee_model.dart';
import '../../fees/data/fee_provider.dart';

/// A single student's own page - attendance percentage and fee history.
/// Reached by tapping a student row from the batch roster or the
/// all-students list. Reuses attendancePercentageProvider and
/// feesForStudentProvider, which already existed for the parent/student
/// self-service views - this screen is the teacher-side reuse of the same
/// data those providers' doc comments anticipated.
class StudentDetailScreen extends ConsumerWidget {
  const StudentDetailScreen({super.key, required this.studentId, required this.studentName});

  final String studentId;
  final String studentName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final attendanceAsync = ref.watch(attendancePercentageProvider(studentId));
    final feesAsync = ref.watch(feesForStudentProvider(studentId));

    return Scaffold(
      appBar: AppBar(title: Text(studentName)),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(attendancePercentageProvider(studentId));
          ref.invalidate(feesForStudentProvider(studentId));
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(AppSpace.md),
          children: [
            attendanceAsync.when(
              data: (percentage) => _AttendanceCard(percentage: percentage),
              loading: () => const _AttendanceCard(percentage: null),
              error: (_, __) => const _ErrorCard(message: "Couldn't load attendance."),
            ),
            const SizedBox(height: AppSpace.lg),
            Text('Fees', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpace.sm),
            feesAsync.when(
              data: (fees) => fees.isEmpty
                  ? Padding(
                      padding: const EdgeInsets.symmetric(vertical: AppSpace.md),
                      child: Text(
                        'No fees recorded for this student yet.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    )
                  : Column(children: fees.map((fee) => _FeeHistoryTile(fee: fee)).toList()),
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: AppSpace.lg),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, __) => const _ErrorCard(message: "Couldn't load fee history."),
            ),
          ],
        ),
      ),
    );
  }
}

class _AttendanceCard extends StatelessWidget {
  const _AttendanceCard({required this.percentage});

  final double? percentage;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Row(
          children: [
            SizedBox(
              width: 56,
              height: 56,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  CircularProgressIndicator(
                    value: percentage == null ? null : (percentage! / 100).clamp(0, 1),
                    strokeWidth: 5,
                    backgroundColor: AppColors.divider,
                    color: AppColors.chalkboard,
                  ),
                  if (percentage != null)
                    Text('${percentage!.toStringAsFixed(0)}%',
                        style: Theme.of(context).textTheme.labelMedium),
                ],
              ),
            ),
            const SizedBox(width: AppSpace.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Overall attendance', style: Theme.of(context).textTheme.titleMedium),
                  Text('Across all recorded sessions',
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FeeHistoryTile extends StatelessWidget {
  const _FeeHistoryTile({required this.fee});

  final Fee fee;

  @override
  Widget build(BuildContext context) {
    final isPaid = fee.status == FeeStatus.paid;
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpace.sm),
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.md),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('₹${fee.amountDue.toStringAsFixed(0)}',
                      style: Theme.of(context).textTheme.titleMedium),
                  Text('Due ${DateFormat('d MMM yyyy').format(fee.dueDate)}',
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
            StatusPill(
              label: isPaid
                  ? 'Paid'
                  : (fee.status == FeeStatus.partiallyPaid ? 'Partially paid' : 'Pending'),
              tone: isPaid
                  ? PillTone.positive
                  : (fee.status == FeeStatus.partiallyPaid ? PillTone.attention : PillTone.neutral),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.md),
        child: Text(message,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.error)),
      ),
    );
  }
}
