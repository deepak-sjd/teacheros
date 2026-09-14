import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../../shared/student_summary_view.dart';
import '../../students/data/student_model.dart';
import '../../students/data/student_provider.dart';

/// The parent's entire app is this one screen - a child picker (only shown
/// if there's more than one linked child) plus that child's summary.
/// Deliberately no bottom nav / multiple tabs: one parent, usually one or
/// two children, nothing else to navigate to yet.
class MyChildrenScreen extends ConsumerStatefulWidget {
  const MyChildrenScreen({super.key});

  @override
  ConsumerState<MyChildrenScreen> createState() => _MyChildrenScreenState();
}

class _MyChildrenScreenState extends ConsumerState<MyChildrenScreen> {
  String? _selectedStudentId;

  @override
  Widget build(BuildContext context) {
    final childrenAsync = ref.watch(myChildrenProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('My children'), actions: const [SignOutButton()]),
      body: childrenAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => _ErrorState(onRetry: () => ref.invalidate(myChildrenProvider)),
        data: (children) {
          if (children.isEmpty) {
            return const _NoChildrenLinked();
          }

          final activeId = _selectedStudentId ?? children.first.id;

          return Column(
            children: [
              if (children.length > 1)
                _ChildPicker(
                  children: children,
                  selectedId: activeId,
                  onSelect: (id) => setState(() => _selectedStudentId = id),
                ),
              Expanded(child: StudentSummaryView(studentId: activeId)),
            ],
          );
        },
      ),
    );
  }
}

class _ChildPicker extends StatelessWidget {
  const _ChildPicker({required this.children, required this.selectedId, required this.onSelect});

  final List<Student> children;
  final String selectedId;
  final void Function(String id) onSelect;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppSpace.md, vertical: AppSpace.xs),
        children: [
          for (final child in children)
            Padding(
              padding: const EdgeInsets.only(right: AppSpace.sm),
              child: ChoiceChip(
                label: Text(child.fullName),
                selected: child.id == selectedId,
                onSelected: (_) => onSelect(child.id),
              ),
            ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_rounded, size: 40, color: AppColors.inkMuted),
            const SizedBox(height: AppSpace.md),
            Text("Couldn't load your children", style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpace.md),
            OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      ),
    );
  }
}

class _NoChildrenLinked extends StatelessWidget {
  const _NoChildrenLinked();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'No children linked to this account',
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpace.xs),
            Text(
              "Your account links automatically using the guardian phone number your child's teacher has on file. "
              "If that's wrong, ask the teacher to check the phone number on your child's student record.",
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
