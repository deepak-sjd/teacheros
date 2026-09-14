import '../../../core/api/api_client.dart';

enum AttendanceStatus { present, absent, late }

extension AttendanceStatusJson on AttendanceStatus {
  String toJson() => name.toUpperCase();

  static AttendanceStatus fromJson(String value) => AttendanceStatus.values
      .firstWhere((s) => s.name.toUpperCase() == value, orElse: () => AttendanceStatus.present);
}

/// One row in a batch's attendance history - a session date plus a quick
/// present/absent/late tally. Mirrors the backend's BatchSessionSummary DTO.
class BatchSessionSummary {
  BatchSessionSummary({
    required this.classSessionId,
    required this.sessionDate,
    required this.presentCount,
    required this.absentCount,
    required this.lateCount,
    required this.totalCount,
  });

  factory BatchSessionSummary.fromJson(Map<String, dynamic> json) => BatchSessionSummary(
        classSessionId: json['classSessionId'] as String,
        sessionDate: DateTime.parse(json['sessionDate'] as String),
        presentCount: json['presentCount'] as int,
        absentCount: json['absentCount'] as int,
        lateCount: json['lateCount'] as int,
        totalCount: json['totalCount'] as int,
      );

  final String classSessionId;
  final DateTime sessionDate;
  final int presentCount;
  final int absentCount;
  final int lateCount;
  final int totalCount;
}

/// One student's status within a specific session - used when a teacher
/// taps into one session from the batch's attendance history.
class SessionAttendanceRecord {
  SessionAttendanceRecord({required this.studentId, required this.status});

  factory SessionAttendanceRecord.fromJson(Map<String, dynamic> json) => SessionAttendanceRecord(
        studentId: json['studentId'] as String,
        status: AttendanceStatusJson.fromJson(json['status'] as String),
      );

  final String studentId;
  final AttendanceStatus status;
}

class AttendanceApi {
  AttendanceApi(this._client);

  final ApiClient _client;

  Future<void> markAttendance({
    required String batchId,
    required DateTime sessionDate,
    required Map<String, AttendanceStatus> entriesByStudentId,
  }) async {
    try {
      await _client.raw.post('/attendance', data: {
        'batchId': batchId,
        'sessionDate': _formatDate(sessionDate),
        'entries': entriesByStudentId.entries
            .map((e) => {'studentId': e.key, 'status': e.value.toJson()})
            .toList(),
      });
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<double> getStudentAttendancePercentage(String studentId) async {
    try {
      final response = await _client.raw.get('/attendance/students/$studentId/summary');
      return (response.data['attendancePercentage'] as num).toDouble();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// Attendance history for a batch's detail page - newest session first.
  Future<List<BatchSessionSummary>> listSessionsForBatch(String batchId) async {
    try {
      final response = await _client.raw.get('/attendance/batches/$batchId/sessions');
      final content = (response.data as List).cast<Map<String, dynamic>>();
      return content.map(BatchSessionSummary.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// Per-student breakdown for one session - used when a teacher taps into
  /// a specific date from the batch's attendance history.
  Future<List<SessionAttendanceRecord>> getSessionAttendance(String classSessionId) async {
    try {
      final response = await _client.raw.get('/attendance/sessions/$classSessionId');
      final content = (response.data as List).cast<Map<String, dynamic>>();
      return content.map(SessionAttendanceRecord.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  String _formatDate(DateTime date) {
    final y = date.year.toString().padLeft(4, '0');
    final m = date.month.toString().padLeft(2, '0');
    final d = date.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }
}
