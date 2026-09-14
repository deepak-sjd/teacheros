import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../../batches/data/batch_provider.dart';
import '../../fees/data/fee_model.dart';
import '../../fees/data/fee_provider.dart';
import '../../students/data/student_provider.dart';

/// Today's snapshot - batches, students, and fees pending, at a glance.
/// Matches the web app's Overview page. Deliberately just three numbers,
/// not a chart-heavy dashboard - this is meant to be readable in two
/// seconds, not studied.
class OverviewScreen extends ConsumerWidget {
  const OverviewScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final batches = ref.watch(batchProvider);
    final students = ref.watch(allStudentsProvider);
    final fees = ref.watch(feeProvider);

    final pendingTotal =
        (fees.items ?? const <Fee>[]).fold<double>(0, (sum, f) => sum + f.amountRemaining);

    return Scaffold(
      appBar: AppBar(title: const Text('Overview'), actions: const [SignOutButton()]),
      body: RefreshIndicator(
        onRefresh: () async {
          await ref.read(batchProvider.notifier).load();
          await ref.read(allStudentsProvider.notifier).load();
          await ref.read(feeProvider.notifier).load();
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(AppSpace.md),
          children: [
            _StatCard(
              label: 'Active batches',
              value: batches.isLoading ? null : '${batches.items?.length ?? 0}',
              icon: Icons.groups_outlined,
            ),
            const SizedBox(height: AppSpace.sm),
            _StatCard(
              label: 'Students enrolled',
              value: students.isLoading ? null : '${students.items?.length ?? 0}',
              icon: Icons.people_outline,
            ),
            const SizedBox(height: AppSpace.sm),
            _StatCard(
              label: 'Fees pending',
              value: fees.isLoading ? null : '₹${pendingTotal.toStringAsFixed(0)}',
              subLabel: fees.isLoading
                  ? null
                  : '${fees.items?.length ?? 0} student${(fees.items?.length ?? 0) == 1 ? '' : 's'}',
              icon: Icons.payments_outlined,
            ),
          ],
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.label, required this.value, this.subLabel, required this.icon});

  final String label;
  final String? value;
  final String? subLabel;
  final IconData icon;

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
                  Text(label, style: Theme.of(context).textTheme.labelMedium),
                  const SizedBox(height: AppSpace.xs),
                  value == null
                      ? const SizedBox(height: 32, width: 60, child: LinearProgressIndicator())
                      : Text(value!, style: Theme.of(context).textTheme.displaySmall),
                  if (subLabel != null) ...[
                    const SizedBox(height: 2),
                    Text(subLabel!, style: Theme.of(context).textTheme.bodySmall),
                  ],
                ],
              ),
            ),
            Icon(icon, size: 32, color: AppColors.inkMuted),
          ],
        ),
      ),
    );
  }
}
