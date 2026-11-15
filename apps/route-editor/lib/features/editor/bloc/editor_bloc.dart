import 'package:bloc/bloc.dart';
import '../../../core/geo/haversine_calculator.dart';
import '../../../core/models/geo_point.dart';
import '../../../core/models/track.dart';
import 'editor_event.dart';
import 'editor_state.dart';

class EditorBloc extends Bloc<EditorEvent, EditorState> {
  EditorBloc() : super(const EditorState()) {
    on<AddPointRequested>(_onAddPointRequested);
    on<UndoRequested>(_onUndoRequested);
    on<RedoRequested>(_onRedoRequested);
    on<ClearTrackRequested>(_onClearTrackRequested);
    on<LoadTrackRequested>(_onLoadTrackRequested);
  }

  final List<List<GeoPoint>> _undoHistory = [];
  final List<List<GeoPoint>> _redoHistory = [];

  void _onAddPointRequested(AddPointRequested event, Emitter<EditorState> emit) {
    final currentPoints = List<GeoPoint>.from(
      state.activeTrack?.segments.firstOrNull?.points ?? [],
    );

    _undoHistory.add(List<GeoPoint>.from(currentPoints));
    _redoHistory.clear();

    currentPoints.add(event.point);
    _emitUpdatedPoints(currentPoints, emit);
  }

  void _onUndoRequested(UndoRequested event, Emitter<EditorState> emit) {
    if (_undoHistory.isEmpty) return;

    final currentPoints = List<GeoPoint>.from(
      state.activeTrack?.segments.firstOrNull?.points ?? [],
    );
    _redoHistory.add(currentPoints);

    final previousPoints = _undoHistory.removeLast();
    _emitUpdatedPoints(previousPoints, emit);
  }

  void _onRedoRequested(RedoRequested event, Emitter<EditorState> emit) {
    if (_redoHistory.isEmpty) return;

    final currentPoints = List<GeoPoint>.from(
      state.activeTrack?.segments.firstOrNull?.points ?? [],
    );
    _undoHistory.add(currentPoints);

    final nextPoints = _redoHistory.removeLast();
    _emitUpdatedPoints(nextPoints, emit);
  }

  void _onClearTrackRequested(ClearTrackRequested event, Emitter<EditorState> emit) {
    _undoHistory.clear();
    _redoHistory.clear();
    emit(const EditorState());
  }

  void _onLoadTrackRequested(LoadTrackRequested event, Emitter<EditorState> emit) {
    _undoHistory.clear();
    _redoHistory.clear();
    emit(state.copyWith(activeTrack: event.track, canUndo: false, canRedo: false));
  }

  void _emitUpdatedPoints(List<GeoPoint> points, Emitter<EditorState> emit) {
    final dist = HaversineCalculator.totalDistanceMeters(points);
    final updatedTrack = Track(
      id: state.activeTrack?.id ?? 'temp-track',
      name: state.activeTrack?.name ?? 'Nueva Ruta',
      segments: [TrackSegment(points: points)],
      statistics: TrackStatistics(totalDistanceMeters: dist),
    );

    emit(
      state.copyWith(
        activeTrack: updatedTrack,
        canUndo: _undoHistory.isNotEmpty,
        canRedo: _redoHistory.isNotEmpty,
      ),
    );
  }
}
