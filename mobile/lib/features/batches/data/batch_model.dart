class Batch {
  Batch({required this.id, required this.name, this.subject, this.scheduleSummary});

  factory Batch.fromJson(Map<String, dynamic> json) => Batch(
        id: json['id'] as String,
        name: json['name'] as String,
        subject: json['subject'] as String?,
        scheduleSummary: json['scheduleSummary'] as String?,
      );

  final String id;
  final String name;
  final String? subject;
  final String? scheduleSummary;
}
