import 'package:flutter/material.dart';
import 'auth_state.dart';
import 'config.dart';
import 'api_client.dart';
import 'screens/auth_screen.dart';
import 'screens/owner_home.dart';
import 'screens/owner_parking.dart';
import 'screens/owner_bookings.dart';
import 'screens/earnings_screen.dart';
import 'screens/more_screen.dart';

const navy = Color(0xFF0B1220);
const emerald = Color(0xFF10B981);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthState();
  await ServerConfig.instance.load();
  await auth.init();
  runApp(OwnerApp(auth: auth));
}

class OwnerApp extends StatelessWidget {
  final AuthState auth;
  const OwnerApp({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: auth,
      builder: (context, _) => MaterialApp(
        title: 'SmartPark Owner',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: emerald, primary: emerald),
          scaffoldBackgroundColor: const Color(0xFFF8FAFC),
          appBarTheme: const AppBarTheme(backgroundColor: navy, foregroundColor: Colors.white),
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
                : (auth.user!.roles.contains('PARKING_OWNER')
                    ? OwnerShell(auth: auth)
                    : OnboardScreen(auth: auth, api: ApiClient(tokenProvider: auth.accessToken))),
      ),
    );
  }
}

class OwnerShell extends StatefulWidget {
  final AuthState auth;
  const OwnerShell({super.key, required this.auth});

  @override
  State<OwnerShell> createState() => _OwnerShellState();
}

class _OwnerShellState extends State<OwnerShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final api = ApiClient(tokenProvider: widget.auth.accessToken);
    final pages = [
      OwnerHome(api: api),
      OwnerParking(api: api),
      OwnerBookings(api: api),
      EarningsScreen(api: api),
      MoreScreen(auth: widget.auth, api: api),
    ];
    return Scaffold(
      body: IndexedStack(index: index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => setState(() => index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.local_parking), label: 'Parking'),
          NavigationDestination(icon: Icon(Icons.calendar_month), label: 'Bookings'),
          NavigationDestination(icon: Icon(Icons.wallet), label: 'Earnings'),
          NavigationDestination(icon: Icon(Icons.more_horiz), label: 'More'),
        ],
      ),
    );
  }
}
