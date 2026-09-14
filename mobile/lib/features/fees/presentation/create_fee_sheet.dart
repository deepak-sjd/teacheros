import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../students/data/student_model.dart';
import '../../students/data/student_provider.dart';
import '../data/fee_provider.dart';

Future<void> showCreateFeeSheet(BuildContext context, WidgetRef ref) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => const _CreateFeeSheet(),
  );
}

class _CreateFeeSheet extends ConsumerStatefulWidget {
  const _CreateFeeSheet();

  @override
  ConsumerState<_CreateFeeSheet> createState() => _CreateFeeSheetState();
}

class _CreateFeeSheetState extends ConsumerState<_CreateFeeSheet> {
  final _amountController = TextEditingController();
  List<Student>? _students;
  String? _selectedStudentId;
  DateTime _dueDate = DateTime.now().add(const Duration(days: 7));
  bool _isLoadingStudents = true;
  bool _isSubmitting = false;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    _loadStudents();
  }

  Future<void> _loadStudents() async {
    try {
      final students = await ref.read(studentApiProvider).listAll();
      if (!mounted) return;
      setState(() {
        _students = students;
        _isLoadingStudents = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoadingStudents = false);
    }
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _pickDueDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _dueDate,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) setState(() => _dueDate = picked);
  }

  Future<void> _submit() async {
    final amount = double.tryParse(_amountController.text.trim());
    setState(() {
      _errorText = _selectedStudentId == null
          ? 'Choose a student'
          : (amount == null || amount <= 0)
              ? 'Enter a valid amount'
              : null;
    });
    if (_errorText != null) return;

    setState(() => _isSubmitting = true);

    final success = await ref
        .read(feeProvider.notifier)
        .createDue(studentId: _selectedStudentId!, amountDue: amount!, dueDate: _dueDate);

    if (!mounted) return;

    if (success) {
      Navigator.of(context).pop();
    } else {
      setState(() {
        _isSubmitting = false;
        _errorText = "Couldn't create this fee. Try again.";
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
                decoration:
                    BoxDecoration(color: AppColors.divider, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            const SizedBox(height: AppSpace.md),
            Text('New fee due', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: AppSpace.lg),
            if (_isLoadingStudents)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: AppSpace.lg),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_students == null || _students!.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpace.md),
                child: Text(
                  'Add a student to a batch first before creating a fee.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              )
            else
              DropdownButtonFormField<String>(
                initialValue: _selectedStudentId,
                decoration: const InputDecoration(labelText: 'Student'),
                items: _students!
                    .map((s) => DropdownMenuItem(value: s.id, child: Text(s.fullName)))
                    .toList(),
                onChanged: (value) => setState(() => _selectedStudentId = value),
              ),
            const SizedBox(height: AppSpace.md),
            TextField(
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'Amount due (₹)'),
            ),
            const SizedBox(height: AppSpace.md),
            InkWell(
              onTap: _pickDueDate,
              borderRadius: BorderRadius.circular(AppRadius.md),
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Due date'),
                child: Text(DateFormat('d MMMM yyyy').format(_dueDate)),
              ),
            ),
            if (_errorText != null) ...[
              const SizedBox(height: AppSpace.sm),
              Text(_errorText!,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.error)),
            ],
            const SizedBox(height: AppSpace.lg),
            FilledButton(
              onPressed:
                  (_isSubmitting || _students == null || _students!.isEmpty) ? null : _submit,
              child: _isSubmitting
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Create fee'),
            ),
          ],
        ),
      ),
    );
  }
}
