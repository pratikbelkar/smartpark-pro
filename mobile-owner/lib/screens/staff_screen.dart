import 'package:flutter/material.dart';
import '../api_client.dart';

class StaffScreen extends StatefulWidget {
  final ApiClient api;
  const StaffScreen({super.key, required this.api});

  @override
  State<StaffScreen> createState() => _StaffScreenState();
}

class _StaffScreenState extends State<StaffScreen> {
  List<dynamic>? staff;
  List<dynamic>? shifts;
  final emailCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    emailCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final s = await widget.api.get('/api/v1/owner/staff');
      final sh = await widget.api.get('/api/v1/owner/staff/shifts');
      if (mounted) {
        setState(() {
          staff = (s as List?) ?? [];
          shifts = (sh as List?) ?? [];
        });
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  Future<void> grant() async {
    if (emailCtrl.text.trim().isEmpty) return;
    try {
      await widget.api.post('/api/v1/owner/staff', {'email': emailCtrl.text.trim()});
      emailCtrl.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Staff access granted.')));
      }
      load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Staff & shifts')),
      body: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: emailCtrl,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(labelText: 'Staff email'),
                    onSubmitted: (_) => grant(),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(onPressed: grant, child: const Text('Grant')),
              ],
            ),
            const SizedBox(height: 8),
            const Text('They must register first; shifts are rostered from the web console.',
                style: TextStyle(color: Colors.black54, fontSize: 12)),
            const SizedBox(height: 16),
            const Text('Staff', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            if (staff == null)
              const Center(child: CircularProgressIndicator())
            else if (staff!.isEmpty)
              const Text('No staff yet.'),
            ...?staff?.map(
              (s) => Card(
                child: ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.badge)),
                  title: Text('${s['fullName']}', style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text('${s['email']}'),
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Shifts', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            ...?shifts?.map(
              (s) => Card(
                child: ListTile(
                  title: Text('${s['staffName']} · ${s['locationName']}',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text('${s['startsAt']} → ${s['endsAt']}'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
