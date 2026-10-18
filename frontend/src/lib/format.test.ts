import { describe, expect, it } from "vitest";
import { rupees, formatDateTime, STATUS_STYLES } from "./format";

describe("rupees", () => {
  it("formats paise as INR", () => {
    expect(rupees(15000)).toBe("₹150");
    expect(rupees(1000)).toBe("₹10");
    expect(rupees(0)).toBe("₹0");
  });
});

describe("formatDateTime", () => {
  it("handles null", () => {
    expect(formatDateTime(null)).toBe("—");
  });

  it("formats an ISO timestamp", () => {
    expect(formatDateTime("2026-10-15T17:00:00")).toContain("Oct");
  });
});

describe("STATUS_STYLES", () => {
  it("covers every booking lifecycle state", () => {
    for (const status of [
      "CONFIRMED",
      "CHECKED_IN",
      "COMPLETED",
      "PENDING_PAYMENT",
      "CANCELLED",
      "EXPIRED",
      "NO_SHOW",
    ]) {
      expect(STATUS_STYLES[status]).toBeTruthy();
    }
  });

  it("covers every slot grid state", () => {
    for (const status of ["AVAILABLE", "RESERVED", "OCCUPIED", "MAINTENANCE"]) {
      expect(STATUS_STYLES[status]).toBeTruthy();
    }
  });
});
