import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/async_state_view.dart';
import '../../students/data/student_model.dart';
import '../../students/data/student_provider.dart';
import '../data/attendance_api.dart';
import '../data/attendance_provider.dart';
import 'attendance_status_chip.dart';

class MarkAttendanceScreen extends ConsumerWidget {
  const MarkAttendanceScreen({super.key, required this.batchId, required this.batchName});

  final String batchId;
  final String batchName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final studentsState = ref.watch(studentsForBatchProvider(batchId));

    return Scaffold(
      appBar: AppBar(
        title: Text(batchName),
        actions: [
          if (studentsState.items != null && studentsState.items!.isNotEmpty)
            TextButton(
              onPressed: () => ref
                  .read(attendanceMarkingProvider(
                    AttendanceMarkingKey(batchId, studentsState.items!.map((s) => s.id).toList()),
                  ).notifier)
                  .markAllPresent(),
              child: const Text('Mark all present'),
            ),
        ],
      ),
      body: AsyncStateView<List<Student>>(
        isLoading: studentsState.isLoading,
        error: studentsState.error,
        data: studentsState.items,
        isEmpty: (items) => items.isEmpty,
        emptyTitle: 'No students to mark',
        emptyMessage: 'Add students to this batch first.',
        builder: (context, students) => _MarkingBody(batchId: batchId, students: students),
      ),
    );
  }
}

class _MarkingBody extends ConsumerWidget {
  const _MarkingBody({required this.batchId, required this.students});

  final String batchId;
  final List<Student> students;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = AttendanceMarkingKey(batchId, students.map((s) => s.id).toList());
    final marking = ref.watch(attendanceMarkingProvider(key));
    final notifier = ref.read(attendanceMarkingProvider(key).notifier);

    if (marking.isSubmitted) {
      return _SubmittedConfirmation(studentCount: students.length);
    }

    final presentCount = marking.statuses.values.where((s) => s == AttendanceStatus.present).length;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.sm, AppSpace.md, AppSpace.xs),
          child: Row(
            children: [
              Text(DateFormat('EEEE, d MMMM').format(DateTime.now()),
                  style: Theme.of(context).textTheme.bodySmall),
              const Spacer(),
              Text(
                '$presentCount of ${students.length} present',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.separated(
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.xs, AppSpace.md, AppSpace.md),
            itemCount: students.length,
            separatorBuilder: (_, __) => const Divider(),
            itemBuilder: (context, index) {
              final student = students[index];
              final status = marking.statuses[student.id] ?? AttendanceStatus.present;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpace.xs),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 18,
                      backgroundColor: AppColors.chalkboardTint,
                      foregroundColor: AppColors.chalkboardDark,
                      child: Text(
                          student.fullName.isNotEmpty ? student.fullName[0].toUpperCase() : '?'),
                    ),
                    const SizedBox(width: AppSpace.md),
                    Expanded(
                        child:
                            Text(student.fullName, style: Theme.of(context).textTheme.bodyLarge)),
                    AttendanceStatusChip(status: status, onTap: () => notifier.toggle(student.id)),
                  ],
                ),
              );
            },
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.sm, AppSpace.md, AppSpace.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (marking.error != null) ...[
                  Text(
                    marking.error!.message,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.error),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: AppSpace.sm),
                ],
                FilledButton(
                  onPressed: marking.isSubmitting ? null : () => notifier.submit(),
                  child: marking.isSubmitting
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('Save attendance'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _SubmittedConfirmation extends StatelessWidget {
  const _SubmittedConfirmation({required this.studentCount});

  final int studentCount;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: 1),
              duration: const Duration(milliseconds: 400),
              curve: Curves.elasticOut,
              builder: (context, value, child) => Transform.scale(scale: value, child: child),
              child: Container(
                width: 72,
                height: 72,
                decoration:
                    const BoxDecoration(color: AppColors.presentTint, shape: BoxShape.circle),
                child: const Icon(Icons.check_rounded, color: AppColors.present, size: 36),
              ),
            ),
            const SizedBox(height: AppSpace.lg),
            Text('Attendance saved', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: AppSpace.xs),
            Text(
              'Recorded for $studentCount student${studentCount == 1 ? '' : 's'} today.',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpace.lg),
            OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Done')),
          ],
        ),
      ),
    );
  }
}
