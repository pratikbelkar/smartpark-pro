import 'package:flutter/material.dart';
import '../api_client.dart';

class OwnerHome extends StatefulWidget {
  final ApiClient api;
  const OwnerHome({super.key, required this.api});

  @override
  State<OwnerHome> createState() => _OwnerHomeState();
}

class _OwnerHomeState extends State<OwnerHome> {
  Map<String, dynamic>? stats;
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
      final data = await widget.api.get('/api/v1/owner/dashboard');
      if (mounted) setState(() => stats = (data as Map).cast<String, dynamic>());
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
      appBar: AppBar(title: const Text('Dashboard')),
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
                : GridView.count(
                    padding: const EdgeInsets.all(16),
                    crossAxisCount: 2,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 1.4,
                    children: [
                      _stat('Locations', '${stats!['locations'] ?? 0}'),
                      _stat('Slots', '${stats!['slotsAvailable'] ?? 0}/${stats!['slotsTotal'] ?? 0}'),
                      _stat('Today', '${stats!['bookingsToday'] ?? 0} bookings'),
                      _stat('Parked now', '${stats!['activeSessions'] ?? 0}'),
                      _stat('Upcoming', '${stats!['upcomingBookings'] ?? 0}'),
                      _stat('Balance', rupees((stats!['balancePaise'] as num?) ?? 0)),
                      _stat('30-day revenue', rupees((stats!['revenue30dPaise'] as num?) ?? 0)),
                    ],
                  ),
      ),
    );
  }

  Widget _stat(String label, String value) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(label, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            const SizedBox(height: 4),
            Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
          ],
        ),
      ),
    );
  }
}
