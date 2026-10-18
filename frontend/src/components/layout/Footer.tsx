export default function Footer() {
  return (
    <footer className="mt-16 border-t border-slate-200 bg-white">
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 text-sm md:grid-cols-3">
        <div>
          <p className="font-extrabold text-navy-950">SmartPark Pro</p>
          <p className="mt-2 text-slate-500">Find Parking. Book Your Slot. Scan &amp; Park.</p>
        </div>
        <div>
          <p className="font-semibold text-navy-950">How booking works</p>
          <ol className="mt-2 list-decimal space-y-1 pl-5 text-slate-500">
            <li>Search and pick a slot</li>
            <li>Pay securely with Razorpay</li>
            <li>Scan your QR at the gate</li>
          </ol>
        </div>
        <div>
          <p className="font-semibold text-navy-950">Support</p>
          <p className="mt-2 text-slate-500">Refunds follow the posted cancellation policy. Contact support from your profile page.</p>
        </div>
      </div>
    </footer>
  );
}
