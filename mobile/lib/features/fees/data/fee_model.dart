enum FeeStatus { pending, partiallyPaid, paid, overdue }

FeeStatus _statusFromJson(String value) {
  switch (value) {
    case 'PENDING':
      return FeeStatus.pending;
    case 'PARTIALLY_PAID':
      return FeeStatus.partiallyPaid;
    case 'PAID':
      return FeeStatus.paid;
    case 'OVERDUE':
      return FeeStatus.overdue;
    default:
      return FeeStatus.pending;
  }
}

class Fee {
  Fee({
    required this.id,
    required this.studentId,
    required this.amountDue,
    required this.amountPaid,
    required this.dueDate,
    required this.status,
  });

  factory Fee.fromJson(Map<String, dynamic> json) => Fee(
        id: json['id'] as String,
        studentId: json['studentId'] as String,
        amountDue: (json['amountDue'] as num).toDouble(),
        amountPaid: (json['amountPaid'] as num).toDouble(),
        dueDate: DateTime.parse(json['dueDate'] as String),
        status: _statusFromJson(json['status'] as String),
      );

  final String id;
  final String studentId;
  final double amountDue;
  final double amountPaid;
  final DateTime dueDate;
  final FeeStatus status;

  double get amountRemaining => (amountDue - amountPaid).clamp(0, amountDue);
}
