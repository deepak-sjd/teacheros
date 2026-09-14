import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/async_state_view.dart';
import '../../../core/widgets/status_pill.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../../students/data/student_provider.dart';
import '../data/fee_model.dart';
import '../data/fee_provider.dart';
import 'create_fee_sheet.dart';
import 'record_payment_sheet.dart';

class PendingFeesScreen extends ConsumerWidget {
  const PendingFeesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(feeProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Pending fees'), actions: const [SignOutButton()]),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showCreateFeeSheet(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('New fee'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(feeProvider.notifier).load(),
        child: AsyncStateView<List<Fee>>(
          isLoading: state.isLoading,
          error: state.error,
          data: state.items,
          onRetry: () => ref.read(feeProvider.notifier).load(),
          isEmpty: (items) => items.isEmpty,
          emptyTitle: "You're all caught up",
          emptyMessage: 'No pending fees right now. Tap "New fee" to record a due amount for a student.',
          builder: (context, fees) => ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.md, AppSpace.md, 96),
            itemCount: fees.length,
            separatorBuilder: (_, __) => const SizedBox(height: AppSpace.sm),
            itemBuilder: (context, index) => _FeeTile(fee: fees[index]),
          ),
        ),
      ),
    );
  }
}

class _FeeTile extends ConsumerWidget {
  const _FeeTile({required this.fee});

  final Fee fee;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final studentAsync = ref.watch(studentByIdProvider(fee.studentId));
    final isOverdue = fee.status == FeeStatus.overdue || fee.dueDate.isBefore(DateTime.now());

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.md),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  studentAsync.when(
                    data: (student) => Text(student.fullName, style: Theme.of(context).textTheme.titleMedium),
                    loading: () => const SizedBox(height: 18, width: 100, child: LinearProgressIndicator()),
                    error: (_, __) => Text('Student', style: Theme.of(context).textTheme.titleMedium),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '₹${fee.amountRemaining.toStringAsFixed(0)} due · ${DateFormat('d MMM').format(fee.dueDate)}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: AppSpace.xs),
                  StatusPill(
                    label: isOverdue
                        ? 'Overdue'
                        : fee.status == FeeStatus.partiallyPaid
                            ? 'Partially paid'
                            : 'Pending',
                    tone: isOverdue
                        ? PillTone.negative
                        : fee.status == FeeStatus.partiallyPaid
                            ? PillTone.attention
                            : PillTone.neutral,
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpace.sm),
            OutlinedButton(
              onPressed: () => studentAsync.whenData(
                (student) => showRecordPaymentSheet(context, ref, fee, student.fullName),
              ),
              child: const Text('Record payment'),
            ),
          ],
        ),
      ),
    );
  }
}
