import 'package:flutter/material.dart';
import '../api_client.dart';
import '../models.dart';
import 'ticket_screen.dart';

class BookingsScreen extends StatefulWidget {
  final ApiClient api;
  const BookingsScreen({super.key, required this.api});

  @override
  State<BookingsScreen> createState() => _BookingsScreenState();
}

class _BookingsScreenState extends State<BookingsScreen> {
  List<Booking>? bookings;
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
      final data = await widget.api.get('/api/v1/bookings/my', {'size': '20'});
      final content = (data is Map ? data['content'] : data) as List;
      bookings = content.map((e) => Booking.fromJson(e)).toList();
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Color statusColor(String status) {
    return switch (status) {
      'CONFIRMED' => Colors.green,
      'CHECKED_IN' => Colors.cyan,
      'COMPLETED' => Colors.grey,
      'PENDING_PAYMENT' => Colors.amber,
      _ => Colors.red,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My bookings')),
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
                    ? const Center(child: Text('No bookings yet. Find parking to reserve your first slot.'))
                    : ListView.builder(
                        padding: const EdgeInsets.all(12),
                        itemCount: bookings!.length,
                        itemBuilder: (context, i) {
                          final b = bookings![i];
                          return Card(
                            child: ListTile(
                              leading: CircleAvatar(
                                backgroundColor: const Color(0xFF0B1220),
                                child: Text(b.slotNumber.characters.first,
                                    style: const TextStyle(color: Colors.white)),
                              ),
                              title: Text('${b.locationName} · ${b.slotNumber}'),
                              subtitle: Text('${b.bookingReference} · ${rupees(b.totalPaise)}'),
                              trailing: Chip(
                                label: Text(
                                  b.bookingStatus.replaceAll('_', ' '),
                                  style: const TextStyle(fontSize: 11),
                                ),
                                backgroundColor: statusColor(b.bookingStatus).withValues(alpha: 0.2),
                              ),
                              onTap: () => Navigator.of(context)
                                  .push(MaterialPageRoute(
                                    builder: (_) => TicketScreen(api: widget.api, booking: b),
                                  ))
                                  .then((_) => load()),
                            ),
                          );
                        },
                      ),
      ),
    );
  }
}
