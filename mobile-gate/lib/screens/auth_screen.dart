import 'package:flutter/material.dart';
import '../auth_state.dart';
import '../config.dart';

class AuthScreen extends StatefulWidget {
  final AuthState auth;
  const AuthScreen({super.key, required this.auth});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  bool registerMode = false;
  final nameCtrl = TextEditingController();
  final emailCtrl = TextEditingController();
  final passCtrl = TextEditingController();
  bool busy = false;
  bool obscurePassword = true;
  String? error;

  @override
  void dispose() {
    nameCtrl.dispose();
    emailCtrl.dispose();
    passCtrl.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      if (registerMode) {
        await widget.auth.register(nameCtrl.text.trim(), emailCtrl.text.trim(), passCtrl.text);
      } else {
        await widget.auth.login(emailCtrl.text.trim(), passCtrl.text);
      }
    } catch (e) {
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 32),
            const Text('SmartPark Pro', style: TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
            Text(
              registerMode ? 'Create your account.' : 'Parking Made Simple.',
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: Colors.black54),
            ),
            const SizedBox(height: 28),
            if (registerMode)
              TextField(
                controller: nameCtrl,
                textCapitalization: TextCapitalization.words,
                decoration: const InputDecoration(labelText: 'Full name'),
              ),
            if (registerMode) const SizedBox(height: 12),
            TextField(
              controller: emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: 'Email'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: passCtrl,
              obscureText: obscurePassword,
              decoration: InputDecoration(
                labelText: 'Password (8+ characters)',
                suffixIcon: IconButton(
                  tooltip: obscurePassword ? 'Show password' : 'Hide password',
                  icon: Icon(obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                  onPressed: () => setState(() => obscurePassword = !obscurePassword),
                ),
              ),
              onSubmitted: (_) => submit(),
            ),
            if (error != null) ...[
              const SizedBox(height: 12),
              Text(error!, style: const TextStyle(color: Colors.red)),
            ],
            const SizedBox(height: 20),
            FilledButton(
              onPressed: busy ? null : submit,
              child: Text(busy ? 'Please wait…' : (registerMode ? 'Create account' : 'Sign in')),
            ),
            TextButton(
              onPressed: () => setState(() {
                registerMode = !registerMode;
                error = null;
              }),
              child: Text(registerMode ? 'Have an account? Sign in' : 'New here? Create an account'),
            ),
            const SizedBox(height: 16),
            Center(
              child: Text(
                'Server: ${ServerConfig.instance.baseUrl}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.black38),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
