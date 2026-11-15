import 'package:equatable/equatable.dart';
import '../../../core/models/geo_point.dart';
import '../../../core/models/track.dart';

class EditorState extends Equatable {
  const EditorState({
    this.activeTrack,
    this.canUndo = false,
    this.canRedo = false,
    this.selectedPointIndex,
    this.activeTool = 'auto_routing',
  });

  final Track? activeTrack;
  final bool canUndo;
  final bool canRedo;
  final int? selectedPointIndex;
  final String activeTool;

  EditorState copyWith({
    Track? activeTrack,
    bool? canUndo,
    bool? canRedo,
    int? selectedPointIndex,
    String? activeTool,
  }) {
    return EditorState(
      activeTrack: activeTrack ?? this.activeTrack,
      canUndo: canUndo ?? this.canUndo,
      canRedo: canRedo ?? this.canRedo,
      selectedPointIndex: selectedPointIndex ?? this.selectedPointIndex,
      activeTool: activeTool ?? this.activeTool,
    );
  }

  @override
  List<Object?> get props => [activeTrack, canUndo, canRedo, selectedPointIndex, activeTool];
}
