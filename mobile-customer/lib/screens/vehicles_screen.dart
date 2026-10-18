import 'package:flutter/material.dart';
import '../api_client.dart';
import '../models.dart';

class VehiclesScreen extends StatefulWidget {
  final ApiClient api;
  const VehiclesScreen({super.key, required this.api});

  @override
  State<VehiclesScreen> createState() => _VehiclesScreenState();
}

class _VehiclesScreenState extends State<VehiclesScreen> {
  List<Vehicle>? vehicles;
  bool loading = true;
  String? error;
  final numberCtrl = TextEditingController();
  String type = 'CAR';

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    numberCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final data = await widget.api.get('/api/v1/vehicles');
      final raw = data;
      final list = raw is List ? raw : const [];
      vehicles = list.map((e) => Vehicle.fromJson(e)).toList();
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> save() async {
    if (numberCtrl.text.trim().isEmpty) return;
    try {
      await widget.api.post('/api/v1/vehicles', {
        'vehicleNumber': numberCtrl.text.trim(),
        'vehicleType': type,
        'defaultVehicle': (vehicles?.isEmpty ?? true),
      });
      numberCtrl.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Vehicle saved.')));
      }
      load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> remove(String id) async {
    try {
      await widget.api.delete('/api/v1/vehicles/$id');
      load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Vehicles')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: numberCtrl,
                  textCapitalization: TextCapitalization.characters,
                  decoration: const InputDecoration(labelText: 'Number, e.g. MH12AB1234'),
                  onSubmitted: (_) => save(),
                ),
              ),
              const SizedBox(width: 8),
              DropdownButton<String>(
                value: type,
                items: const [
                  DropdownMenuItem(value: 'CAR', child: Text('Car')),
                  DropdownMenuItem(value: 'TWO_WHEELER', child: Text('2W')),
                ],
                onChanged: (v) => setState(() => type = v ?? 'CAR'),
              ),
              IconButton(onPressed: save, icon: const Icon(Icons.add_circle), tooltip: 'Save'),
            ],
          ),
          const SizedBox(height: 12),
          if (loading)
            const Center(child: CircularProgressIndicator())
          else if (error != null)
            Center(child: Text(error!))
          else if (vehicles!.isEmpty)
            const Center(child: Text('No vehicles yet.'))
          else
            ...vehicles!.map(
              (v) => Card(
                child: ListTile(
                  leading: CircleAvatar(child: Text(v.vehicleNumber.characters.first)),
                  title: Text(v.vehicleNumber, style: const TextStyle(fontFamily: 'monospace')),
                  subtitle: Text(v.vehicleType == 'CAR' ? 'Car' : 'Two-wheeler'),
                  trailing: IconButton(
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () => remove(v.id),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
