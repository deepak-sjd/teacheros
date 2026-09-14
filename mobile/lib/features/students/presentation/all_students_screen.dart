import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/async_state_view.dart';
import '../../auth/presentation/sign_out_button.dart';
import '../../batches/data/batch_model.dart';
import '../../batches/data/batch_provider.dart';
import '../data/student_model.dart';
import '../data/student_provider.dart';

/// Unlike StudentsScreen (one batch's roster), this shows every student in
/// the tenant and lets a teacher add one without picking a batch first -
/// matches the web app's "Students" nav item, and matches the backend,
/// which has always allowed a student to exist without a batch.
class AllStudentsScreen extends ConsumerWidget {
  const AllStudentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(allStudentsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Students'), actions: const [SignOutButton()]),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showAddStudentSheet(context, ref),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('Add student'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(allStudentsProvider.notifier).load(),
        child: AsyncStateView<List<Student>>(
          isLoading: state.isLoading,
          error: state.error,
          data: state.items,
          onRetry: () => ref.read(allStudentsProvider.notifier).load(),
          isEmpty: (items) => items.isEmpty,
          emptyTitle: 'No students yet',
          emptyMessage: 'Add your first student to start tracking attendance and fees.',
          emptyAction: FilledButton(
            onPressed: () => _showAddStudentSheet(context, ref),
            child: const Text('Add a student'),
          ),
          builder: (context, students) => ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.md, AppSpace.md, 96),
            itemCount: students.length,
            separatorBuilder: (_, __) => const SizedBox(height: AppSpace.sm),
            itemBuilder: (context, index) => _StudentRow(student: students[index]),
          ),
        ),
      ),
    );
  }

  void _showAddStudentSheet(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const _AddStudentSheet(),
    );
  }
}

class _StudentRow extends StatelessWidget {
  const _StudentRow({required this.student});

  final Student student;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: () => context.push('/students/${student.id}', extra: student.fullName),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpace.md, vertical: AppSpace.sm),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: AppColors.chalkboardTint,
                foregroundColor: AppColors.chalkboardDark,
                child: Text(student.fullName.isNotEmpty ? student.fullName[0].toUpperCase() : '?'),
              ),
              const SizedBox(width: AppSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(student.fullName, style: Theme.of(context).textTheme.titleMedium),
                    Text(
                      student.grade != null && student.grade!.isNotEmpty
                          ? 'Grade ${student.grade} · ${student.guardianPhone}'
                          : student.guardianPhone,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
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

class _AddStudentSheet extends ConsumerStatefulWidget {
  const _AddStudentSheet();

  @override
  ConsumerState<_AddStudentSheet> createState() => _AddStudentSheetState();
}

class _AddStudentSheetState extends ConsumerState<_AddStudentSheet> {
  final _nameController = TextEditingController();
  final _gradeController = TextEditingController();
  final _phoneController = TextEditingController();
  String? _batchId;
  bool _isSubmitting = false;
  String? _nameError;
  String? _phoneError;

  @override
  void dispose() {
    _nameController.dispose();
    _gradeController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final name = _nameController.text.trim();
    final phone = _phoneController.text.trim();

    setState(() {
      _nameError = name.isEmpty ? "Enter the student's name" : null;
      _phoneError = phone.isEmpty ? "Enter a guardian's phone number" : null;
    });
    if (_nameError != null || _phoneError != null) return;

    setState(() => _isSubmitting = true);

    final success = await ref.read(allStudentsProvider.notifier).create(
          fullName: name,
          grade: _gradeController.text.trim(),
          guardianPhone: phone,
          batchId: _batchId,
        );

    if (!mounted) return;

    if (success) {
      Navigator.of(context).pop();
    } else {
      setState(() => _isSubmitting = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Couldn't add the student. Try again.")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final batchState = ref.watch(batchProvider);

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
        ),
        padding: const EdgeInsets.fromLTRB(AppSpace.lg, AppSpace.md, AppSpace.lg, AppSpace.lg),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                      color: AppColors.divider, borderRadius: BorderRadius.circular(2)),
                ),
              ),
              const SizedBox(height: AppSpace.md),
              Text('Add student', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: AppSpace.lg),
              TextField(
                controller: _nameController,
                autofocus: true,
                decoration: InputDecoration(labelText: "Student's name", errorText: _nameError),
              ),
              const SizedBox(height: AppSpace.md),
              TextField(
                controller: _gradeController,
                decoration:
                    const InputDecoration(labelText: 'Grade (optional)', hintText: 'e.g. 8'),
              ),
              const SizedBox(height: AppSpace.md),
              TextField(
                controller: _phoneController,
                keyboardType: TextInputType.phone,
                decoration:
                    InputDecoration(labelText: "Guardian's phone number", errorText: _phoneError),
              ),
              const SizedBox(height: AppSpace.md),
              DropdownButtonFormField<String?>(
                initialValue: _batchId,
                decoration: const InputDecoration(labelText: 'Batch (optional)'),
                items: [
                  const DropdownMenuItem<String?>(value: null, child: Text('No batch yet')),
                  for (final batch in batchState.items ?? const <Batch>[])
                    DropdownMenuItem<String?>(value: batch.id, child: Text(batch.name)),
                ],
                onChanged: (value) => setState(() => _batchId = value),
              ),
              const SizedBox(height: AppSpace.lg),
              FilledButton(
                onPressed: _isSubmitting ? null : _submit,
                child: _isSubmitting
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Add student'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
