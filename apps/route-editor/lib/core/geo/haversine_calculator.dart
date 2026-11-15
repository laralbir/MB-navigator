import 'dart:math';
import '../models/geo_point.dart';

class HaversineCalculator {
  static const double earthRadiusMeters = 6371000.0;

  static double distanceMeters(GeoPoint p1, GeoPoint p2) {
    final lat1Rad = p1.latitude * pi / 180.0;
    final lat2Rad = p2.latitude * pi / 180.0;
    final deltaLat = (p2.latitude - p1.latitude) * pi / 180.0;
    final deltaLon = (p2.longitude - p1.longitude) * pi / 180.0;

    final a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2) * sin(deltaLon / 2);

    final c = 2 * atan2(sqrt(a), sqrt(1 - a));
    return earthRadiusMeters * c;
  }

  static double totalDistanceMeters(List<GeoPoint> points) {
    if (points.length < 2) return 0.0;
    var total = 0.0;
    for (var i = 0; i < points.length - 1; i++) {
      total += distanceMeters(points[i], points[i + 1]);
    }
    return total;
  }
}
