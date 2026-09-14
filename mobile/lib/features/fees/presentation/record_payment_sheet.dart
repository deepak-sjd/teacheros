import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../data/fee_model.dart';
import '../data/fee_provider.dart';

Future<void> showRecordPaymentSheet(BuildContext context, WidgetRef ref, Fee fee, String studentName) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => _RecordPaymentSheet(fee: fee, studentName: studentName),
  );
}

class _RecordPaymentSheet extends ConsumerStatefulWidget {
  const _RecordPaymentSheet({required this.fee, required this.studentName});

  final Fee fee;
  final String studentName;

  @override
  ConsumerState<_RecordPaymentSheet> createState() => _RecordPaymentSheetState();
}

class _RecordPaymentSheetState extends ConsumerState<_RecordPaymentSheet> {
  late final TextEditingController _amountController =
      TextEditingController(text: widget.fee.amountRemaining.toStringAsFixed(0));
  String _method = 'CASH';
  bool _isSubmitting = false;
  String? _errorText;

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final amount = double.tryParse(_amountController.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _errorText = 'Enter a valid amount');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorText = null;
    });

    final success = await ref
        .read(feeProvider.notifier)
        .recordPayment(feeId: widget.fee.id, amountPaid: amount, method: _method);

    if (!mounted) return;

    if (success) {
      Navigator.of(context).pop();
    } else {
      setState(() {
        _isSubmitting = false;
        _errorText = "Couldn't record the payment. Try again.";
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
            Text('Record payment', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 2),
            Text(
              '${widget.studentName} · ₹${widget.fee.amountRemaining.toStringAsFixed(0)} remaining',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpace.lg),
            TextField(
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              autofocus: true,
              decoration: InputDecoration(labelText: 'Amount received (₹)', errorText: _errorText),
            ),
            const SizedBox(height: AppSpace.md),
            Wrap(
              spacing: AppSpace.sm,
              children: [
                for (final option in const [('CASH', 'Cash'), ('UPI', 'UPI'), ('BANK_TRANSFER', 'Bank transfer')])
                  ChoiceChip(
                    label: Text(option.$2),
                    selected: _method == option.$1,
                    onSelected: (_) => setState(() => _method = option.$1),
                  ),
              ],
            ),
            const SizedBox(height: AppSpace.lg),
            FilledButton(
              onPressed: _isSubmitting ? null : _submit,
              child: _isSubmitting
                  ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Record payment'),
            ),
          ],
        ),
      ),
    );
  }
}
