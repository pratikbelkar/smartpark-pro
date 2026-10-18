// Platform Razorpay dispatch: native plugin on mobile, Checkout.js on web.
export 'pay_mobile.dart' if (dart.library.html) 'pay_web.dart';
