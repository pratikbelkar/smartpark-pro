export interface ApiEnvelope<T> {
  ok: boolean;
  code: string;
  message: string;
  data: T;
  fieldErrors: Record<string, string> | null;
  timestamp: string;
  requestId: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface User {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  roles: string[];
  emailVerified: boolean;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
}

export interface LocationCard {
  id: string;
  parkingName: string;
  city: string;
  fullAddress: string;
  latitude: number | null;
  longitude: number | null;
  imageUrl: string | null;
  operationalStatus: string;
  hourlyRates: Record<string, number>;
  freeSlots: number | null;
  averageRating?: number | null;
  reviewCount?: number;
}

export interface LocationDetail extends LocationCard {
  description: string | null;
  state: string | null;
  postalCode: string | null;
  entranceLatitude: number | null;
  entranceLongitude: number | null;
  totalCapacity: number;
  openingTime: string | null;
  closingTime: string | null;
  imageUrls: string[];
  averageRating: number | null;
  reviewCount: number;
}

export interface SlotState {
  slotId: string;
  slotNumber: string;
  state: "AVAILABLE" | "RESERVED" | "OCCUPIED" | "MAINTENANCE";
}

export interface ZoneAvailability {
  zoneId: string;
  zoneName: string;
  slots: SlotState[];
}

export interface FloorAvailability {
  floorId: string;
  floorName: string;
  zones: ZoneAvailability[];
}

export interface Availability {
  locationId: string;
  start: string;
  end: string;
  vehicleType: string | null;
  freeSlots: number;
  floors: FloorAvailability[];
}

export interface Quote {
  locationId: string;
  slotId: string;
  slotNumber: string;
  vehicleType: string;
  start: string;
  end: string;
  billableHours: number;
  parkingPaise: number;
  conveniencePaise: number;
  taxPaise: number;
  totalPaise: number;
  holdTtlMinutes: number;
  pricingSnapshot: Record<string, unknown>;
}

export interface Hold {
  holdId: string;
  bookingReference: string;
  slotId: string;
  slotNumber: string;
  start: string;
  end: string;
  expiresAt: string;
  totalPaise: number;
}

export interface Booking {
  id: string;
  bookingReference: string;
  bookingStatus: string;
  paymentStatus: string;
  locationId: string;
  locationName: string;
  slotNumber: string;
  floorName: string;
  zoneName: string;
  vehicleNumber: string | null;
  start: string;
  end: string;
  actualEntryTime: string | null;
  actualExitTime: string | null;
  parkingPaise: number;
  conveniencePaise: number;
  taxPaise: number;
  totalPaise: number;
  createdAt: string;
}

export interface Vehicle {
  id: string;
  vehicleNumber: string;
  vehicleType: string;
  vehicleModel: string | null;
  vehicleColor: string | null;
  defaultVehicle: boolean;
}

export interface QrTicket {
  bookingReference: string;
  bookingStatus: string;
  token: string;
  dataUri: string;
  expiresAt: string;
  customerName: string;
  vehicleNumber: string | null;
  locationName: string;
  slotNumber: string;
  floorName: string;
  zoneName: string;
  start: string;
  end: string;
}

export interface NotificationItem {
  id: number;
  userId?: string;
  title: string;
  message: string;
  notificationType: string;
  read: boolean;
  createdAt: string;
}

export interface OrderResponse {
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
  bookingReference: string;
}
