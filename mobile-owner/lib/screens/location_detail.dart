import 'package:flutter/material.dart';
import '../api_client.dart';

class OwnerLocationDetail extends StatefulWidget {
  final ApiClient api;
  final String locationId;
  const OwnerLocationDetail({super.key, required this.api, required this.locationId});

  @override
  State<OwnerLocationDetail> createState() => _OwnerLocationDetailState();
}

class _OwnerLocationDetailState extends State<OwnerLocationDetail> with SingleTickerProviderStateMixin {
  late TabController tabs;
  List<dynamic> floors = [];
  List<dynamic> slots = [];
  List<dynamic> pricing = [];
  List<dynamic> zones = [];
  String? zonesForFloor;
  bool loading = true;

  final floorCtrl = TextEditingController();
  final zoneCtrl = TextEditingController();
  final prefixCtrl = TextEditingController(text: 'C');
  final countCtrl = TextEditingController(text: '10');
  final rateCtrl = TextEditingController(text: '50');
  String bulkVehicle = 'CAR';
  String? bulkFloorId;
  String? bulkZoneId;

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 3, vsync: this);
    load();
  }

  @override
  void dispose() {
    tabs.dispose();
    floorCtrl.dispose();
    zoneCtrl.dispose();
    prefixCtrl.dispose();
    countCtrl.dispose();
    rateCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final f = await widget.api.get('/api/v1/owner/parking-locations/${widget.locationId}/floors');
      final s = await widget.api.get('/api/v1/owner/parking-locations/${widget.locationId}/slots');
      final p = await widget.api.get('/api/v1/owner/parking-locations/${widget.locationId}/pricing');
      if (mounted) {
        setState(() {
          floors = (f as List?) ?? [];
          slots = (s as List?) ?? [];
          pricing = (p as List?) ?? [];
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> loadZones(String floorId) async {
    try {
      final z = await widget.api.get('/api/v1/owner/floors/$floorId/zones');
      if (mounted) {
        setState(() {
          zones = (z as List?) ?? [];
          zonesForFloor = floorId;
        });
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  void snack(String message) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Manage location'),
        bottom: TabBar(
          controller: tabs,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          tabs: const [Tab(text: 'Structure'), Tab(text: 'Slots'), Tab(text: 'Pricing')],
        ),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: tabs,
              children: [
                _structureTab(),
                _slotsTab(),
                _pricingTab(),
              ],
            ),
    );
  }

  Widget _structureTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            Expanded(child: TextField(controller: floorCtrl, decoration: const InputDecoration(labelText: 'Floor, e.g. P1'))),
            const SizedBox(width: 8),
            FilledButton.tonal(
              onPressed: () async {
                if (floorCtrl.text.trim().isEmpty) return;
                try {
                  await widget.api.post(
                    '/api/v1/owner/parking-locations/${widget.locationId}/floors',
                    {'floorName': floorCtrl.text.trim(), 'floorNumber': floors.length + 1},
                  );
                  floorCtrl.clear();
                  load();
                } catch (e) {
                  snack(e.toString());
                }
              },
              child: const Text('Add'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        ...floors.map(
          (f) => Card(
            child: ListTile(
              title: Text((f['floorName'] ?? '') as String, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: zonesForFloor == f['id']
                  ? Text(zones.map((z) => z['zoneName']).join(', '))
                  : const Text('Tap Zones to view'),
              trailing: TextButton(
                onPressed: () => loadZones(f['id'] as String),
                child: const Text('Zones'),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(child: TextField(controller: zoneCtrl, decoration: const InputDecoration(labelText: 'Zone, e.g. A'))),
            const SizedBox(width: 8),
            FilledButton.tonal(
              onPressed: zonesForFloor == null
                  ? null
                  : () async {
                      try {
                        await widget.api.post('/api/v1/owner/zones', {
                          'floorId': zonesForFloor,
                          'zoneName': zoneCtrl.text.trim(),
                        });
                        zoneCtrl.clear();
                        loadZones(zonesForFloor!);
                      } catch (e) {
                        snack(e.toString());
                      }
                    },
              child: const Text('Add'),
            ),
          ],
        ),
        if (zonesForFloor == null)
          const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text('Pick a floor’s Zones button first.', style: TextStyle(color: Colors.black54)),
          ),
      ],
    );
  }

  Widget _slotsTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        DropdownButtonFormField<String>(
          initialValue: bulkFloorId,
          decoration: const InputDecoration(labelText: 'Floor'),
          items: [for (final f in floors) DropdownMenuItem(value: f['id'] as String, child: Text('${f['floorName']}'))],
          onChanged: (v) => setState(() {
            bulkFloorId = v;
            bulkZoneId = null;
            if (v != null) loadZones(v);
          }),
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          initialValue: bulkZoneId,
          decoration: const InputDecoration(labelText: 'Zone'),
          items: [for (final z in zones) DropdownMenuItem(value: z['id'] as String, child: Text('${z['zoneName']}'))],
          onChanged: (v) => setState(() => bulkZoneId = v),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: bulkVehicle,
                decoration: const InputDecoration(labelText: 'Type'),
                items: const [
                  DropdownMenuItem(value: 'CAR', child: Text('Car')),
                  DropdownMenuItem(value: 'TWO_WHEELER', child: Text('2W')),
                ],
                onChanged: (v) => setState(() => bulkVehicle = v ?? 'CAR'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(child: TextField(controller: prefixCtrl, decoration: const InputDecoration(labelText: 'Prefix'))),
            const SizedBox(width: 8),
            Expanded(child: TextField(controller: countCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Count'))),
          ],
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: (bulkFloorId == null || bulkZoneId == null)
              ? null
              : () async {
                  try {
                    final res = await widget.api.post(
                      '/api/v1/owner/parking-locations/${widget.locationId}/slots/bulk',
                      {
                        'floorId': bulkFloorId,
                        'zoneId': bulkZoneId,
                        'vehicleType': bulkVehicle,
                        'prefix': prefixCtrl.text.trim().isEmpty ? 'C' : prefixCtrl.text.trim(),
                        'startNumber': 1,
                        'count': int.tryParse(countCtrl.text) ?? 10,
                      },
                    );
                    snack('${(res as List).length} slots created.');
                    load();
                  } catch (e) {
                    snack(e.toString());
                  }
                },
          child: const Text('Bulk create'),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final s in slots)
              InputChip(
                label: Text(s['slotNumber'] as String),
                selected: (s['operationalStatus'] as String) == 'MAINTENANCE',
                onPressed: () async {
                  final toMaintenance = (s['operationalStatus'] as String) != 'MAINTENANCE';
                  try {
                    await widget.api.post('/api/v1/owner/slots/${s['id']}/status', {
                      'status': toMaintenance ? 'MAINTENANCE' : 'AVAILABLE',
                      'reason': toMaintenance ? 'Flagged from app' : 'Reopened from app',
                    });
                    load();
                  } catch (e) {
                    snack(e.toString());
                  }
                },
              ),
          ],
        ),
        const Padding(
          padding: EdgeInsets.only(top: 8),
          child: Text('Tap a slot to toggle maintenance.', style: TextStyle(color: Colors.black54)),
        ),
      ],
    );
  }

  Widget _pricingTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            Expanded(child: TextField(controller: rateCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: '₹/hour (car)'))),
            const SizedBox(width: 8),
            FilledButton.tonal(
              onPressed: () async {
                try {
                  await widget.api.post(
                    '/api/v1/owner/parking-locations/${widget.locationId}/pricing',
                    {
                      'vehicleType': 'CAR',
                      'pricingType': 'HOURLY',
                      'hourlyRateRupees': double.tryParse(rateCtrl.text) ?? 50,
                      'gracePeriodMinutes': 15,
                    },
                  );
                  snack('Pricing updated.');
                  load();
                } catch (e) {
                  snack(e.toString());
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        ...pricing.map(
          (p) => Card(
            child: ListTile(
              title: Text('${p['vehicleType']} · ${p['pricingType']}',
                  style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text('₹${((p['hourlyRatePaise'] as num) / 100).toStringAsFixed(0)}/hr · grace ${p['gracePeriodMinutes']}m'),
            ),
          ),
        ),
      ],
    );
  }
}
