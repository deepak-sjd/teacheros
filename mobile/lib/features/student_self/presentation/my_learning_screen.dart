import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../../shared/student_summary_view.dart';
import '../../students/data/student_provider.dart';

/// The student's entire app is this one screen - their own attendance and
/// fee summary. No batch browsing, no editing - this is a read-only view
/// of what the teacher has recorded, matching the parent view's scope.
class MyLearningScreen extends ConsumerWidget {
  const MyLearningScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final studentAsync = ref.watch(myStudentRecordProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('My learning'), actions: const [SignOutButton()]),
      body: studentAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpace.lg),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.wifi_off_rounded, size: 40, color: AppColors.inkMuted),
                const SizedBox(height: AppSpace.md),
                Text("Couldn't load your record", style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: AppSpace.md),
                OutlinedButton(
                  onPressed: () => ref.invalidate(myStudentRecordProvider),
                  child: const Text('Try again'),
                ),
              ],
            ),
          ),
        ),
        data: (student) => Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.md, AppSpace.md, 0),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(student.fullName, style: Theme.of(context).textTheme.headlineSmall),
              ),
            ),
            Expanded(child: StudentSummaryView(studentId: student.id)),
          ],
        ),
      ),
    );
  }
}
