import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../api_client.dart';

class VerifyView {
  final String bookingReference;
  final String bookingStatus;
  final String paymentStatus;
  final String customerName;
  final String? vehicleNumber;
  final String slotNumber;
  final String start;
  final String end;
  final bool canCheckIn;
  final String? checkInBlockReason;
  final bool canCheckOut;
  final String? checkOutBlockReason;

  VerifyView({
    required this.bookingReference,
    required this.bookingStatus,
    required this.paymentStatus,
    required this.customerName,
    this.vehicleNumber,
    required this.slotNumber,
    required this.start,
    required this.end,
    required this.canCheckIn,
    this.checkInBlockReason,
    required this.canCheckOut,
    this.checkOutBlockReason,
  });

  factory VerifyView.fromJson(Map<String, dynamic> j) => VerifyView(
        bookingReference: j['bookingReference'] as String,
        bookingStatus: j['bookingStatus'] as String,
        paymentStatus: j['paymentStatus'] as String,
        customerName: j['customerName'] as String,
        vehicleNumber: j['vehicleNumber'] as String?,
        slotNumber: j['slotNumber'] as String,
        start: j['start'] as String,
        end: j['end'] as String,
        canCheckIn: j['canCheckIn'] as bool? ?? false,
        checkInBlockReason: j['checkInBlockReason'] as String?,
        canCheckOut: j['canCheckOut'] as bool? ?? false,
        checkOutBlockReason: j['checkOutBlockReason'] as String?,
      );
}

class ScannerScreen extends StatefulWidget {
  final ApiClient api;
  const ScannerScreen({super.key, required this.api});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> with WidgetsBindingObserver {
  final refCtrl = TextEditingController();
  final plateCtrl = TextEditingController();
  VerifyView? view;
  bool busy = false;
  String? error;
  bool torchOn = false;
  late MobileScannerController scanner;
  bool scanPaused = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    scanner = MobileScannerController(
      detectionSpeed: DetectionSpeed.normal,
      facing: CameraFacing.back,
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    refCtrl.dispose();
    plateCtrl.dispose();
    scanner.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;
    if (state == AppLifecycleState.paused) {
      scanner.stop();
    } else if (state == AppLifecycleState.resumed && !scanPaused) {
      scanner.start();
    }
  }

  Future<void> verify(Map<String, String> body) async {
    setState(() {
      busy = true;
      error = null;
      view = null;
    });
    try {
      final data = await widget.api.post('/api/v1/staff/verify', body);
      if (mounted) setState(() => view = VerifyView.fromJson(data));
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> act(String action) async {
    if (view == null) return;
    setState(() => busy = true);
    try {
      final data = await widget.api.post(
        '/api/v1/staff/$action',
        {'bookingReference': view!.bookingReference},
      );
      if (!mounted) return;
      final extra = (data['extraPaise'] as num?)?.toInt() ?? 0;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(action == 'check-in'
            ? 'Checked in — park at ${data['slotNumber']}.'
            : extra > 0
                ? 'Checked out. Overstay due — see receipt.'
                : 'Checked out. Thank you!'),
      ));
      await verify({'bookingReference': view!.bookingReference});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void onDetect(BarcodeCapture capture) {
    if (scanPaused || busy) return;
    for (final barcode in capture.barcodes) {
      final token = barcode.rawValue;
      if (token != null && token.isNotEmpty) {
        setState(() => scanPaused = true);
        scanner.stop();
        verify({'qrToken': token});
        break;
      }
    }
  }

  void resumeScan() {
    setState(() {
      scanPaused = false;
      view = null;
      error = null;
    });
    scanner.start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gate scanner'),
        actions: [
          IconButton(
            tooltip: 'Torch',
            icon: Icon(torchOn ? Icons.flash_on : Icons.flash_off),
            onPressed: () {
              scanner.toggleTorch();
              setState(() => torchOn = !torchOn);
            },
          ),
          IconButton(
            tooltip: 'Flip camera',
            icon: const Icon(Icons.cameraswitch),
            onPressed: () => scanner.switchCamera(),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: SizedBox(
              height: 260,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  MobileScanner(controller: scanner, onDetect: onDetect),
                  IgnorePointer(
                    child: Center(
                      child: Container(
                        width: 220,
                        height: 220,
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.white70, width: 3),
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),
                  if (scanPaused)
                    Positioned.fill(
                      child: Container(
                        color: Colors.black54,
                        alignment: Alignment.center,
                        child: FilledButton.tonal(
                          onPressed: resumeScan,
                          child: const Text('Scan next vehicle'),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: refCtrl,
            textCapitalization: TextCapitalization.characters,
            decoration: const InputDecoration(labelText: 'Booking reference'),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: plateCtrl,
                  textCapitalization: TextCapitalization.characters,
                  decoration: const InputDecoration(labelText: '…or vehicle number'),
                  onSubmitted: (_) => verify(_manualBody()),
                ),
              ),
              const SizedBox(width: 8),
              FilledButton.tonal(
                onPressed: busy ? null : () => verify(_manualBody()),
                child: const Text('Verify'),
              ),
            ],
          ),
          if (busy) const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
          if (error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(error!, style: const TextStyle(color: Colors.red), textAlign: TextAlign.center),
            ),
          if (view != null) ...[
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(view!.bookingReference,
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text('${view!.customerName} · ${view!.vehicleNumber ?? '—'}'),
                    Text('Slot ${view!.slotNumber} · ${view!.bookingStatus} · ${view!.paymentStatus}'),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: FilledButton(
                            onPressed: view!.canCheckIn && !busy ? () => act('check-in') : null,
                            child: const Text('Approve entry'),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: FilledButton.tonal(
                            onPressed: view!.canCheckOut && !busy ? () => act('check-out') : null,
                            child: const Text('Record exit'),
                          ),
                        ),
                      ],
                    ),
                    if (!view!.canCheckIn && view!.checkInBlockReason != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text('Entry: ${view!.checkInBlockReason}',
                            style: const TextStyle(color: Colors.orange)),
                      ),
                    if (!view!.canCheckOut && view!.checkOutBlockReason != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text('Exit: ${view!.checkOutBlockReason}',
                            style: const TextStyle(color: Colors.black54)),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Map<String, String> _manualBody() {
    final ref = refCtrl.text.trim();
    final plate = plateCtrl.text.trim();
    return {
      if (ref.isNotEmpty) 'bookingReference': ref,
      if (ref.isEmpty && plate.isNotEmpty) 'vehicleNumber': plate,
    };
  }
}
