import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:teacheros/main.dart';

void main() {
  testWidgets('App boots and shows the phone entry screen', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: TeacherOsApp()));
    await tester.pump();

    expect(find.text('TeacherOS'), findsOneWidget);
    expect(find.text('Send code'), findsOneWidget);
  });
}
