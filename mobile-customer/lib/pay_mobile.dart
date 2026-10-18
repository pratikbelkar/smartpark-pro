import 'dart:async';
import 'package:razorpay_flutter/razorpay_flutter.dart';
import 'pay_result.dart';

/// Native Razorpay Checkout (Android / iOS).
Future<PayResult> openRazorpay({
  required String key,
  required int amount,
  required String currency,
  required String orderId,
  required String description,
}) {
  final completer = Completer<PayResult>();
  final razorpay = Razorpay();
  razorpay.on(Razorpay.EVENT_PAYMENT_SUCCESS, (PaymentSuccessResponse response) {
    if (!completer.isCompleted) {
      completer.complete(PayResult(
        orderId: response.orderId ?? orderId,
        paymentId: response.paymentId ?? '',
        signature: response.signature ?? '',
      ));
    }
    razorpay.clear();
  });
  razorpay.on(Razorpay.EVENT_PAYMENT_ERROR, (PaymentFailureResponse response) {
    if (!completer.isCompleted) {
      completer.completeError(Exception(
        'Payment failed (${response.message ?? 'unknown error'}). Your hold stays active until it expires.',
      ));
    }
    razorpay.clear();
  });
  razorpay.on(Razorpay.EVENT_EXTERNAL_WALLET, (_) {});
  razorpay.open({
    'key': key,
    'amount': amount,
    'currency': currency,
    'order_id': orderId,
    'name': 'SmartPark Pro',
    'description': description,
    'theme.color': '#10B981',
  });
  return completer.future;
}
