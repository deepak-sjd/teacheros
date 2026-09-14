import '../../../core/api/api_client.dart';
import 'batch_model.dart';

class BatchApi {
  BatchApi(this._client);

  final ApiClient _client;

  Future<List<Batch>> list() async {
    try {
      final response = await _client.raw.get('/batches', queryParameters: {'size': 100});
      final content = (response.data['content'] as List).cast<Map<String, dynamic>>();
      return content.map(Batch.fromJson).toList();
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }

  Future<Batch> create({required String name, String? subject, String? scheduleSummary}) async {
    try {
      final response = await _client.raw.post('/batches', data: {
        'name': name,
        if (subject != null && subject.isNotEmpty) 'subject': subject,
        if (scheduleSummary != null && scheduleSummary.isNotEmpty) 'scheduleSummary': scheduleSummary,
      });
      return Batch.fromJson(response.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.toAppException(e);
    }
  }
}
