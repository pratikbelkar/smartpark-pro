import 'package:flutter/material.dart';
import '../api_client.dart';

class EarningsScreen extends StatefulWidget {
  final ApiClient api;
  const EarningsScreen({super.key, required this.api});

  @override
  State<EarningsScreen> createState() => _EarningsScreenState();
}

class _EarningsScreenState extends State<EarningsScreen> {
  Map<String, dynamic>? data;
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
      final res = await widget.api.get('/api/v1/owner/earnings');
      if (mounted) setState(() => data = (res as Map).cast<String, dynamic>());
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
      appBar: AppBar(title: const Text('Earnings')),
      body: RefreshIndicator(
        onRefresh: load,
        child: loading
            ? const Center(child: CircularProgressIndicator())
            : error != null
                ? Center(child: Text(error!))
                : ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Card(
                        color: const Color(0xFF0B1220),
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('Payable balance',
                                  style: TextStyle(color: Colors.white70, fontSize: 12)),
                              Text(rupees(data!['balancePaise'] ?? 0),
                                  style: const TextStyle(
                                      color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800)),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      const Text('By location', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const SizedBox(height: 8),
                      ...((data!['byLocation'] as List?) ?? []).map(
                        (r) => Card(
                          child: ListTile(
                            title: Text('${r['locationName']}', style: const TextStyle(fontWeight: FontWeight.bold)),
                            subtitle: Text(
                                'Gross ${rupees(r['grossPaise'] ?? 0)} · commission ${rupees(r['commissionPaise'] ?? 0)}'),
                            trailing: Text(rupees(r['netPaise'] ?? 0),
                                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      const Text('Recent ledger', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const SizedBox(height: 8),
                      ...((data!['recentEntries'] as List?) ?? []).map(
                        (e) => ListTile(
                          dense: true,
                          title: Text('${e['entryType']}', style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                          subtitle: Text('${e['bookingReference'] ?? '—'}'),
                          trailing: Text(
                            rupees(e['amountPaise'] ?? 0),
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: (e['amountPaise'] as num? ?? 0) < 0 ? Colors.red : Colors.green,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
      ),
    );
  }
}
