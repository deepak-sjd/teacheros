import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/widgets/async_state_view.dart';
import '../../attendance/data/attendance_api.dart';
import '../../attendance/data/attendance_provider.dart';
import '../../attendance/presentation/attendance_status_chip.dart';
import '../data/student_model.dart';
import '../data/student_provider.dart';
import 'add_student_sheet.dart';

/// A batch's detail page: its roster (with the "mark today's attendance"
/// entry point) and its attendance history, as two tabs. Both tabs share
/// the same batchId/batchName, so this stays one screen rather than two
/// separately-routed ones.
class StudentsScreen extends StatelessWidget {
  const StudentsScreen({super.key, required this.batchId, required this.batchName});

  final String batchId;
  final String batchName;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(batchName),
          bottom: const TabBar(tabs: [Tab(text: 'Roster'), Tab(text: 'History')]),
        ),
        body: TabBarView(
          children: [
            _RosterTab(batchId: batchId, batchName: batchName),
            _HistoryTab(batchId: batchId),
          ],
        ),
      ),
    );
  }
}

class _RosterTab extends ConsumerWidget {
  const _RosterTab({required this.batchId, required this.batchName});

  final String batchId;
  final String batchName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(studentsForBatchProvider(batchId));

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showAddStudentSheet(context, ref, batchId),
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('Add student'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(studentsForBatchProvider(batchId).notifier).load(),
        child: AsyncStateView<List<Student>>(
          isLoading: state.isLoading,
          error: state.error,
          data: state.items,
          onRetry: () => ref.read(studentsForBatchProvider(batchId).notifier).load(),
          isEmpty: (items) => items.isEmpty,
          emptyTitle: 'No students yet',
          emptyMessage: 'Add your first student to start tracking attendance and fees.',
          emptyAction: FilledButton(
            onPressed: () => showAddStudentSheet(context, ref, batchId),
            child: const Text('Add a student'),
          ),
          builder: (context, students) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.sm, AppSpace.md, 96),
            children: [
              _MarkAttendanceBanner(
                studentCount: students.length,
                onTap: () => context.push('/batches/$batchId/attendance', extra: batchName),
              ),
              const SizedBox(height: AppSpace.md),
              Text('Roster', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: AppSpace.sm),
              ...students.map((s) => _StudentTile(student: s)),
            ],
          ),
        ),
      ),
    );
  }
}

/// The single most important call-to-action on this screen, per the "reduce
/// cognitive load" principle - marking attendance shouldn't be buried
/// below a scrolling roster, it should be the first thing a teacher sees.
class _MarkAttendanceBanner extends StatelessWidget {
  const _MarkAttendanceBanner({required this.studentCount, required this.onTap});

  final int studentCount;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.chalkboard,
      borderRadius: BorderRadius.circular(AppRadius.lg),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.lg),
        onTap: studentCount == 0 ? null : onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.lg),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Mark today's attendance",
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(color: Colors.white),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      studentCount == 0
                          ? 'Add students first'
                          : '$studentCount student${studentCount == 1 ? '' : 's'} in this batch',
                      style: Theme.of(context)
                          .textTheme
                          .bodySmall
                          ?.copyWith(color: Colors.white.withValues(alpha: 0.8)),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.arrow_forward_rounded, color: Colors.white),
            ],
          ),
        ),
      ),
    );
  }
}

class _StudentTile extends StatelessWidget {
  const _StudentTile({required this.student});

  final Student student;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpace.sm),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: () => context.push('/students/${student.id}', extra: student.fullName),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpace.md, vertical: AppSpace.sm),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: AppColors.chalkboardTint,
                foregroundColor: AppColors.chalkboardDark,
                child: Text(student.fullName.isNotEmpty ? student.fullName[0].toUpperCase() : '?'),
              ),
              const SizedBox(width: AppSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(student.fullName, style: Theme.of(context).textTheme.titleMedium),
                    if (student.grade != null && student.grade!.isNotEmpty)
                      Text('Grade ${student.grade}', style: Theme.of(context).textTheme.bodySmall),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.inkMuted),
            ],
          ),
        ),
      ),
    );
  }
}

