import 'package:flutter/material.dart';
import '../api_client.dart';
import 'location_detail.dart';

class OwnerParking extends StatefulWidget {
  final ApiClient api;
  const OwnerParking({super.key, required this.api});

  @override
  State<OwnerParking> createState() => _OwnerParkingState();
}

class _OwnerParkingState extends State<OwnerParking> {
  List<Map<String, dynamic>>? locations;
  bool loading = true;
  String? error;
  final nameCtrl = TextEditingController();
  final addrCtrl = TextEditingController();
  final cityCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    nameCtrl.dispose();
    addrCtrl.dispose();
    cityCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final data = await widget.api.get('/api/v1/owner/parking-locations');
      final raw = data;
      final list = raw is List ? raw : const [];
      if (mounted) {
        setState(() => locations = [for (final e in list) (e as Map).cast<String, dynamic>()]);
      }
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> create() async {
    if (nameCtrl.text.trim().isEmpty || addrCtrl.text.trim().isEmpty || cityCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Name, address and city are required.')),
      );
      return;
    }
    try {
      await widget.api.post('/api/v1/owner/parking-locations', {
        'parkingName': nameCtrl.text.trim(),
        'fullAddress': addrCtrl.text.trim(),
        'city': cityCtrl.text.trim(),
        'totalCapacity': 0,
      });
      nameCtrl.clear();
      addrCtrl.clear();
      cityCtrl.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Location created.')));
      }
      load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My parking')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Parking name')),
          const SizedBox(height: 8),
          TextField(controller: addrCtrl, decoration: const InputDecoration(labelText: 'Address')),
          const SizedBox(height: 8),
          TextField(controller: cityCtrl, decoration: const InputDecoration(labelText: 'City')),
          const SizedBox(height: 8),
          FilledButton(onPressed: create, child: const Text('Add location')),
          const SizedBox(height: 16),
          if (loading)
            const Center(child: CircularProgressIndicator())
          else if (error != null)
            Center(child: Text(error!))
          else if (locations!.isEmpty)
            const Center(child: Text('No locations yet.'))
          else
            ...locations!.map(
              (l) => Card(
                child: ListTile(
                  title: Text(l['parkingName'] as String? ?? '',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text('${l['city'] ?? ''} · capacity ${l['totalCapacity'] ?? 0}'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.of(context)
                      .push(MaterialPageRoute(
                        builder: (_) => OwnerLocationDetail(api: widget.api, locationId: l['id'] as String),
                      ))
                      .then((_) => load()),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
