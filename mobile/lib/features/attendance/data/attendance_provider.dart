import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/app_exception.dart';
import '../../../core/providers.dart';
import 'attendance_api.dart';

class MarkingState {
  const MarkingState({
    required this.statuses,
    this.isSubmitting = false,
    this.isSubmitted = false,
    this.error,
  });

  final Map<String, AttendanceStatus> statuses;
  final bool isSubmitting;
  final bool isSubmitted;
  final AppException? error;

  MarkingState copyWith({
    Map<String, AttendanceStatus>? statuses,
    bool? isSubmitting,
    bool? isSubmitted,
    AppException? error,
    bool clearError = false,
  }) {
    return MarkingState(
      statuses: statuses ?? this.statuses,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      isSubmitted: isSubmitted ?? this.isSubmitted,
      error: clearError ? null : (error ?? this.error),
    );
  }
}

/// Holds the in-progress attendance marking for one batch's today session.
/// Defaults every student to PRESENT (the common case in most classes),
/// so the teacher's job is tapping the exceptions, not marking everyone -
/// matches how teachers described doing this on paper.
class AttendanceMarkingNotifier extends StateNotifier<MarkingState> {
  AttendanceMarkingNotifier(this._api, this._batchId, List<String> studentIds)
      : super(MarkingState(statuses: {for (final id in studentIds) id: AttendanceStatus.present}));

  final AttendanceApi _api;
  final String _batchId;

  void toggle(String studentId) {
    final current = state.statuses[studentId] ?? AttendanceStatus.present;
    final next = switch (current) {
      AttendanceStatus.present => AttendanceStatus.absent,
      AttendanceStatus.absent => AttendanceStatus.late,
      AttendanceStatus.late => AttendanceStatus.present,
    };
    state = state.copyWith(statuses: {...state.statuses, studentId: next});
  }

  void markAllPresent() {
    state = state
        .copyWith(statuses: {for (final id in state.statuses.keys) id: AttendanceStatus.present});
  }

  Future<bool> submit() async {
    state = state.copyWith(isSubmitting: true, clearError: true);
    try {
      await _api.markAttendance(
          batchId: _batchId, sessionDate: DateTime.now(), entriesByStudentId: state.statuses);
      state = state.copyWith(isSubmitting: false, isSubmitted: true);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(isSubmitting: false, error: e);
      return false;
    }
  }
}

final attendanceApiProvider =
    Provider<AttendanceApi>((ref) => AttendanceApi(ref.watch(apiClientProvider)));

/// Used by the parent/student self-service views (and could be reused by a
/// future teacher-side student detail screen) - a single student's derived
/// attendance percentage, cached per studentId by Riverpod.
final attendancePercentageProvider = FutureProvider.family<double, String>((ref, studentId) async {
  return ref.watch(attendanceApiProvider).getStudentAttendancePercentage(studentId);
});

/// A batch's attendance history for its detail page - newest session first.
final batchSessionsProvider =
    FutureProvider.autoDispose.family<List<BatchSessionSummary>, String>((ref, batchId) async {
  return ref.watch(attendanceApiProvider).listSessionsForBatch(batchId);
});

/// Per-student breakdown for one session, shown when a teacher taps into a
/// specific date from the batch's attendance history.
final sessionAttendanceProvider = FutureProvider.autoDispose
    .family<List<SessionAttendanceRecord>, String>((ref, classSessionId) async {
  return ref.watch(attendanceApiProvider).getSessionAttendance(classSessionId);
});

/// Family key is a single canonical String, NOT a record containing a List.
/// Dart Lists use identity equality, so a record like
/// `({String batchId, List<String> studentIds})` would compare unequal on
/// every rebuild even with identical content (a fresh .toList() call makes
/// a new list instance each time) - Riverpod would then treat every
/// rebuild as a brand-new provider instance and silently reset state back
/// to defaults. Encoding everything into one String sidesteps that trap.
class AttendanceMarkingKey {
  AttendanceMarkingKey(this.batchId, List<String> studentIds)
      : studentIds = List.unmodifiable(studentIds);

  final String batchId;
  final List<String> studentIds;

  late final String _cacheKey = '$batchId::${studentIds.join(',')}';

  @override
  bool operator ==(Object other) => other is AttendanceMarkingKey && other._cacheKey == _cacheKey;

  @override
  int get hashCode => _cacheKey.hashCode;
}

final attendanceMarkingProvider =
    StateNotifierProvider.family<AttendanceMarkingNotifier, MarkingState, AttendanceMarkingKey>(
        (ref, key) {
  return AttendanceMarkingNotifier(ref.watch(attendanceApiProvider), key.batchId, key.studentIds);
});