class _HistoryTab extends ConsumerWidget {
  const _HistoryTab({required this.batchId});

  final String batchId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessionsAsync = ref.watch(batchSessionsProvider(batchId));

    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(batchSessionsProvider(batchId)),
      child: sessionsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            Padding(
              padding: const EdgeInsets.all(AppSpace.lg),
              child: Column(
                children: [
                  const Icon(Icons.wifi_off_rounded, size: 40, color: AppColors.inkMuted),
                  const SizedBox(height: AppSpace.md),
                  Text("Couldn't load attendance history",
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: AppSpace.md),
                  OutlinedButton(
                    onPressed: () => ref.invalidate(batchSessionsProvider(batchId)),
                    child: const Text('Try again'),
                  ),
                ],
              ),
            ),
          ],
        ),
        data: (sessions) {
          if (sessions.isEmpty) {
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                Padding(
                  padding: const EdgeInsets.all(AppSpace.lg),
                  child: Column(
                    children: [
                      Text('No attendance marked yet',
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: AppSpace.xs),
                      Text(
                        'Once you mark a session, it shows up here.',
                        style: Theme.of(context).textTheme.bodySmall,
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              ],
            );
          }

          return ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpace.md, AppSpace.md, AppSpace.md, AppSpace.xl),
            itemCount: sessions.length,
            separatorBuilder: (_, __) => const SizedBox(height: AppSpace.sm),
            itemBuilder: (context, index) =>
                _SessionRow(session: sessions[index], batchId: batchId),
          );
        },
      ),
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session, required this.batchId});

  final BatchSessionSummary session;
  final String batchId;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          backgroundColor: Colors.transparent,
          builder: (_) => _SessionDetailSheet(session: session, batchId: batchId),
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpace.md),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  DateFormat('EEE, d MMM yyyy').format(session.sessionDate),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              _CountBadge(
                  count: session.presentCount, color: AppColors.present, bg: AppColors.presentTint),
              const SizedBox(width: 6),
              _CountBadge(
                  count: session.absentCount, color: AppColors.absent, bg: AppColors.absentTint),
              const SizedBox(width: 6),
              _CountBadge(
                  count: session.lateCount, color: AppColors.pending, bg: AppColors.pendingTint),
            ],
          ),
        ),
      ),
    );
  }
}

class _CountBadge extends StatelessWidget {
  const _CountBadge({required this.count, required this.color, required this.bg});

  final int count;
  final Color color;
  final Color bg;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: bg, shape: BoxShape.circle),
      child: Text('$count', style: Theme.of(context).textTheme.labelMedium?.copyWith(color: color)),
    );
  }
}

class _SessionDetailSheet extends ConsumerWidget {
  const _SessionDetailSheet({required this.session, required this.batchId});

  final BatchSessionSummary session;
  final String batchId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final recordsAsync = ref.watch(sessionAttendanceProvider(session.classSessionId));
    final rosterState = ref.watch(studentsForBatchProvider(batchId));
    final namesById = {for (final s in rosterState.items ?? const <Student>[]) s.id: s.fullName};

    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
        ),
        child: Column(
          children: [
            const SizedBox(height: AppSpace.sm),
            Container(
              width: 36,
              height: 4,
              decoration:
                  BoxDecoration(color: AppColors.divider, borderRadius: BorderRadius.circular(2)),
            ),
            Padding(
              padding: const EdgeInsets.all(AppSpace.md),
              child: Text(
                DateFormat('EEEE, d MMM yyyy').format(session.sessionDate),
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            Expanded(
              child: recordsAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, __) => const Center(child: Text("Couldn't load this session.")),
                data: (records) => ListView.separated(
                  controller: scrollController,
                  padding: const EdgeInsets.symmetric(horizontal: AppSpace.md),
                  itemCount: records.length,
                  separatorBuilder: (_, __) => const Divider(),
                  itemBuilder: (context, index) {
                    final record = records[index];
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: AppSpace.xs),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              namesById[record.studentId] ?? 'Student',
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                          ),
                          AttendanceStatusChip(status: record.status, onTap: () {}),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
