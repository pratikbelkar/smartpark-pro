let loading: Promise<void> | null = null;

export function loadRazorpay(): Promise<void> {
  if (typeof (window as unknown as { Razorpay?: unknown }).Razorpay !== "undefined") {
    return Promise.resolve();
  }
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("Could not load the payment page. Check your connection."));
      document.body.appendChild(script);
    });
  }
  return loading;
}

interface RazorpayOptions {
  key: string;
  amount: number;
  currency: string;
  order_id: string;
  name: string;
  description: string;
  handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => void;
  modal?: { ondismiss?: () => void };
  theme?: { color?: string };
}

export function openCheckout(options: RazorpayOptions): void {
  const Razorpay = (window as unknown as { Razorpay: new (o: RazorpayOptions) => { open: () => void } }).Razorpay;
  new Razorpay(options).open();
}
