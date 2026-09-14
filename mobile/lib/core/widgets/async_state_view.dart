import 'package:flutter/material.dart';

import '../api/app_exception.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

/// Wraps a Riverpod AsyncValue-shaped (data / loading / error) screen body
/// so every list/detail screen gets consistent loading, empty, and error
/// treatment instead of each screen inventing its own - this is what makes
/// "every screen has a designed empty/error state" actually enforceable
/// rather than a guideline people forget under deadline pressure.
class AsyncStateView<T> extends StatelessWidget {
  const AsyncStateView({
    super.key,
    required this.isLoading,
    required this.error,
    required this.data,
    required this.builder,
    required this.isEmpty,
    required this.emptyTitle,
    required this.emptyMessage,
    this.emptyAction,
    this.onRetry,
  });

  final bool isLoading;
  final AppException? error;
  final T? data;
  final Widget Function(BuildContext context, T data) builder;
  final bool Function(T data) isEmpty;
  final String emptyTitle;
  final String emptyMessage;
  final Widget? emptyAction;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    if (isLoading && data == null) {
      return const Center(child: CircularProgressIndicator());
    }

    if (error != null && data == null) {
      return _ErrorView(error: error!, onRetry: onRetry);
    }

    if (data != null && isEmpty(data as T)) {
      return _EmptyView(title: emptyTitle, message: emptyMessage, action: emptyAction);
    }

    if (data != null) {
      return builder(context, data as T);
    }

    return const SizedBox.shrink();
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, this.onRetry});

  final AppException error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_rounded, size: 40, color: AppColors.inkMuted),
            const SizedBox(height: AppSpace.md),
            Text(
              error.code == 'NETWORK_ERROR' ? "Can't reach the server" : "Couldn't load this",
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpace.xs),
            Text(
              error.message,
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: AppSpace.md),
              OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
            ],
          ],
        ),
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView({required this.title, required this.message, this.action});

  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpace.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: AppSpace.xs),
            Text(message, style: Theme.of(context).textTheme.bodySmall, textAlign: TextAlign.center),
            if (action != null) ...[
              const SizedBox(height: AppSpace.md),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}
