import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../api_client.dart';
import '../models.dart';

class TicketScreen extends StatefulWidget {
  final ApiClient api;
  final Booking booking;
  const TicketScreen({super.key, required this.api, required this.booking});

  @override
  State<TicketScreen> createState() => _TicketScreenState();
}

class _TicketScreenState extends State<TicketScreen> {
  QrTicket? ticket;
  bool loading = true;
  String? error;
  bool cancelling = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    if (widget.booking.bookingStatus != 'CONFIRMED' &&
        widget.booking.bookingStatus != 'CHECKED_IN') {
      setState(() => loading = false);
      return;
    }
    try {
      final data = await widget.api.get('/api/v1/bookings/${widget.booking.bookingReference}/qr');
      if (mounted) setState(() => ticket = QrTicket.fromJson(data));
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> cancel() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancel booking?'),
        content: const Text('Refunds follow the posted cancellation policy.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Cancel booking')),
        ],
      ),
    );
    if (confirm != true) return;
    setState(() => cancelling = true);
    try {
      final data = await widget.api
          .post('/api/v1/bookings/${widget.booking.bookingReference}/cancel', {});
      final refund = (data['refundPaise'] as num?)?.toInt() ?? 0;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(refund > 0 ? 'Cancelled. Refund of ${rupees(refund)} initiated.' : 'Booking cancelled.'),
        ));
        Navigator.of(context).pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => cancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.booking;
    return Scaffold(
      appBar: AppBar(title: Text(b.slotNumber)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(b.locationName, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 4),
                  Text('Slot ${b.slotNumber} · ${b.bookingReference}'),
                  Text('Entry: ${b.start}'),
                  Text('Exit: ${b.end}'),
                  Text('Total: ${rupees(b.totalPaise)}'),
                  Text('Status: ${b.bookingStatus.replaceAll('_', ' ')} · Payment: ${b.paymentStatus}'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (loading)
            const Center(child: CircularProgressIndicator())
          else if (ticket != null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: [
                    const Text('SMARTPARK PRO · BOOKING CONFIRMED',
                        style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 12),
                    QrImageView(data: ticket!.token, version: QrVersions.auto, size: 220),
                    const SizedBox(height: 12),
                    Text('Show this QR at the gate. Slot ${ticket!.slotNumber}.'),
                  ],
                ),
              ),
            )
          else if (error != null)
            Text(error!, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          if (b.bookingStatus == 'CONFIRMED' || b.bookingStatus == 'PENDING_PAYMENT')
            OutlinedButton(
              onPressed: cancelling ? null : cancel,
              child: Text(cancelling ? 'Cancelling…' : 'Cancel booking'),
            ),
        ],
      ),
    );
  }
}
