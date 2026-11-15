import 'package:equatable/equatable.dart';
import 'geo_point.dart';

class TrackSegment extends Equatable {
  const TrackSegment({this.points = const []});

  final List<GeoPoint> points;

  @override
  List<Object?> get props => [points];
}

class TrackStatistics extends Equatable {
  const TrackStatistics({
    this.totalDistanceMeters = 0.0,
    this.elevationGainMeters = 0.0,
    this.elevationLossMeters = 0.0,
  });

  final double totalDistanceMeters;
  final double elevationGainMeters;
  final double elevationLossMeters;

  @override
  List<Object?> get props => [totalDistanceMeters, elevationGainMeters, elevationLossMeters];
}

class Track extends Equatable {
  const Track({
    required this.id,
    required this.name,
    this.description,
    this.segments = const [],
    this.statistics = const TrackStatistics(),
  });

  final String id;
  final String name;
  final String? description;
  final List<TrackSegment> segments;
  final TrackStatistics statistics;

  @override
  List<Object?> get props => [id, name, description, segments, statistics];
}
