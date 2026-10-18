import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import '../api_client.dart';
import '../models.dart';
import '../places.dart';
import 'booking_screen.dart';

const slotColors = {
  'AVAILABLE': Colors.green,
  'RESERVED': Colors.amber,
  'OCCUPIED': Colors.red,
  'MAINTENANCE': Colors.grey,
};

class DetailScreen extends StatefulWidget {
  final ApiClient api;
  final LocationCard location;
  final String vehicleType;
  final DateTime start;
  final DateTime end;

  const DetailScreen({
    super.key,
    required this.api,
    required this.location,
    required this.vehicleType,
    required this.start,
    required this.end,
  });

  @override
  State<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends State<DetailScreen> {
  Availability? availability;
  bool loading = true;
  String? error;
  String? pickedSlotId;
  String? pickedSlotNumber;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      availability = await widget.api.availability(
        locationId: widget.location.id,
        start: widget.start,
        end: widget.end,
        vehicleType: widget.vehicleType,
      );
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.location.parkingName)),
      bottomNavigationBar: availability == null
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: FilledButton(
                  onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => BookingScreen(
                      api: widget.api,
                      location: widget.location,
                      vehicleType: widget.vehicleType,
                      start: widget.start,
                      end: widget.end,
                      slotId: pickedSlotId,
                      slotNumber: pickedSlotNumber,
                    ),
                  )),
                  child: Text(pickedSlotNumber == null
                      ? 'Continue with automatic allocation'
                      : 'Continue with slot $pickedSlotNumber'),
                ),
              ),
            ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(error!, textAlign: TextAlign.center),
                      TextButton(onPressed: load, child: const Text('Try again')),
                    ],
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: SizedBox(
                        height: 170,
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            Image.network(
                              imageFor(widget.location.id),
                              fit: BoxFit.cover,
                              errorBuilder: (_, _, _) => Container(color: const Color(0xFF0B1220)),
                            ),
                            Positioned(
                              left: 12,
                              bottom: 12,
                              right: 12,
                              child: Text(
                                widget.location.parkingName,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 22,
                                  fontWeight: FontWeight.w800,
                                  shadows: [Shadow(blurRadius: 8, color: Colors.black)],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      widget.location.fullAddress,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.black54),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${availability!.freeSlots} free · ${widget.vehicleType == 'CAR' ? 'Car' : 'Two-wheeler'}',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    const Wrap(
                      spacing: 12,
                      children: [
                        _Legend(color: Colors.green, label: 'Available'),
                        _Legend(color: Colors.amber, label: 'Reserved'),
                        _Legend(color: Colors.red, label: 'Occupied'),
                        _Legend(color: Colors.grey, label: 'Maintenance'),
                      ],
                    ),
                    const SizedBox(height: 12),
                    for (final floor in availability!.floors) ...[
                      Text('Floor ${floor.floorName}',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const SizedBox(height: 6),
                      for (final zone in floor.zones) ...[
                        Text('Zone ${zone.zoneName}',
                            style: Theme.of(context).textTheme.bodySmall),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            for (final slot in zone.slots)
                              ChoiceChip(
                                label: Text(slot.slotNumber),
                                selected: pickedSlotId == slot.slotId,
                                selectedColor: const Color(0xFF0B1220),
                                labelStyle: TextStyle(
                                  color: pickedSlotId == slot.slotId
                                      ? Colors.white
                                      : ((slotColors[slot.state] ?? Colors.grey).computeLuminance() > 0.5
                                          ? Colors.black87
                                          : Colors.white),
                                  fontWeight: FontWeight.bold,
                                ),
                                backgroundColor: (slotColors[slot.state] ?? Colors.grey).withValues(alpha: 0.25),
                                onSelected: slot.state != 'AVAILABLE'
                                    ? null
                                    : (_) => setState(() {
                                          if (pickedSlotId == slot.slotId) {
                                            pickedSlotId = null;
                                            pickedSlotNumber = null;
                                          } else {
                                            pickedSlotId = slot.slotId;
                                            pickedSlotNumber = slot.slotNumber;
                                          }
                                        }),
                              ),
                          ],
                        ),
                        const SizedBox(height: 12),
                      ],
                    ],
                    if (widget.location.latitude != null) ...[
                      const SizedBox(height: 8),
                      Text('Getting there', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      SizedBox(
                        height: 200,
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(16),
                          child: FlutterMap(
                            options: MapOptions(
                              initialCenter: LatLng(
                                widget.location.latitude!,
                                widget.location.longitude!,
                              ),
                              initialZoom: 15,
                            ),
                            children: [
                              TileLayer(
                                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                                userAgentPackageName: 'com.smartpark.smartpark_customer',
                              ),
                              MarkerLayer(markers: [
                                Marker(
                                  point: LatLng(
                                    widget.location.latitude!,
                                    widget.location.longitude!,
                                  ),
                                  width: 40,
                                  height: 40,
                                  child: const Icon(Icons.location_pin,
                                      color: Color(0xFF10B981), size: 38),
                                ),
                              ]),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
    );
  }
}

class _Legend extends StatelessWidget {
  final Color color;
  final String label;
  const _Legend({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 12, height: 12, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 4),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}
