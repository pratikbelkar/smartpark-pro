import 'package:flutter/material.dart';
import 'auth_state.dart';
import 'api_client.dart';
import 'config.dart';
import 'screens/auth_screen.dart';
import 'screens/search_screen.dart';
import 'screens/bookings_screen.dart';
import 'screens/vehicles_screen.dart';
import 'screens/profile_screen.dart';
import 'screens/scanner_screen.dart';

const navy = Color(0xFF0B1220);
const emerald = Color(0xFF10B981);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthState();
  await ServerConfig.instance.load();
  await auth.init();
  runApp(SmartParkApp(auth: auth));
}

class SmartParkApp extends StatelessWidget {
  final AuthState auth;
  const SmartParkApp({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: auth,
      builder: (context, _) => MaterialApp(
        title: 'SmartPark Pro',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: emerald, primary: emerald),
          scaffoldBackgroundColor: const Color(0xFFF8FAFC),
          appBarTheme: const AppBarTheme(
            backgroundColor: navy,
            foregroundColor: Colors.white,
            centerTitle: false,
          ),
          inputDecorationTheme: InputDecorationTheme(
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
              backgroundColor: emerald,
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(48),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
        home: auth.loading
            ? const Scaffold(body: Center(child: CircularProgressIndicator()))
            : auth.user == null
                ? AuthScreen(auth: auth)
                : HomeShell(auth: auth),
      ),
    );
  }
}

class HomeShell extends StatefulWidget {
  final AuthState auth;
  const HomeShell({super.key, required this.auth});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final api = ApiClient(tokenProvider: widget.auth.accessToken);
    final roles = widget.auth.user?.roles ?? const [];
    final isStaff = roles.contains('PARKING_STAFF');
    final pages = isStaff
        ? [
            ScannerScreen(api: api),
            ProfileScreen(auth: widget.auth, api: api),
          ]
        : [
            SearchScreen(api: api),
            BookingsScreen(api: api),
            VehiclesScreen(api: api),
            ProfileScreen(auth: widget.auth, api: api),
          ];
    return Scaffold(
      body: IndexedStack(index: index.clamp(0, pages.length - 1), children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index.clamp(0, pages.length - 1),
        onDestinationSelected: (i) => setState(() => index = i),
        destinations: isStaff
            ? const [
                NavigationDestination(icon: Icon(Icons.qr_code_scanner), label: 'Gate'),
                NavigationDestination(icon: Icon(Icons.person), label: 'Profile'),
              ]
            : const [
                NavigationDestination(icon: Icon(Icons.search), label: 'Find'),
                NavigationDestination(icon: Icon(Icons.confirmation_number), label: 'Bookings'),
                NavigationDestination(icon: Icon(Icons.directions_car), label: 'Vehicles'),
                NavigationDestination(icon: Icon(Icons.person), label: 'Profile'),
              ],
      ),
    );
  }
}
