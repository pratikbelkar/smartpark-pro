#!/usr/bin/env python3
"""Full SmartPark API sweep: exercises every endpoint, reports PASS/FAIL."""
import json
import subprocess
import sys
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
results = []


def api(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            payload = r.read().decode()
            return r.status, (payload if raw else (json.loads(payload) if payload else None))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "null")
        except Exception:
            return e.code, None
    except Exception as e:
        return 0, {"_conn": str(e)}


def check(name, cond, extra=""):
    results.append((name, bool(cond), extra))
    print(("PASS " if cond else "FAIL ") + name + (f" [{extra}]" if extra and not cond else ""))


def login(email, password):
    s, b = api("POST", "/api/v1/auth/login", body={"email": email, "password": password})
    assert s == 200, f"login {email}: {s} {b}"
    return b["data"]["tokens"]["accessToken"]


def psql(sql):
    return subprocess.run(
        ["psql", "-U", "pratikbelkar", "-d", "smartpark", "-tAc", sql],
        capture_output=True, text=True).stdout.strip()


def main():
    import time
    suffix = str(int(time.time()))[-6:]
    W1S, W1E, W1X = "2026-10-11T18:17", "2026-10-11T20:17", "2026-10-11T21:17"
    # ---------- auth ----------
    s, b = api("POST", "/api/v1/auth/register", body={
        "fullName": "Sweep User", "email": "sweep"+suffix+"@test.local", "password": "Secret123!"})
    check("register", s == 200, f"{s}")
    tok = b["data"]["tokens"]["accessToken"] if s == 200 else None
    s, _ = api("POST", "/api/v1/auth/register", body={
        "fullName": "Sweep User", "email": "sweep"+suffix+"@test.local", "password": "Secret123!"})
    check("register duplicate 409", s == 409, f"{s}")
    s, _ = api("POST", "/api/v1/auth/login", body={"email": "sweep"+suffix+"@test.local", "password": "Wrong1!!"})
    check("login wrong 401", s == 401, f"{s}")
    s, b = api("POST", "/api/v1/auth/login", body={"email": "sweep"+suffix+"@test.local", "password": "Secret123!"})
    check("login", s == 200, f"{s}")
    refresh = b["data"]["tokens"]["refreshToken"]
    s, _ = api("GET", "/api/v1/auth/me", token=tok)
    check("me", s == 200, f"{s}")
    s, _ = api("POST", "/api/v1/auth/refresh", body={"refreshToken": refresh})
    check("refresh", s == 200, f"{s}")
    s, _ = api("POST", "/api/v1/auth/refresh", body={"refreshToken": refresh})
    check("refresh replay 401", s == 401, f"{s}")

    admin = login("admin@smartpark.local", "ChangeMe123!")
    owner1 = login("owner1@dev.local", "Password123!")
    staff1 = login("staff1@dev.local", "Password123!")
    rahul = login("rahul@dev.local", "Password123!")

    # ---------- public ----------
    s, b = api("GET", "/api/v1/parking-locations?size=20")
    check("search no-city", s == 200 and b["data"]["totalElements"] >= 3, f"{s}")
    loc = b["data"]["content"][0]["id"]
    s, b = api("GET", "/api/v1/parking-locations?city=Pune&vehicleType=CAR"
               "&start=2026-10-12T17:00&end=2026-10-12T20:00")
    check("search window", s == 200 and b["data"]["content"][0]["freeSlots"] is not None, f"{s}")
    s, b = api("GET", f"/api/v1/parking-locations/{loc}")
    check("location detail", s == 200, f"{s}")
    s, b = api("GET", f"/api/v1/parking-locations/{loc}/availability"
               f"?start={W1S}&end={W1E}&vehicleType=CAR")
    free = [sl["slotId"] for fl in b["data"]["floors"] for zn in fl["zones"]
            for sl in zn["slots"] if sl["state"] == "AVAILABLE"] if s == 200 else []
    check("availability", s == 200 and len(free) >= 2, f"{s} free={len(free)}")
    slot = free[0]
    slot2id = free[1]
    s, _ = api("GET", "/api/v1/parking-locations/00000000-0000-0000-0000-000000000000")
    check("location 404", s == 404, f"{s}")

    # ---------- vehicles ----------
    s, b = api("POST", "/api/v1/vehicles", token=tok, body={
        "vehicleNumber": "MH99ZZ0001", "vehicleType": "CAR"})
    check("vehicle create", s == 200, f"{s}")
    vid = b["data"]["id"] if s == 200 else None
    s, _ = api("GET", "/api/v1/vehicles", token=tok)
    check("vehicle list", s == 200, f"{s}")
    if vid:
        s, _ = api("PUT", f"/api/v1/vehicles/{vid}", token=tok, body={
            "vehicleNumber": "MH99ZZ0001", "vehicleType": "CAR", "vehicleModel": "X"})
        check("vehicle update", s == 200, f"{s}")

    # ---------- quote + hold ----------
    q = {"locationId": loc, "slotId": slot, "vehicleType": "CAR",
         "start": W1S, "end": W1E}
    s, b = api("POST", "/api/v1/bookings/quote", token=tok, body=q)
    check("quote", s == 200 and b["data"]["totalPaise"] == 11000, f"{s}")
    s, b = api("POST", "/api/v1/bookings/hold", token=tok, body=q)
    check("hold", s == 200, f"{s}")
    ref = b["data"]["bookingReference"] if s == 200 else None
    s, _ = api("POST", "/api/v1/bookings/hold", token=tok, body=q)
    check("double hold 409", s == 409, f"{s}")
    s, _ = api("GET", "/api/v1/bookings/my", token=tok)
    check("my bookings", s == 200, f"{s}")
    s, _ = api("GET", f"/api/v1/bookings/{ref}", token=tok)
    check("get booking", s == 200, f"{s}")
    s, _ = api("GET", f"/api/v1/bookings/{ref}/qr", token=tok)
    check("qr before pay 409", s == 409, f"{s}")

    # ---------- payments without keys ----------
    s, b = api("POST", "/api/v1/payments/orders", token=tok, body={"bookingReference": ref})
    check("order 503 no-keys", s == 503, f"{s}")
    s, _ = api("POST", "/api/v1/payments/verify", token=tok, body={
        "razorpayOrderId": "x", "razorpayPaymentId": "y", "razorpaySignature": "z"})
    check("verify unknown order 404", s == 404, f"{s}")
    s, _ = api("POST", "/api/v1/webhooks/razorpay", body={"id": "evt_bad", "event": "payment.captured"})
    check("webhook bad sig 401/503", s in (401, 503), f"{s}")

    # ---------- gate full flow (paid via SQL, gate via API) ----------
    psql(f"UPDATE parking_bookings SET booking_status='CONFIRMED', payment_status='CAPTURED' WHERE booking_reference='{ref}'")
    bid = psql(f"SELECT id FROM parking_bookings WHERE booking_reference='{ref}'")
    psql(f"INSERT INTO payments (booking_id, razorpay_order_id, razorpay_payment_id, payment_amount_paise, currency, payment_method, payment_status) VALUES ('{bid}','order_sweep1','pay_sweep1',16000,'INR','upi','CAPTURED')")
    s, _ = api("GET", f"/api/v1/bookings/{ref}/qr", token=tok)
    check("qr absent without gateway confirm 404", s == 404, f"{s}")
    s, b = api("POST", "/api/v1/staff/verify", token=staff1, body={"bookingReference": ref})
    check("staff verify", s == 200 and b["data"]["canCheckIn"] is True, f"{s}")
    s, b = api("POST", "/api/v1/staff/check-in", token=staff1, body={"bookingReference": ref})
    check("check-in", s == 200, f"{s}")
    s, _ = api("POST", "/api/v1/staff/check-in", token=staff1, body={"bookingReference": ref})
    check("double check-in 409", s == 409, f"{s}")
    s, _ = api("GET", "/api/v1/staff/active-sessions", token=staff1)
    check("active sessions", s == 200, f"{s}")
    s, b = api("POST", "/api/v1/staff/check-out", token=staff1, body={"bookingReference": ref})
    check("check-out", s == 200 and b["data"]["extraPaise"] == 0, f"{s}")
    # Ticket endpoint closes once the booking completes
    s, _ = api("GET", f"/api/v1/bookings/{ref}/qr", token=tok)
    check("qr after complete 409", s == 409, f"{s}")

    # ---------- review + support + notifications ----------
    s, _ = api("POST", "/api/v1/reviews", token=tok, body={
        "bookingReference": ref, "rating": 5, "comment": "Great!"})
    check("review", s == 200, f"{s}")
    s, _ = api("POST", "/api/v1/reviews", token=tok, body={"bookingReference": ref, "rating": 4})
    check("review dup 409", s == 409, f"{s}")
    s, _ = api("POST", "/api/v1/support", token=tok, body={"subject": "Hi", "body": "Test"})
    check("support ticket", s == 200, f"{s}")
    s, b = api("GET", "/api/v1/notifications", token=tok)
    check("notifications", s == 200 and b["data"]["unread"] >= 1, f"{s}")
    nid = b["data"]["items"]["content"][0]["id"]
    s, _ = api("POST", f"/api/v1/notifications/{nid}/read", token=tok)
    check("notif read", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/customer/dashboard", token=tok)
    check("customer dashboard", s == 200, f"{s}")

    # ---------- cancel + extend ----------
    q2 = dict(q, slotId=slot2id)
    s, b = api("POST", "/api/v1/bookings/hold", token=tok, body=q2)
    ref2 = b["data"]["bookingReference"] if s == 200 else None
    check("hold2", s == 200, f"{s}")
    s, b = api("POST", f"/api/v1/bookings/{ref2}/extend", token=tok, body={"newEnd": W1X})
    check("extend pending 409", s == 409, f"{s}")
    s, b = api("POST", f"/api/v1/bookings/{ref2}/cancel", token=tok, body={"reason": "sweep"})
    check("cancel unpaid", s == 200 and b["data"]["bookingStatus"] == "CANCELLED", f"{s}")

    # ---------- owner ----------
    s, b = api("POST", "/api/v1/owner/onboard", token=tok, body={"businessName": "Sweep Co"})
    check("onboard", s == 200, f"{s}")
    oid = b["data"]["id"] if s == 200 else None
    s, _ = api("PUT", f"/api/v1/admin/owners/{oid}/status", token=admin, body={"status": "APPROVED"})
    check("approve", s == 200, f"{s}")
    tok2 = login("sweep"+suffix+"@test.local", "Secret123!")
    s, b = api("POST", "/api/v1/owner/parking-locations", token=tok2, body={
        "parkingName": "Sweep Lot", "fullAddress": "1 Sweep St", "city": "Mumbai",
        "totalCapacity": 5, "latitude": 19.07, "longitude": 72.87})
    check("owner create location", s == 200, f"{s}")
    lid = b["data"]["id"] if s == 200 else None
    s, b = api("POST", f"/api/v1/owner/parking-locations/{lid}/floors", token=tok2, body={
        "floorName": "G", "floorNumber": 0})
    check("owner floor", s == 200, f"{s}")
    fid = b["data"]["id"] if s == 200 else None
    s, b = api("POST", "/api/v1/owner/zones", token=tok2, body={"floorId": fid, "zoneName": "A"})
    check("owner zone", s == 200, f"{s}")
    zid = b["data"]["id"] if s == 200 else None
    s, b = api("POST", f"/api/v1/owner/parking-locations/{lid}/slots/bulk", token=tok2, body={
        "floorId": fid, "zoneId": zid, "vehicleType": "CAR", "prefix": "S", "startNumber": 1, "count": 3})
    check("owner bulk", s == 200 and len(b["data"]) == 3, f"{s}")
    sid = b["data"][0]["id"] if s == 200 else None
    s, _ = api("POST", f"/api/v1/owner/slots/{sid}/status", token=tok2, body={
        "status": "MAINTENANCE", "reason": "sweep"})
    check("slot maintenance", s == 200, f"{s}")
    s, _ = api("POST", f"/api/v1/owner/parking-locations/{lid}/pricing", token=tok2, body={
        "vehicleType": "CAR", "pricingType": "HOURLY", "hourlyRateRupees": 60, "gracePeriodMinutes": 10})
    check("pricing", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/owner/dashboard", token=tok2)
    check("owner dashboard", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/owner/earnings", token=tok2)
    check("owner earnings", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/owner/bookings/export", token=tok2, raw=True)
    check("owner csv", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/owner/reviews", token=tok2)
    check("owner reviews", s == 200, f"{s}")
    s, _ = api("GET", "/api/v1/owner/staff", token=tok2)
    check("owner staff", s == 200, f"{s}")

    # ---------- admin ----------
    csv_paths = []
    for path, name in [
        ("/api/v1/admin/owners", "admin owners"),
        ("/api/v1/admin/parking-locations", "admin locations"),
        ("/api/v1/admin/bookings", "admin bookings"),
        ("/api/v1/admin/users?search=sweep", "admin users"),
        ("/api/v1/admin/payments", "admin payments"),
        ("/api/v1/admin/refunds", "admin refunds"),
        ("/api/v1/admin/dashboard", "admin dashboard"),
        ("/api/v1/admin/revenue", "admin revenue"),
        ("/api/v1/admin/reviews", "admin reviews"),
        ("/api/v1/admin/audit-logs", "admin audit"),
        ("/api/v1/admin/settings/fees", "admin fees"),
        ("/api/v1/admin/settlements", "admin settlements"),
        ("/api/v1/admin/support", "admin support"),
        ("/api/v1/admin/subscription-plans", "admin plans"),
        ("__CSV__/api/v1/admin/reports/bookings.csv", "admin csv"),
    ]:
        raw = path.startswith("__CSV__")
        if raw:
            path = path[len("__CSV__"):]
        s, _ = api("GET", path, token=admin, raw=raw)
        check(name, s == 200, f"{s}")
    s, _ = api("POST", "/api/v1/admin/refunds/process", token=admin)
    check("refunds process", s == 200, f"{s}")
    s, _ = api("PUT", f"/api/v1/admin/parking-locations/{lid}/status", token=admin,
               body={"status": "CLOSED"})
    check("admin location status", s == 200, f"{s}")

    # ---------- guards ----------
    s, _ = api("GET", "/api/v1/admin/dashboard", token=tok)
    check("customer blocked from admin 403", s == 403, f"{s}")
    s, _ = api("POST", "/api/v1/staff/verify", token=tok, body={"bookingReference": ref})
    check("customer blocked from gate 403", s == 403, f"{s}")
    s, _ = api("GET", "/api/v1/bookings/my")
    check("anon blocked 401", s == 401, f"{s}")

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    return 1 if failed else 0


sys.exit(main())
