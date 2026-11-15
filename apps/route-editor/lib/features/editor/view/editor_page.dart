import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../bloc/editor_bloc.dart';
import '../bloc/editor_event.dart';
import '../bloc/editor_state.dart';

class EditorPage extends StatelessWidget {
  const EditorPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(LucideIcons.navigation, color: Color(0xFF10B981)),
            SizedBox(width: 8),
            Text('MB-navigator | Route Editor', style: TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
        actions: [
          BlocBuilder<EditorBloc, EditorState>(
            builder: (context, state) {
              return Row(
                children: [
                  IconButton(
                    icon: const Icon(LucideIcons.undo),
                    tooltip: 'Deshacer (Ctrl+Z)',
                    onPressed: state.canUndo
                        ? () => context.read<EditorBloc>().add(const UndoRequested())
                        : null,
                  ),
                  IconButton(
                    icon: const Icon(LucideIcons.redo),
                    tooltip: 'Rehacer (Ctrl+Y)',
                    onPressed: state.canRedo
                        ? () => context.read<EditorBloc>().add(const RedoRequested())
                        : null,
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    onPressed: () {},
                    icon: const Icon(LucideIcons.download, size: 16),
                    label: const Text('Exportar GPX'),
                  ),
                  const SizedBox(width: 16),
                ],
              );
            },
          ),
        ],
      ),
      body: Row(
        children: [
          // Barra lateral de herramientas
          Container(
            width: 260,
            color: const Color(0xFF1E293B),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('HERRAMIENTAS', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey)),
                const SizedBox(height: 8),
                ListTile(
                  leading: const Icon(LucideIcons.route, color: Color(0xFF10B981)),
                  title: const Text('Auto-routing BRouter'),
                  selected: true,
                  onTap: () {},
                ),
                ListTile(
                  leading: const Icon(LucideIcons.penTool),
                  title: const Text('Línea recta (Manual)'),
                  onTap: () {},
                ),
                ListTile(
                  leading: const Icon(LucideIcons.scissors),
                  title: const Text('Dividir / Cortar'),
                  onTap: () {},
                ),
                const Divider(height: 32),
                const Text('ESTADÍSTICAS', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey)),
                const SizedBox(height: 8),
                BlocBuilder<EditorBloc, EditorState>(
                  builder: (context, state) {
                    final distKm = (state.activeTrack?.statistics.totalDistanceMeters ?? 0.0) / 1000.0;
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Distancia: ${distKm.toStringAsFixed(2)} km', style: const TextStyle(fontSize: 14)),
                        const SizedBox(height: 4),
                        Text('Puntos: ${state.activeTrack?.segments.firstOrNull?.points.length ?? 0}', style: const TextStyle(fontSize: 14)),
                      ],
                    );
                  },
                ),
              ],
            ),
          ),
          // Canvas central
          Expanded(
            child: Container(
              color: const Color(0xFF0F172A),
              child: const Center(
                child: Text(
                  'Lienzo de mapa (flutter_map) + Panel de elevación (fl_chart)',
                  style: TextStyle(color: Colors.grey),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
