import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/auth_provider.dart';

class PhoneEntryScreen extends ConsumerStatefulWidget {
  const PhoneEntryScreen({super.key});

  @override
  ConsumerState<PhoneEntryScreen> createState() => _PhoneEntryScreenState();
}

class _PhoneEntryScreenState extends ConsumerState<PhoneEntryScreen> {
  final _phoneController = TextEditingController();
  bool _isSubmitting = false;
  String? _errorText;

  @override
  void dispose() {
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(() => _errorText = 'Enter your phone number');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorText = null;
    });

    final success = await ref.read(authProvider.notifier).requestOtp(phone);

    if (!mounted) return;
    setState(() => _isSubmitting = false);

    if (success) {
      context.push('/otp-verify', extra: phone);
    } else {
      setState(() => _errorText = ref.read(authProvider).error?.message ?? 'Unable to send code. Try again.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('TeacherOS', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              const Text('Enter your phone number to get started.'),
              const SizedBox(height: 24),
              TextField(
                controller: _phoneController,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(
                  labelText: 'Phone number',
                  border: const OutlineInputBorder(),
                  errorText: _errorText,
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _isSubmitting ? null : _submit,
                child: _isSubmitting
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Send code'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
