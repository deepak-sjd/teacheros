import '../../../core/api/api_client.dart';
import 'fee_model.dart';

class FeeApi {
  FeeApi(this._client);

  final ApiClient _client;

  Future<List<Fee>> listPending() async {
    try {
      final response = await _client.raw.get('/fees/pending', queryParameters: {'size': 100});
      final content = (response.data['content'] as List).cast<Map<String, dynamic>>();
      return content.map(Fee.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// PARENT/STUDENT self-service view of one student's fee history - the
  /// backend's StudentAccessGuard restricts this to the caller's own child
  /// or themself; staff can call it for any student in their tenant.
  Future<List<Fee>> listForStudent(String studentId) async {
    try {
      final response = await _client.raw.get('/fees/students/$studentId');
      final content = (response.data as List).cast<Map<String, dynamic>>();
      return content.map(Fee.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<Fee> createDue({required String studentId, required double amountDue, required DateTime dueDate}) async {
    try {
      final response = await _client.raw.post('/fees', data: {
        'studentId': studentId,
        'amountDue': amountDue,
        'dueDate': _formatDate(dueDate),
      });
      return Fee.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<Fee> recordPayment({required String feeId, required double amountPaid, String? method}) async {
    try {
      final response = await _client.raw.post('/fees/$feeId/payments', data: {
        'amountPaid': amountPaid,
        if (method != null && method.isNotEmpty) 'method': method,
      });
      return Fee.fromJson(response.data as Map<String, dynamic>);
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
