import 'package:flutter/material.dart';
import '../api_client.dart';
import '../auth_state.dart';
import '../config.dart';
import 'staff_screen.dart';

class MoreScreen extends StatefulWidget {
  final AuthState auth;
  final ApiClient api;
  const MoreScreen({super.key, required this.auth, required this.api});

  @override
  State<MoreScreen> createState() => _MoreScreenState();
}

class _MoreScreenState extends State<MoreScreen> {
  List<dynamic>? reviews;
  final businessCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    businessCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final data = await widget.api.get('/api/v1/owner/reviews', {'size': '20'});
      final content = (data is Map ? data['content'] : data) as List;
      if (mounted) setState(() => reviews = content);
    } catch (_) {
      // Reviews are optional reading.
    }
  }

  Future<void> saveProfile() async {
    if (businessCtrl.text.trim().isEmpty) return;
    try {
      await widget.api.put('/api/v1/owner/me', {'businessName': businessCtrl.text.trim()});
      businessCtrl.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Profile updated.')));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  Future<void> _editServer(BuildContext context) async {
    final ctrl = TextEditingController(
      text: ServerConfig.instance.isCustom ? ServerConfig.instance.baseUrl : '',
    );
    final result = await showDialog<String?>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Server address'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: ctrl,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'API base URL',
                hintText: 'http://10.0.2.2:8080',
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Emulator: http://10.0.2.2:8080\nReal phone: http://<mac-ip>:8080\nEmpty = automatic.',
              style: TextStyle(fontSize: 12, color: Colors.black54),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop('__reset__'),
            child: const Text('Auto'),
          ),
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(ctrl.text), child: const Text('Save')),
        ],
      ),
    );
    ctrl.dispose();
    if (result == null) return;
    await ServerConfig.instance.setOverride(result == '__reset__' ? null : result);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Server set to ${ServerConfig.instance.baseUrl}.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.auth.user;
    return Scaffold(
      appBar: AppBar(title: const Text('More')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(user?.fullName ?? '', style: Theme.of(context).textTheme.titleLarge),
                  Text(user?.email ?? ''),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: businessCtrl,
                          decoration: const InputDecoration(labelText: 'Business name'),
                          onSubmitted: (_) => saveProfile(),
                        ),
                      ),
                      const SizedBox(width: 8),
                      FilledButton.tonal(onPressed: saveProfile, child: const Text('Save')),
                    ],
                  ),
                  const SizedBox(height: 12),
                  FilledButton.tonal(
                    onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                      builder: (_) => StaffScreen(api: widget.api),
                    )),
                    child: const Text('Staff & shifts'),
                  ),
                  const SizedBox(height: 8),
                  ListenableBuilder(
                    listenable: ServerConfig.instance,
                    builder: (context, _) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.dns_outlined),
                      title: const Text('Server'),
                      subtitle: Text(
                        ServerConfig.instance.baseUrl +
                            (ServerConfig.instance.isCustom ? '' : '  (auto)'),
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                      ),
                      trailing: const Icon(Icons.edit, size: 18),
                      onTap: () => _editServer(context),
                    ),
                  ),
                  OutlinedButton(onPressed: () => widget.auth.logout(), child: const Text('Sign out')),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Latest reviews', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (reviews == null)
            const Center(child: CircularProgressIndicator())
          else if (reviews!.isEmpty)
            const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('No reviews yet.'))),
          ...?reviews?.map(
            (r) => Card(
              child: ListTile(
                leading: CircleAvatar(child: Text('${r['rating']}★')),
                title: Text('${r['locationName']} · ${r['customerName']}'),
                subtitle: Text('${r['comment'] ?? '—'}'),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class OnboardScreen extends StatefulWidget {
  final AuthState auth;
  final ApiClient api;
  const OnboardScreen({super.key, required this.auth, required this.api});

  @override
  State<OnboardScreen> createState() => _OnboardScreenState();
}

class _OnboardScreenState extends State<OnboardScreen> {
  final businessCtrl = TextEditingController();
  bool busy = false;
  bool done = false;

  @override
  void dispose() {
    businessCtrl.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() => busy = true);
    try {
      await widget.api.post('/api/v1/owner/onboard', {'businessName': businessCtrl.text.trim()});
      if (mounted) setState(() => done = true);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Become an owner')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: done
            ? const Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('Application received', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text('Sign in again after the platform team approves your business.',
                      textAlign: TextAlign.center),
                ],
              )
            : Column(
                children: [
                  TextField(
                    controller: businessCtrl,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Business name'),
                    onChanged: (_) => setState(() {}),
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: busy || businessCtrl.text.trim().isEmpty ? null : submit,
                    child: Text(busy ? 'Sending…' : 'Apply'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton(onPressed: () => widget.auth.logout(), child: const Text('Sign out')),
                ],
              ),
      ),
    );
  }
}
