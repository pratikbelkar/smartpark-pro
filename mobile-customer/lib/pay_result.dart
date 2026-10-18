class PayResult {
  final String orderId;
  final String paymentId;
  final String signature;

  PayResult({required this.orderId, required this.paymentId, required this.signature});
}

class PayCancelled implements Exception {
  final String message;
  PayCancelled([this.message = 'Checkout closed before payment.']);

  @override
  String toString() => message;
}
