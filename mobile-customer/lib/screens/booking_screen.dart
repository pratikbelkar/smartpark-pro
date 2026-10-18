import 'package:flutter/material.dart';
import '../api_client.dart';
import '../models.dart';
import '../pay_result.dart';
import '../pay.dart';

class BookingScreen extends StatefulWidget {
  final ApiClient api;
  final LocationCard location;
  final String vehicleType;
  final DateTime start;
  final DateTime end;
  final String? slotId;
  final String? slotNumber;

  const BookingScreen({
    super.key,
    required this.api,
    required this.location,
    required this.vehicleType,
    required this.start,
    required this.end,
    this.slotId,
    this.slotNumber,
  });

  @override
  State<BookingScreen> createState() => _BookingScreenState();
}

class _BookingScreenState extends State<BookingScreen> {
  Quote? quote;
  String? bookingReference;
  String? expiresAt;
  bool busy = false;
  bool paying = false;
  String? error;
  bool done = false;

  @override
  void initState() {
    super.initState();
    fetchQuote();
  }

  Map<String, dynamic> _windowBody({String? slotId}) => {
        'locationId': widget.location.id,
        if (slotId case final String sid) 'slotId': sid,
        'vehicleType': widget.vehicleType,
        'start': widget.start.toIso8601String().substring(0, 16),
        'end': widget.end.toIso8601String().substring(0, 16),
      };

  Future<void> fetchQuote() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final data = await widget.api.post('/api/v1/bookings/quote', _windowBody(slotId: widget.slotId));
      setState(() => quote = Quote.fromJson(data));
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> createHold() async {
    if (quote == null) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final data = await widget.api.post('/api/v1/bookings/hold', _windowBody(slotId: quote!.slotId));
      setState(() {
        bookingReference = data['bookingReference'] as String;
        expiresAt = data['expiresAt'] as String;
      });
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> pay() async {
    if (bookingReference == null) return;
    setState(() {
      paying = true;
      error = null;
    });
    try {
      final order = await widget.api.post('/api/v1/payments/orders', {
        'bookingReference': bookingReference,
      });
      final result = await openRazorpay(
        key: order['keyId'] as String,
        amount: (order['amountPaise'] as num).toInt(),
        currency: (order['currency'] as String?) ?? 'INR',
        orderId: order['orderId'] as String,
        description: 'Parking $bookingReference',
      );
      await widget.api.post('/api/v1/payments/verify', {
        'razorpayOrderId': result.orderId,
        'razorpayPaymentId': result.paymentId,
        'razorpaySignature': result.signature,
      });
      if (mounted) setState(() => done = true);
    } on PayCancelled catch (e) {
      if (mounted) {
        setState(() => error = '$e Your hold stays active until it expires.');
      }
    } catch (e) {
      if (mounted) {
        setState(() => error = '$e Check My Bookings before retrying.');
      }
    } finally {
      if (mounted) setState(() => paying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Review & pay')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.location.parkingName, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 4),
                  Text('${widget.start.day}/${widget.start.month} '
                      '${widget.start.hour}:${widget.start.minute.toString().padLeft(2, '0')} → '
                      '${widget.end.hour}:${widget.end.minute.toString().padLeft(2, '0')}'),
                  Text('Slot: ${widget.slotNumber ?? 'automatic allocation'}'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (busy && quote == null) const Center(child: CircularProgressIndicator()),
          if (error != null)
            Card(
              color: Colors.red.shade50,
              child: Padding(padding: const EdgeInsets.all(12), child: Text(error!)),
            ),
          if (quote != null && bookingReference == null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('Slot ${quote!.slotNumber} · ${quote!.billableHours}h — ${rupees(quote!.parkingPaise)}'),
                    Text('Platform fee — ${rupees(quote!.conveniencePaise)}'),
                    const Divider(),
                    Text('Total — ${rupees(quote!.totalPaise)}',
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: busy ? null : createHold,
                      child: Text(busy ? 'Holding…' : 'Hold slot ${quote!.slotNumber}'),
                    ),
                  ],
                ),
              ),
            ),
          if (bookingReference != null && !done)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('Held until $expiresAt. Complete payment or the slot is released.',
                        style: Theme.of(context).textTheme.bodyMedium),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: paying ? null : pay,
                      child: Text(paying ? 'Opening checkout…' : 'Pay with Razorpay'),
                    ),
                  ],
                ),
              ),
            ),
          if (done)
            Card(
              color: Colors.green.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Text('Booking confirmed 🎉', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    Text('Reference: $bookingReference'),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
                      child: const Text('Back to bookings for your QR ticket'),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
