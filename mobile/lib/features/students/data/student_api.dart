import '../../../core/api/api_client.dart';
import 'student_model.dart';

class StudentApi {
  StudentApi(this._client);

  final ApiClient _client;

  Future<List<Student>> listForBatch(String batchId) async {
    try {
      final response =
          await _client.raw.get('/students', queryParameters: {'batchId': batchId, 'size': 200});
      final content = (response.data['content'] as List).cast<Map<String, dynamic>>();
      return content.map(Student.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// All students across every batch for this tenant - used where a
  /// screen needs to pick "any student" rather than one batch's roster
  /// (e.g. creating a fee due).
  Future<List<Student>> listAll() async {
    try {
      final response = await _client.raw.get('/students', queryParameters: {'size': 200});
      final content = (response.data['content'] as List).cast<Map<String, dynamic>>();
      return content.map(Student.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<Student> getById(String studentId) async {
    try {
      final response = await _client.raw.get('/students/$studentId');
      return Student.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// STUDENT self-service - the calling user's own linked student record,
  /// resolved server-side from their login phone number. See
  /// StudentAccessGuard on the backend for why this is safe to expose.
  Future<Student> getMe() async {
    try {
      final response = await _client.raw.get('/students/me');
      return Student.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  /// PARENT self-service - every student linked to the calling user via
  /// matching guardian phone, siblings included.
  Future<List<Student>> getMyChildren() async {
    try {
      final response = await _client.raw.get('/students/me/children');
      final content = (response.data as List).cast<Map<String, dynamic>>();
      return content.map(Student.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<Student> create({
    required String fullName,
    String? grade,
    required String guardianPhone,
    String? batchId,
  }) async {
    try {
      final response = await _client.raw.post('/students', data: {
        'fullName': fullName,
        if (grade != null && grade.isNotEmpty) 'grade': grade,
        'guardianPhone': guardianPhone,
        if (batchId != null && batchId.isNotEmpty) 'batchId': batchId,
      });
      return Student.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }
}
