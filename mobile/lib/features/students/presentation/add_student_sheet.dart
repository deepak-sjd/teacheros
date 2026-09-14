import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../data/student_provider.dart';

Future<void> showAddStudentSheet(BuildContext context, WidgetRef ref, String batchId) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => _AddStudentSheet(batchId: batchId),
  );
}

class _AddStudentSheet extends ConsumerStatefulWidget {
  const _AddStudentSheet({required this.batchId});

  final String batchId;

  @override
  ConsumerState<_AddStudentSheet> createState() => _AddStudentSheetState();
}

class _AddStudentSheetState extends ConsumerState<_AddStudentSheet> {
  final _nameController = TextEditingController();
  final _gradeController = TextEditingController();
  final _phoneController = TextEditingController();
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

    final success = await ref.read(studentsForBatchProvider(widget.batchId).notifier).create(
          fullName: name,
          grade: _gradeController.text.trim(),
          guardianPhone: phone,
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
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
        ),
        padding: const EdgeInsets.fromLTRB(AppSpace.lg, AppSpace.md, AppSpace.lg, AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(color: AppColors.divider, borderRadius: BorderRadius.circular(2)),
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
              decoration: const InputDecoration(labelText: 'Grade (optional)', hintText: 'e.g. 8'),
            ),
            const SizedBox(height: AppSpace.md),
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(labelText: "Guardian's phone number", errorText: _phoneError),
            ),
            const SizedBox(height: AppSpace.lg),
            FilledButton(
              onPressed: _isSubmitting ? null : _submit,
              child: _isSubmitting
                  ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Add student'),
            ),
          ],
        ),
      ),
    );
  }
}
