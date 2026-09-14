import '../api/app_exception.dart';

/// Generic (loading / data / error) bookkeeping for a list-shaped screen.
/// Every feature (batches, students, fees) reuses this instead of
/// reinventing the same three booleans/fields each time.
class ListState<T> {
  const ListState({this.items, this.isLoading = false, this.error});

  final List<T>? items;
  final bool isLoading;
  final AppException? error;

  static ListState<T> initial<T>() => ListState<T>(isLoading: true);

  ListState<T> copyWith({List<T>? items, bool? isLoading, AppException? error, bool clearError = false}) {
    return ListState<T>(
      items: items ?? this.items,
      isLoading: isLoading ?? this.isLoading,
      error: clearError ? null : (error ?? this.error),
    );
  }
}
