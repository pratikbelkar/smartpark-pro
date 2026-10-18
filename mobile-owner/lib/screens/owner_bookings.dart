import 'package:flutter/material.dart';
import '../api_client.dart';

class OwnerBookings extends StatefulWidget {
  final ApiClient api;
  const OwnerBookings({super.key, required this.api});

  @override
  State<OwnerBookings> createState() => _OwnerBookingsState();
}

class _OwnerBookingsState extends State<OwnerBookings> {
  List<dynamic>? bookings;
  bool loading = true;
  String? error;

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
      final data = await widget.api.get('/api/v1/owner/bookings', {'size': '50'});
      final content = (data is Map ? data['content'] : data) as List;
      if (mounted) setState(() => bookings = content);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String rupees(num paise) => '₹${(paise / 100).toStringAsFixed(0)}';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Bookings')),
      body: RefreshIndicator(
        onRefresh: load,
        child: loading
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
                : bookings!.isEmpty
                    ? const Center(child: Text('No bookings yet.'))
                    : ListView.builder(
                        padding: const EdgeInsets.all(12),
                        itemCount: bookings!.length,
                        itemBuilder: (context, i) {
                          final b = bookings![i] as Map;
                          return Card(
                            child: ListTile(
                              title: Text('${b['slotNumber']} · ${b['bookingReference']}',
                                  style: const TextStyle(fontFamily: 'monospace')),
                              subtitle: Text(
                                  '${b['start']} → ${b['end']}\n${b['vehicleNumber'] ?? '—'} · ${rupees(b['totalPaise'] ?? 0)}'),
                              trailing: Chip(
                                label: Text('${b['bookingStatus']}',
                                    style: const TextStyle(fontSize: 11)),
                              ),
                            ),
                          );
                        },
                      ),
      ),
    );
  }
}
