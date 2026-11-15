import 'package:equatable/equatable.dart';

class GeoPoint extends Equatable {
  const GeoPoint({
    required this.latitude,
    required this.longitude,
    this.elevationMeters,
    this.timestamp,
  }) : assert(
          latitude >= -90.0 && latitude <= 90.0,
          'Latitude must be between -90 and 90',
        ),
        assert(
          longitude >= -180.0 && longitude <= 180.0,
          'Longitude must be between -180 and 180',
        );

  final double latitude;
  final double longitude;
  final double? elevationMeters;
  final DateTime? timestamp;

  @override
  List<Object?> get props => [latitude, longitude, elevationMeters, timestamp];
}
