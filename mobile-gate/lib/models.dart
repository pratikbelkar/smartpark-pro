class User {
  final String id;
  final String fullName;
  final String email;
  final List<String> roles;

  User({required this.id, required this.fullName, required this.email, required this.roles});

  factory User.fromJson(Map<String, dynamic> j) => User(
        id: j['id'] as String,
        fullName: j['fullName'] as String,
        email: j['email'] as String,
        roles: List<String>.from(j['roles'] as List? ?? const []),
      );
}

class LocationCard {
  final String id;
  final String parkingName;
  final String city;
  final String fullAddress;
  final Map<String, int> hourlyRates;
  final int? freeSlots;
  final double? latitude;
  final double? longitude;
  final String? imageUrl;

  LocationCard({
    required this.id,
    required this.parkingName,
    required this.city,
    required this.fullAddress,
    required this.hourlyRates,
    this.freeSlots,
    this.latitude,
    this.longitude,
    this.imageUrl,
  });

  factory LocationCard.fromJson(Map<String, dynamic> j) => LocationCard(
        id: j['id'] as String,
        parkingName: j['parkingName'] as String,
        city: j['city'] as String,
        fullAddress: j['fullAddress'] as String,
        hourlyRates: (j['hourlyRates'] as Map? ?? {}).map(
          (k, v) => MapEntry(k as String, (v as num).toInt()),
        ),
        freeSlots: (j['freeSlots'] as num?)?.toInt(),
        latitude: (j['latitude'] as num?)?.toDouble(),
        longitude: (j['longitude'] as num?)?.toDouble(),
        imageUrl: j['imageUrl'] as String?,
      );
}

class SlotState {
  final String slotId;
  final String slotNumber;
  final String state;

  SlotState({required this.slotId, required this.slotNumber, required this.state});

  factory SlotState.fromJson(Map<String, dynamic> j) => SlotState(
        slotId: j['slotId'] as String,
        slotNumber: j['slotNumber'] as String,
        state: j['state'] as String,
      );
}

class ZoneAvailability {
  final String zoneId;
  final String zoneName;
  final List<SlotState> slots;

  ZoneAvailability({required this.zoneId, required this.zoneName, required this.slots});

  factory ZoneAvailability.fromJson(Map<String, dynamic> j) => ZoneAvailability(
        zoneId: j['zoneId'] as String,
        zoneName: j['zoneName'] as String,
        slots: (j['slots'] as List).map((s) => SlotState.fromJson(s)).toList(),
      );
}

class FloorAvailability {
  final String floorId;
  final String floorName;
  final List<ZoneAvailability> zones;

  FloorAvailability({required this.floorId, required this.floorName, required this.zones});

  factory FloorAvailability.fromJson(Map<String, dynamic> j) => FloorAvailability(
        floorId: j['floorId'] as String,
        floorName: j['floorName'] as String,
        zones: (j['zones'] as List).map((z) => ZoneAvailability.fromJson(z)).toList(),
      );
}

class Availability {
  final int freeSlots;
  final List<FloorAvailability> floors;

  Availability({required this.freeSlots, required this.floors});

  factory Availability.fromJson(Map<String, dynamic> j) => Availability(
        freeSlots: (j['freeSlots'] as num).toInt(),
        floors: (j['floors'] as List).map((f) => FloorAvailability.fromJson(f)).toList(),
      );
}

class Quote {
  final String slotId;
  final String slotNumber;
  final int totalPaise;
  final int parkingPaise;
  final int conveniencePaise;
  final int billableHours;

  Quote({
    required this.slotId,
    required this.slotNumber,
    required this.totalPaise,
    required this.parkingPaise,
    required this.conveniencePaise,
    required this.billableHours,
  });

  factory Quote.fromJson(Map<String, dynamic> j) => Quote(
        slotId: j['slotId'] as String,
        slotNumber: j['slotNumber'] as String,
        totalPaise: (j['totalPaise'] as num).toInt(),
        parkingPaise: (j['parkingPaise'] as num).toInt(),
        conveniencePaise: (j['conveniencePaise'] as num).toInt(),
        billableHours: (j['billableHours'] as num).toInt(),
      );
}

class Booking {
  final String bookingReference;
  final String bookingStatus;
  final String paymentStatus;
  final String locationName;
  final String slotNumber;
  final String? vehicleNumber;
  final String start;
  final String end;
  final int totalPaise;

  Booking({
    required this.bookingReference,
    required this.bookingStatus,
    required this.paymentStatus,
    required this.locationName,
    required this.slotNumber,
    this.vehicleNumber,
    required this.start,
    required this.end,
    required this.totalPaise,
  });

  factory Booking.fromJson(Map<String, dynamic> j) => Booking(
        bookingReference: j['bookingReference'] as String,
        bookingStatus: j['bookingStatus'] as String,
        paymentStatus: j['paymentStatus'] as String,
        locationName: j['locationName'] as String,
        slotNumber: j['slotNumber'] as String,
        vehicleNumber: j['vehicleNumber'] as String?,
        start: j['start'] as String,
        end: j['end'] as String,
        totalPaise: (j['totalPaise'] as num).toInt(),
      );
}

class Vehicle {
  final String id;
  final String vehicleNumber;
  final String vehicleType;

  Vehicle({required this.id, required this.vehicleNumber, required this.vehicleType});

  factory Vehicle.fromJson(Map<String, dynamic> j) => Vehicle(
        id: j['id'] as String,
        vehicleNumber: j['vehicleNumber'] as String,
        vehicleType: j['vehicleType'] as String,
      );
}

class QrTicket {
  final String bookingReference;
  final String token;
  final String slotNumber;
  final String locationName;
  final String start;
  final String end;

  QrTicket({
    required this.bookingReference,
    required this.token,
    required this.slotNumber,
    required this.locationName,
    required this.start,
    required this.end,
  });

  factory QrTicket.fromJson(Map<String, dynamic> j) => QrTicket(
        bookingReference: j['bookingReference'] as String,
        token: j['token'] as String,
        slotNumber: j['slotNumber'] as String,
        locationName: j['locationName'] as String,
        start: j['start'] as String,
        end: j['end'] as String,
      );
}

String rupees(int paise) => '₹${(paise / 100).toStringAsFixed(paise % 100 == 0 ? 0 : 2)}';
