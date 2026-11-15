import 'package:equatable/equatable.dart';
import '../../../core/models/geo_point.dart';
import '../../../core/models/track.dart';

abstract class EditorEvent extends Equatable {
  const EditorEvent();

  @override
  List<Object?> get props => [];
}

class AddPointRequested extends EditorEvent {
  const AddPointRequested(this.point);

  final GeoPoint point;

  @override
  List<Object?> get props => [point];
}

class UndoRequested extends EditorEvent {
  const UndoRequested();
}

class RedoRequested extends EditorEvent {
  const RedoRequested();
}

class ClearTrackRequested extends EditorEvent {
  const ClearTrackRequested();
}

class LoadTrackRequested extends EditorEvent {
  const LoadTrackRequested(this.track);

  final Track track;

  @override
  List<Object?> get props => [track];
}
