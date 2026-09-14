import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/auth_provider.dart';

class OtpVerifyScreen extends ConsumerStatefulWidget {
  const OtpVerifyScreen({super.key, required this.phoneNumber});

  final String phoneNumber;

  @override
  ConsumerState<OtpVerifyScreen> createState() => _OtpVerifyScreenState();
}

class _OtpVerifyScreenState extends ConsumerState<OtpVerifyScreen> {
  final _codeController = TextEditingController();
  final _nameController = TextEditingController();
  final _tuitionNameController = TextEditingController();
  bool _isSubmitting = false;
  String? _errorText;

  @override
  void dispose() {
    _codeController.dispose();
    _nameController.dispose();
    _tuitionNameController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _isSubmitting = true;
      _errorText = null;
    });

    final success = await ref.read(authProvider.notifier).verifyOtp(
          phoneNumber: widget.phoneNumber,
          code: _codeController.text.trim(),
          displayName: _nameController.text.trim().isEmpty ? null : _nameController.text.trim(),
          tenantName: _tuitionNameController.text.trim().isEmpty ? null : _tuitionNameController.text.trim(),
        );

    if (!mounted) return;
    setState(() => _isSubmitting = false);

    if (!success) {
      setState(() => _errorText = ref.read(authProvider).error?.message ?? 'Incorrect code. Try again.');
    }
    // On success, GoRouter's redirect (driven by authProvider state) takes
    // over navigation to the home screen - no explicit push needed here.
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Verify code')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: ListView(
            children: [
              Text('Enter the code sent to ${widget.phoneNumber}'),
              const SizedBox(height: 16),
              TextField(
                controller: _codeController,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: InputDecoration(labelText: '6-digit code', errorText: _errorText),
              ),
              const SizedBox(height: 8),
              const Divider(),
              const Text('First time here? Fill these in too:'),
              const SizedBox(height: 8),
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(labelText: 'Your name'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _tuitionNameController,
                decoration: const InputDecoration(labelText: 'Your tuition/coaching name'),
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: _isSubmitting ? null : _submit,
                child: _isSubmitting
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Continue'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
