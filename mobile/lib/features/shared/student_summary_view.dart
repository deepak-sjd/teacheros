import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_spacing.dart';
import '../../core/widgets/status_pill.dart';
import '../attendance/data/attendance_provider.dart';
import '../fees/data/fee_model.dart';
import '../fees/data/fee_provider.dart';

/// Shared between the parent view ("this child's summary") and the student
/// view ("my summary") - same data shape, same read-only presentation,
/// just a different studentId source. Keeping this in one widget avoids
/// building two near-identical screens that quietly drift apart over time.
class StudentSummaryView extends ConsumerWidget {
  const StudentSummaryView({super.key, required this.studentId});

  final String studentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final percentageAsync = ref.watch(attendancePercentageProvider(studentId));
    final feesAsync = ref.watch(feesForStudentProvider(studentId));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(attendancePercentageProvider(studentId));
        ref.invalidate(feesForStudentProvider(studentId));
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.md, AppSpace.md, AppSpace.xxl),
        children: [
          _AttendanceCard(percentageAsync: percentageAsync),
          const SizedBox(height: AppSpace.lg),
          Text('Fee history', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpace.sm),
          feesAsync.when(
            data: (fees) => fees.isEmpty
                ? const _EmptyFees()
                : Column(children: [for (final fee in fees) _FeeTile(fee: fee)]),
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: AppSpace.lg),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (_, __) => const Text("Couldn't load fee history."),
          ),
        ],
      ),
    );
  }
}

class _AttendanceCard extends StatelessWidget {
  const _AttendanceCard({required this.percentageAsync});

  final AsyncValue<double> percentageAsync;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.md),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Attendance', style: Theme.of(context).textTheme.labelMedium),
                  const SizedBox(height: AppSpace.xs),
                  percentageAsync.when(
                    data: (pct) => Text('${pct.toStringAsFixed(0)}%', style: Theme.of(context).textTheme.displaySmall),
                    loading: () => const SizedBox(
                      height: 32,
                      width: 60,
                      child: LinearProgressIndicator(),
                    ),
                    error: (_, __) => Text("Couldn't load", style: Theme.of(context).textTheme.bodySmall),
                  ),
                ],
              ),
            ),
            const Icon(Icons.event_available_outlined, size: 32),
          ],
        ),
      ),
    );
  }
}

class _FeeTile extends StatelessWidget {
  const _FeeTile({required this.fee});

  final Fee fee;

  @override
  Widget build(BuildContext context) {
    final isOverdue = fee.status == FeeStatus.overdue || fee.dueDate.isBefore(DateTime.now());
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpace.sm),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.md),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Due ${DateFormat('d MMM yyyy').format(fee.dueDate)}',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 2),
                    Text(
                      '₹${fee.amountDue.toStringAsFixed(0)} due · ₹${fee.amountPaid.toStringAsFixed(0)} paid',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              StatusPill(
                label: fee.status == FeeStatus.paid
                    ? 'Paid'
                    : isOverdue
                        ? 'Overdue'
                        : fee.status == FeeStatus.partiallyPaid
                            ? 'Partially paid'
                            : 'Pending',
                tone: fee.status == FeeStatus.paid
                    ? PillTone.positive
                    : isOverdue
                        ? PillTone.negative
                        : fee.status == FeeStatus.partiallyPaid
                            ? PillTone.attention
                            : PillTone.neutral,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyFees extends StatelessWidget {
  const _EmptyFees();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpace.lg),
      child: Center(
        child: Text('No fees on record', style: Theme.of(context).textTheme.bodySmall),
      ),
    );
  }
}
