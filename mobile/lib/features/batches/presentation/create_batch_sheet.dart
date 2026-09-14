import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../data/batch_provider.dart';

/// Shows the create-batch bottom sheet. Bottom sheets keep the teacher in
/// context (they can still see the list behind it) rather than a full page
/// navigation for what's a 10-second task.
Future<void> showCreateBatchSheet(BuildContext context, WidgetRef ref) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => const _CreateBatchSheet(),
  );
}

class _CreateBatchSheet extends ConsumerStatefulWidget {
  const _CreateBatchSheet();

  @override
  ConsumerState<_CreateBatchSheet> createState() => _CreateBatchSheetState();
}

class _CreateBatchSheetState extends ConsumerState<_CreateBatchSheet> {
  final _nameController = TextEditingController();
  final _subjectController = TextEditingController();
  final _scheduleController = TextEditingController();
  bool _isSubmitting = false;
  String? _errorText;

  @override
  void dispose() {
    _nameController.dispose();
    _subjectController.dispose();
    _scheduleController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      setState(() => _errorText = 'Give this batch a name');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorText = null;
    });

    final success = await ref.read(batchProvider.notifier).create(
          name: name,
          subject: _subjectController.text.trim(),
          scheduleSummary: _scheduleController.text.trim(),
        );

    if (!mounted) return;

    if (success) {
      Navigator.of(context).pop();
    } else {
      setState(() {
        _isSubmitting = false;
        _errorText = 'Could not create the batch. Try again.';
      });
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
            Text('New batch', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: AppSpace.lg),
            TextField(
              controller: _nameController,
              autofocus: true,
              decoration: InputDecoration(labelText: 'Batch name', hintText: 'e.g. Class 8 Maths', errorText: _errorText),
            ),
            const SizedBox(height: AppSpace.md),
            TextField(
              controller: _subjectController,
              decoration: const InputDecoration(labelText: 'Subject (optional)'),
            ),
            const SizedBox(height: AppSpace.md),
            TextField(
              controller: _scheduleController,
              decoration: const InputDecoration(labelText: 'Schedule (optional)', hintText: 'e.g. Mon/Wed/Fri 5-6pm'),
            ),
            const SizedBox(height: AppSpace.lg),
            FilledButton(
              onPressed: _isSubmitting ? null : _submit,
              child: _isSubmitting
                  ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Create batch'),
            ),
          ],
        ),
      ),
    );
  }
}
