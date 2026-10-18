import 'package:flutter/material.dart';
import 'auth_state.dart';
import 'config.dart';
import 'api_client.dart';
import 'screens/auth_screen.dart';
import 'screens/scanner_screen.dart';

const navy = Color(0xFF0B1220);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthState();
  await ServerConfig.instance.load();
  await auth.init();
  runApp(GateApp(auth: auth));
}

class GateApp extends StatelessWidget {
  final AuthState auth;
  const GateApp({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: auth,
      builder: (context, _) => MaterialApp(
        title: 'SmartPark Gate',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF22D3EE)),
          scaffoldBackgroundColor: const Color(0xFFF8FAFC),
          appBarTheme: const AppBarTheme(backgroundColor: navy, foregroundColor: Colors.white),
          inputDecorationTheme: InputDecorationTheme(
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(48),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
        home: auth.loading
            ? const Scaffold(body: Center(child: CircularProgressIndicator()))
            : auth.user == null
                ? AuthScreen(auth: auth)
                : GateShell(auth: auth),
      ),
    );
  }
}

class GateShell extends StatefulWidget {
  final AuthState auth;
  const GateShell({super.key, required this.auth});

  @override
  State<GateShell> createState() => _GateShellState();
}

class ActiveSession {
  final String bookingReference;
  final String slotNumber;
  final String entryTime;

  ActiveSession({required this.bookingReference, required this.slotNumber, required this.entryTime});

  factory ActiveSession.fromJson(Map<String, dynamic> j) => ActiveSession(
        bookingReference: j['bookingReference'] as String,
        slotNumber: j['slotNumber'] as String,
        entryTime: '${j['entryTime'] ?? ''}',
      );
}

class _GateShellState extends State<GateShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final api = ApiClient(tokenProvider: widget.auth.accessToken);
    final roles = widget.auth.user?.roles ?? const [];
    if (!roles.contains('PARKING_STAFF')) {
      return Scaffold(
        appBar: AppBar(title: const Text('SmartPark Gate')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Staff access required',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                const Text(
                  'This gate app needs a staff account. Ask your parking owner to grant access, then sign in again.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                OutlinedButton(onPressed: () => widget.auth.logout(), child: const Text('Sign out')),
              ],
            ),
          ),
        ),
      );
    }
    return Scaffold(
      body: IndexedStack(
        index: index,
        children: [
          ScannerScreen(api: api),
          SessionsScreen(api: api),
          GateProfile(auth: widget.auth),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => setState(() => index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.qr_code_scanner), label: 'Scan'),
          NavigationDestination(icon: Icon(Icons.local_parking), label: 'Parked'),
          NavigationDestination(icon: Icon(Icons.person), label: 'Profile'),
        ],
      ),
    );
  }
}

class SessionsScreen extends StatefulWidget {
  final ApiClient api;
  const SessionsScreen({super.key, required this.api});

  @override
  State<SessionsScreen> createState() => _SessionsScreenState();
}

class _SessionsScreenState extends State<SessionsScreen> {
  List<ActiveSession>? sessions;
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
      final data = await widget.api.get('/api/v1/staff/active-sessions');
      final raw = data;
      final list = raw is List ? raw : const [];
      sessions = [for (final e in list) ActiveSession.fromJson((e as Map).cast<String, dynamic>())];
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Parked now')),
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
                : sessions!.isEmpty
                    ? const Center(child: Text('No active sessions.'))
                    : ListView.builder(
                        padding: const EdgeInsets.all(12),
                        itemCount: sessions!.length,
                        itemBuilder: (context, i) {
                          final s = sessions![i];
                          return Card(
                            child: ListTile(
                              leading: const CircleAvatar(child: Icon(Icons.directions_car)),
                              title: Text(s.slotNumber,
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontFamily: 'monospace')),
                              subtitle: Text('${s.bookingReference}\nsince ${s.entryTime}'),
                              isThreeLine: true,
                            ),
                          );
                        },
                      ),
      ),
    );
  }
}

Future<void> editServer(BuildContext context) async {
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
}

class GateProfile extends StatelessWidget {
  final AuthState auth;
  const GateProfile({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    final user = auth.user;
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
                  const SizedBox(height: 4),
                  const Text('Gate staff — your shifts define which locations you can scan for.',
                      style: TextStyle(color: Colors.black54)),
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
                      onTap: () => editServer(context),
                    ),
                  ),
                  OutlinedButton(onPressed: () => auth.logout(), child: const Text('Sign out')),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
