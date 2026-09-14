import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/auth/user_role.dart';
import '../auth/providers/auth_provider.dart';
import '../parent/presentation/my_children_screen.dart';
import '../student_self/presentation/my_learning_screen.dart';
import 'home_shell.dart';

/// The single entry point after login. Which screen a user lands on is
/// entirely a function of their role - staff get the operational app
/// (HomeShell), everyone else gets a read-only self-service view. Adding a
/// role in the future means adding one branch here, not touching the
/// router or auth flow.
class RoleHome extends ConsumerWidget {
  const RoleHome({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(authProvider).role;

    if (role == null) {
      // Shouldn't normally happen (the router only reaches here once
      // authenticated, and authentication always yields a decodable role) -
      // but fail safe rather than crash if a future role isn't in
      // UserRole yet, or the token is malformed in some unexpected way.
      return const _UnrecognizedRoleView();
    }

    if (role == UserRole.parent) {
      return const MyChildrenScreen();
    }
    if (role == UserRole.student) {
      return const MyLearningScreen();
    }
    // OWNER, TEACHER, STAFF, ADMIN
    return const HomeShell();
  }
}

class _UnrecognizedRoleView extends StatelessWidget {
  const _UnrecognizedRoleView();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            "This account's role isn't supported by this version of the app yet.",
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ),
    );
  }
}
