import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/async_state_view.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../data/batch_model.dart';
import '../data/batch_provider.dart';
import 'create_batch_sheet.dart';

class BatchesScreen extends ConsumerWidget {
  const BatchesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(batchProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Your batches'), actions: const [SignOutButton()]),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showCreateBatchSheet(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('New batch'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(batchProvider.notifier).load(),
        child: AsyncStateView<List<Batch>>(
          isLoading: state.isLoading,
          error: state.error,
          data: state.items,
          onRetry: () => ref.read(batchProvider.notifier).load(),
          isEmpty: (items) => items.isEmpty,
          emptyTitle: 'No batches yet',
          emptyMessage: 'Create your first batch to start adding students and marking attendance.',
          emptyAction: FilledButton(
            onPressed: () => showCreateBatchSheet(context, ref),
            child: const Text('Create a batch'),
          ),
          builder: (context, batches) => ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.sm, AppSpace.md, 96),
            itemCount: batches.length,
            itemBuilder: (context, index) {
              final batch = batches[index];
              return _AnimatedListItem(
                index: index,
                child: _BatchTile(batch: batch),
              );
            },
          ),
        ),
      ),
    );
  }
}

class _BatchTile extends StatelessWidget {
  const _BatchTile({required this.batch});

  final Batch batch;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpace.sm),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: () => context.push('/batches/${batch.id}/students', extra: batch.name),
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.md),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppColors.chalkboardTint,
                  borderRadius: BorderRadius.circular(AppRadius.sm),
                ),
                alignment: Alignment.center,
                child: Text(
                  batch.name.isNotEmpty ? batch.name[0].toUpperCase() : '?',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(color: AppColors.chalkboardDark),
                ),
              ),
              const SizedBox(width: AppSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(batch.name, style: Theme.of(context).textTheme.titleMedium),
                    if (batch.subject != null || batch.scheduleSummary != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        [batch.subject, batch.scheduleSummary].where((s) => s != null && s.isNotEmpty).join(' · '),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.inkMuted),
            ],
          ),
        ),
      ),
    );
  }
}

/// A single, orchestrated entrance: each row fades and rises into place in
/// a short staggered sequence when the list first appears. Deliberately
/// not applied anywhere else (no per-card hover effects, no repeated
/// animation on every rebuild) - one motion moment, not scattered ones.
class _AnimatedListItem extends StatelessWidget {
  const _AnimatedListItem({required this.index, required this.child});

  final int index;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: 260 + (index.clamp(0, 8) * 40)),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) {
        return Opacity(
          opacity: value,
          child: Transform.translate(offset: Offset(0, (1 - value) * 12), child: child),
        );
      },
      child: child,
    );
  }
}
