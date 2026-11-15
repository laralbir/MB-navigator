import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../features/editor/bloc/editor_bloc.dart';
import '../features/editor/view/editor_page.dart';

class RouteEditorApp extends StatelessWidget {
  const RouteEditorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MB-navigator Route Editor',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF10B981),
          surface: Color(0xFF1E293B),
        ),
      ),
      home: BlocProvider(
        create: (_) => EditorBloc(),
        child: const EditorPage(),
      ),
    );
  }
}
