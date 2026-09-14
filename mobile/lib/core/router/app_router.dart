import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/attendance/presentation/mark_attendance_screen.dart';
import '../../features/auth/presentation/otp_verify_screen.dart';
import '../../features/auth/presentation/phone_entry_screen.dart';
import '../../features/auth/providers/auth_provider.dart';
import '../../features/home/role_home.dart';
import '../../features/students/presentation/students_screen.dart';
import '../../features/students/presentation/student_detail_screen.dart';

/// Centralized navigation + auth gating. Screens never need to check "am I
/// logged in" themselves - this redirect is the single source of truth,
/// so it's impossible for a screen to be reachable while unauthenticated
/// just because someone forgot a check on that one screen.
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isAuthenticated = authState.status == AuthStatus.authenticated;
      final isAuthRoute = state.matchedLocation == '/' || state.matchedLocation == '/otp-verify';

      if (authState.status == AuthStatus.unknown) {
        // Still resolving whether a session exists - don't redirect yet.
        return null;
      }
      if (!isAuthenticated && !isAuthRoute) {
        return '/';
      }
      if (isAuthenticated && isAuthRoute) {
        return '/home';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (context, state) => const PhoneEntryScreen()),
      GoRoute(
        path: '/otp-verify',
        builder: (context, state) => OtpVerifyScreen(phoneNumber: state.extra as String? ?? ''),
      ),
      GoRoute(path: '/home', builder: (context, state) => const RoleHome()),
      GoRoute(
        path: '/batches/:batchId/students',
        builder: (context, state) => StudentsScreen(
          batchId: state.pathParameters['batchId']!,
          batchName: state.extra as String? ?? 'Batch',
        ),
      ),
      GoRoute(
        path: '/batches/:batchId/attendance',
        builder: (context, state) => MarkAttendanceScreen(
          batchId: state.pathParameters['batchId']!,
          batchName: state.extra as String? ?? 'Batch',
        ),
      ),
      GoRoute(
        path: '/students/:studentId',
        builder: (context, state) => StudentDetailScreen(
          studentId: state.pathParameters['studentId']!,
          studentName: state.extra as String? ?? 'Student',
        ),
      ),
    ],
  );
});
