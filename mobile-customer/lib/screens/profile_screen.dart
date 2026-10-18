import 'package:flutter/material.dart';
import '../api_client.dart';
import '../auth_state.dart';
import '../config.dart';

class ProfileScreen extends StatefulWidget {
  final AuthState auth;
  final ApiClient api;
  const ProfileScreen({super.key, required this.auth, required this.api});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  List<Map<String, dynamic>>? notifications;
  bool loadingNotifs = true;

  @override
  void initState() {
    super.initState();
    loadNotifs();
  }

  Future<void> loadNotifs() async {
    try {
      final data = await widget.api.get('/api/v1/notifications', {'size': '20'});
      final items = (data is Map ? data['items'] : data);
      final content = (items is Map ? items['content'] : items) as List;
      if (mounted) {
        setState(() {
          notifications = content.cast<Map<String, dynamic>>();
          loadingNotifs = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => loadingNotifs = false);
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
        SnackBar(content: Text('Server set to ${ServerConfig.instance.baseUrl}. Pull to retry.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.auth.user;
    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
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
                  OutlinedButton(
                    onPressed: () async {
                      await widget.auth.logout();
                    },
                    child: const Text('Sign out'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Notifications', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (loadingNotifs)
            const Center(child: CircularProgressIndicator())
          else if (notifications == null || notifications!.isEmpty)
            const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('All quiet.'))),
          ...?notifications?.map(
            (n) => Card(
              color: (n['read'] as bool? ?? true) ? null : Colors.green.shade50,
              child: ListTile(
                title: Text(n['title'] as String? ?? '', style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text(n['message'] as String? ?? ''),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
