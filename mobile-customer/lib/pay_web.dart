import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';
import 'package:web/web.dart' as web;
import 'pay_result.dart';

/// Razorpay Checkout.js for Flutter Web (the plugin is Android/iOS only).
Future<PayResult> openRazorpay({
  required String key,
  required int amount,
  required String currency,
  required String orderId,
  required String description,
}) async {
  await _ensureCheckoutJs();
  final completer = Completer<PayResult>();

  void fail(Object error) {
    if (!completer.isCompleted) completer.completeError(error);
  }

  final options = {
    'key': key,
    'amount': amount,
    'currency': currency,
    'order_id': orderId,
    'name': 'SmartPark Pro',
    'description': description,
    'theme': {'color': '#10B981'},
    'handler': ((JSAny? response) {
      try {
        final obj = response as JSObject;
        String str(String name) => (obj.getProperty(name.toJS) as JSString?)?.toDart ?? '';
        completer.complete(PayResult(
          orderId: str('razorpay_order_id').isEmpty ? orderId : str('razorpay_order_id'),
          paymentId: str('razorpay_payment_id'),
          signature: str('razorpay_signature'),
        ));
      } catch (e) {
        fail(Exception('Payment reply was unreadable: $e'));
      }
    }).toJS,
    'modal': {
      'ondismiss': (() {
        fail(PayCancelled());
      }).toJS,
    },
  }.jsify() as JSObject;

  _Razorpay(options).open();
  return completer.future;
}

@JS('Razorpay')
extension type _Razorpay(JSObject _) implements JSObject {
  external void open();
}

Future<void> _ensureCheckoutJs() async {
  if (web.document.querySelector('script[data-rzp]') != null) return;
  final completer = Completer<void>();
  final script = web.document.createElement('script') as web.HTMLScriptElement;
  script.dataset['rzp'] = '1';
  script.src = 'https://checkout.razorpay.com/v1/checkout.js';
  script.async = true;
  script.onload = (() {
    if (!completer.isCompleted) completer.complete();
  }).toJS;
  script.onerror = ((JSAny? _, JSAny? _) {
    if (!completer.isCompleted) {
      completer.completeError(Exception('Could not load the payment page. Check your connection.'));
    }
  }).toJS;
  web.document.head!.append(script);
  return completer.future.timeout(
    const Duration(seconds: 15),
    onTimeout: () => throw Exception('Could not load the payment page. Check your connection.'),
  );
}
