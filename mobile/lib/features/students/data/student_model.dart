class Student {
  Student({
    required this.id,
    required this.fullName,
    this.grade,
    required this.guardianPhone,
    this.phoneNumber,
    this.batchId,
  });

  factory Student.fromJson(Map<String, dynamic> json) => Student(
        id: json['id'] as String,
        fullName: json['fullName'] as String,
        grade: json['grade'] as String?,
        guardianPhone: json['guardianPhone'] as String,
        phoneNumber: json['phoneNumber'] as String?,
        batchId: json['batchId'] as String?,
      );

  final String id;
  final String fullName;
  final String? grade;
  final String guardianPhone;

  /// The student's own phone, if the teacher set one - lets this student
  /// log in and see their own data directly, separately from the guardian.
  final String? phoneNumber;

  final String? batchId;
}
