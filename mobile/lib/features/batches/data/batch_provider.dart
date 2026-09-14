import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/app_exception.dart';
import '../../../core/providers.dart';
import '../../../core/state/list_state.dart';
import 'batch_api.dart';
import 'batch_model.dart';

class BatchNotifier extends StateNotifier<ListState<Batch>> {
  BatchNotifier(this._api) : super(ListState.initial()) {
    load();
  }

  final BatchApi _api;

  Future<void> load() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final batches = await _api.list();
      state = state.copyWith(items: batches, isLoading: false);
    } on AppException catch (e) {
      state = state.copyWith(isLoading: false, error: e);
    }
  }

  /// Returns true on success so the calling screen knows whether to close
  /// its create form.
  Future<bool> create({required String name, String? subject, String? scheduleSummary}) async {
    try {
      final created = await _api.create(name: name, subject: subject, scheduleSummary: scheduleSummary);
      state = state.copyWith(items: [...?state.items, created]);
      return true;
    } on AppException catch (e) {
      state = state.copyWith(error: e);
      return false;
    }
  }
}

final batchApiProvider = Provider<BatchApi>((ref) => BatchApi(ref.watch(apiClientProvider)));

final batchProvider = StateNotifierProvider<BatchNotifier, ListState<Batch>>((ref) {
  return BatchNotifier(ref.watch(batchApiProvider));
});
