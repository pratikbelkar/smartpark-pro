import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';
import '../api_client.dart';
import '../config.dart' show ServerConfig;
import '../models.dart';
import '../places.dart';
import 'detail_screen.dart';

class SearchScreen extends StatefulWidget {
  final ApiClient api;
  const SearchScreen({super.key, required this.api});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final cityCtrl = TextEditingController();
  String vehicleType = 'CAR';
  late DateTime start;
  late DateTime end;

  List<LocationCard>? results;
  bool loading = false;
  String? error;
  bool showMap = false;
  Position? myPos;
  final mapController = MapController();


  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    start = DateTime(now.year, now.month, now.day, now.hour + 2);
    end = start.add(const Duration(hours: 3));
    search();
  }

  @override
  void dispose() {
    cityCtrl.dispose();
    mapController.dispose();
    super.dispose();
  }

  Future<void> locateMe() async {
    try {
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        return;
      }
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.medium),
      );
      if (mounted) {
        setState(() => myPos = pos);
        final pts = (results ?? []).where((c) => c.latitude != null).toList();
        if (showMap && pts.isNotEmpty) {
          mapController.move(LatLng(pos.latitude, pos.longitude), 12);
        }
        if (mounted) setState(() {});
      }
    } catch (_) {
      // Location is a nice-to-have; search works without it.
    }
  }

  List<LocationCard> sorted() {
    final list = List<LocationCard>.from(results ?? []);
    if (myPos != null) {
      list.sort((a, b) {
        double dist(LocationCard c) => c.latitude == null
            ? double.infinity
            : haversineKm(myPos!.latitude, myPos!.longitude, c.latitude!, c.longitude!);
        return dist(a).compareTo(dist(b));
      });
    }
    return list;
  }

  Future<void> search() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      results = await widget.api.searchLocations(
        city: cityCtrl.text.trim().isEmpty ? null : cityCtrl.text.trim(),
        vehicleType: vehicleType,
        start: start,
        end: end,
      );
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> pickTime(bool isStart) async {
    final date = await showDatePicker(
      context: context,
      initialDate: isStart ? start : end,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 30)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(isStart ? start : end),
    );
    if (time == null) return;
    setState(() {
      final picked = DateTime(date.year, date.month, date.day, time.hour, time.minute);
      if (isStart) {
        start = picked;
        if (!end.isAfter(start)) end = start.add(const Duration(hours: 2));
      } else {
        end = picked;
      }
    });
  }

  String priceLine(LocationCard c) {
    final free = c.freeSlots != null ? ' · ${c.freeSlots} free' : '';
    final rate = c.hourlyRates['CAR'] != null ? '${rupees(c.hourlyRates['CAR']!)} /hr' : 'See prices';
    return '${c.city}$free\n$rate';
  }

  String fmt(DateTime dt) =>
      '${dt.day}/${dt.month}/${dt.year}, ${dt.hour > 12 ? dt.hour - 12 : dt.hour == 0 ? 12 : dt.hour}:${dt.minute.toString().padLeft(2, '0')} ${dt.hour >= 12 ? 'PM' : 'AM'}';

  LatLng? _firstCoords() {
    for (final c in sorted()) {
      if (c.latitude != null) return LatLng(c.latitude!, c.longitude!);
    }
    return null;
  }

  void _openDetail(LocationCard c) {
    Navigator.of(context)
        .push(MaterialPageRoute(
          builder: (_) => DetailScreen(
            api: widget.api,
            location: c,
            vehicleType: vehicleType,
            start: start,
            end: end,
          ),
        ))
        .then((_) => search());
  }

  Widget _locationCard(LocationCard c) {
    final distance = myPos != null && c.latitude != null
        ? formatKm(haversineKm(myPos!.latitude, myPos!.longitude, c.latitude!, c.longitude!))
        : null;
    final rate = c.hourlyRates['CAR'] != null ? '${rupees(c.hourlyRates['CAR']!)} /hr' : 'See prices';
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _openDetail(c),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              height: 130,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.network(
                    c.imageUrl ?? imageFor(c.id),
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => Container(
                      color: const Color(0xFF0B1220),
                      alignment: Alignment.center,
                      child: Text(
                        c.parkingName.characters.first,
                        style: const TextStyle(color: Colors.white38, fontSize: 48, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                  if (c.freeSlots != null)
                    Positioned(
                      left: 8,
                      top: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black87,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text('${c.freeSlots} free',
                            style: const TextStyle(color: Color(0xFF6EE7B7), fontWeight: FontWeight.bold, fontSize: 12)),
                      ),
                    ),
                  if (distance != null)
                    Positioned(
                      right: 8,
                      bottom: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.92),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(distance,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                      ),
                    ),
                ],
              ),
            ),
            ListTile(
              title: Text(c.parkingName, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text('${c.city}\n$rate'),
              trailing: const Icon(Icons.chevron_right),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Find parking')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: cityCtrl,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'City or destination', prefixIcon: Icon(Icons.search)),
            onSubmitted: (_) => search(),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(value: 'CAR', label: Text('Car')),
                    ButtonSegment(value: 'TWO_WHEELER', label: Text('2W')),
                  ],
                  selected: {vehicleType},
                  onSelectionChanged: (s) => setState(() => vehicleType = s.first),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => pickTime(true),
                  child: Text('In: ${fmt(start)}', textAlign: TextAlign.center),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  onPressed: () => pickTime(false),
                  child: Text('Out: ${fmt(end)}', textAlign: TextAlign.center),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: FilledButton(onPressed: loading ? null : search, child: const Text('Search'))),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                onPressed: locateMe,
                tooltip: 'Near me',
                icon: Icon(myPos == null ? Icons.near_me_outlined : Icons.near_me),
              ),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                onPressed: () => setState(() => showMap = !showMap),
                tooltip: showMap ? 'List view' : 'Map view',
                icon: Icon(showMap ? Icons.list : Icons.map),
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (loading) const Center(child: CircularProgressIndicator()),
          if (error != null)
            Card(
              color: Colors.red.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Text('Something went wrong', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text(error!, textAlign: TextAlign.center),
                    Text('Target: ${ServerConfig.instance.baseUrl}',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.black45)),
                    TextButton(onPressed: search, child: const Text('Try again')),
                  ],
                ),
              ),
            ),
          if (results != null && results!.isEmpty && !loading)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text('No parking found. Try another city or time window.', textAlign: TextAlign.center),
              ),
            ),
          if (showMap && sorted().any((c) => c.latitude != null))
            SizedBox(
              height: 320,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: FlutterMap(
                  mapController: mapController,
                  options: MapOptions(
                    initialCenter: myPos != null
                        ? LatLng(myPos!.latitude, myPos!.longitude)
                        : _firstCoords() ?? const LatLng(18.5204, 73.8567),
                    initialZoom: 12,
                  ),
                  children: [
                    TileLayer(
                      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.smartpark.smartpark_customer',
                    ),
                    if (myPos != null)
                      MarkerLayer(markers: [
                        Marker(
                          point: LatLng(myPos!.latitude, myPos!.longitude),
                          width: 40,
                          height: 40,
                          child: const Icon(Icons.person_pin_circle, color: Color(0xFF0B1220), size: 36),
                        ),
                      ]),
                    MarkerLayer(
                      markers: [
                        for (final c in sorted())
                          if (c.latitude != null)
                            Marker(
                              point: LatLng(c.latitude!, c.longitude!),
                              width: 90,
                              height: 40,
                              child: GestureDetector(
                                onTap: () => _openDetail(c),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFF10B981),
                                    borderRadius: BorderRadius.circular(20),
                                    border: Border.all(color: Colors.white, width: 2),
                                  ),
                                  child: Text(
                                    c.freeSlots != null ? '${c.freeSlots}' : 'P',
                                    style: const TextStyle(
                                        color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
                                  ),
                                ),
                              ),
                            ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          if (showMap && sorted().any((c) => c.latitude != null)) const SizedBox(height: 12),
          ...sorted().map((c) => _locationCard(c)),
        ],
      ),
    );
  }
}
