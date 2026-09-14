import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/app_exception.dart';
import '../../../core/providers.dart';
import '../../../core/state/list_state.dart';
import 'student_api.dart';
import 'student_model.dart';

class StudentNotifier extends StateNotifier<ListState<Student>> {
  StudentNotifier(this._api, this._batchId) : super(ListState.initial()) {
    load();
  }

  final StudentApi _api;
  final String _batchId;

  Future<void> load() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final students = await _api.listForBatch(_batchId);
      state = state.copyWith(items: students, isLoading: false);
    } on AppException catch (e) {
      state = state.copyWith(isLoading: false, error: e);
    }
  }

  Future<bool> create(
      {required String fullName, String? grade, required String guardianPhone}) async {
    try {
      final created = await _api.create(
        fullName: fullName,
        grade: grade,
        guardianPhone: guardianPhone,
        batchId: _batchId,
      );
      state = state.copyWith(items: [...?state.items, created]);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(error: e);
      return false;
    }
  }
}

/// Cross-batch version of StudentNotifier - the "Students" tab shows every
/// student in the tenant regardless of batch, and lets a teacher add a
/// student without picking a batch first (batch is optional, same as the
/// backend allows). Kept as a separate notifier rather than reusing
/// StudentNotifier since that one is intentionally scoped to one batchId.
class AllStudentsNotifier extends StateNotifier<ListState<Student>> {
  AllStudentsNotifier(this._api) : super(ListState.initial()) {
    load();
  }

  final StudentApi _api;

  Future<void> load() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final students = await _api.listAll();
      state = state.copyWith(items: students, isLoading: false);
    } on AppException catch (e) {
      state = state.copyWith(isLoading: false, error: e);
    }
  }

  Future<bool> create({
    required String fullName,
    String? grade,
    required String guardianPhone,
    String? batchId,
  }) async {
    try {
      final created = await _api.create(
        fullName: fullName,
        grade: grade,
        guardianPhone: guardianPhone,
        batchId: batchId,
      );
      state = state.copyWith(items: [...?state.items, created]);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(error: e);
      return false;
    }
  }
}

final allStudentsProvider = StateNotifierProvider<AllStudentsNotifier, ListState<Student>>((ref) {
  return AllStudentsNotifier(ref.watch(studentApiProvider));
});

final studentApiProvider = Provider<StudentApi>((ref) => StudentApi(ref.watch(apiClientProvider)));

/// PARENT self-service - every child linked to the logged-in parent.
/// autoDispose + no caching-forever here since this list can change if the
/// teacher edits a guardian phone; refreshing the screen should re-fetch.
final myChildrenProvider = FutureProvider.autoDispose<List<Student>>((ref) async {
  return ref.watch(studentApiProvider).getMyChildren();
});

/// STUDENT self-service - the logged-in student's own record.
final myStudentRecordProvider = FutureProvider.autoDispose<Student>((ref) async {
  return ref.watch(studentApiProvider).getMe();
});

/// Cached by Riverpod automatically per studentId - used anywhere we only
/// have a studentId (like a Fee record) and need the student's display
/// name, without re-fetching an entire batch roster just to look one up.
final studentByIdProvider = FutureProvider.family<Student, String>((ref, studentId) async {
  return ref.watch(studentApiProvider).getById(studentId);
});

final studentsForBatchProvider =
    StateNotifierProvider.family<StudentNotifier, ListState<Student>, String>((ref, batchId) {
  return StudentNotifier(ref.watch(studentApiProvider), batchId);
});
