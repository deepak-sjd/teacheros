import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/app_exception.dart';
import '../../../core/providers.dart';
import '../../../core/state/list_state.dart';
import 'fee_api.dart';
import 'fee_model.dart';

class FeeNotifier extends StateNotifier<ListState<Fee>> {
  FeeNotifier(this._api) : super(ListState.initial()) {
    load();
  }

  final FeeApi _api;

  Future<void> load() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final fees = await _api.listPending();
      state = state.copyWith(items: fees, isLoading: false);
    } on AppException catch (e) {
      state = state.copyWith(isLoading: false, error: e);
    }
  }

  Future<bool> createDue({required String studentId, required double amountDue, required DateTime dueDate}) async {
    try {
      final created = await _api.createDue(studentId: studentId, amountDue: amountDue, dueDate: dueDate);
      state = state.copyWith(items: [...?state.items, created]);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(error: e);
      return false;
    }
  }

  Future<bool> recordPayment({required String feeId, required double amountPaid, String? method}) async {
    try {
      final updated = await _api.recordPayment(feeId: feeId, amountPaid: amountPaid, method: method);
      final items = [...?state.items];
      final index = items.indexWhere((f) => f.id == feeId);
      if (index != -1) {
        if (updated.status == FeeStatus.paid) {
          items.removeAt(index); // fully paid - drops out of the pending list
        } else {
          items[index] = updated;
        }
      }
      state = state.copyWith(items: items);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(error: e);
      return false;
    }
  }
}

final feeApiProvider = Provider<FeeApi>((ref) => FeeApi(ref.watch(apiClientProvider)));

final feeProvider = StateNotifierProvider<FeeNotifier, ListState<Fee>>((ref) {
  return FeeNotifier(ref.watch(feeApiProvider));
});

/// Used by the parent/student self-service views - one student's full fee
/// history, not just pending ones (unlike feeProvider above, which is
/// staff's tenant-wide pending queue).
final feesForStudentProvider = FutureProvider.family<List<Fee>, String>((ref, studentId) async {
  return ref.watch(feeApiProvider).listForStudent(studentId);
});
