package com.smartpark.parking;

import com.smartpark.audit.AuditService;
import com.smartpark.booking.BookingHoldRepository;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Parking inventory: locations, structure, slots, pricing, and window-based
 * availability. All owner mutations are scoped to the caller's own locations.
 */
@Service
@RequiredArgsConstructor
public class ParkingService {

    private final ParkingLocationRepository locationRepository;
    private final ParkingImageRepository imageRepository;
    private final ParkingFloorRepository floorRepository;
    private final ParkingZoneRepository zoneRepository;
    private final ParkingSlotRepository slotRepository;
    private final ParkingPricingRepository pricingRepository;
    private final SlotMaintenanceRepository maintenanceRepository;
    private final BookingHoldRepository holdRepository;
    private final ParkingBookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------- locations

    @Transactional
    public ParkingDto.LocationDetail createLocation(ParkingOwner owner, ParkingDto.LocationRequest request) {
        ParkingLocation location = new ParkingLocation();
        applyLocationFields(location, request);
        location.setOwner(owner);
        location = locationRepository.save(location);
        saveImages(location, request.getImageUrls());
        auditService.log(owner.getUser().getId(), "LOCATION_CREATED", "parking_locations",
                location.getId().toString(), Map.of());
        return detail(location);
    }

    @Transactional
    public ParkingDto.LocationDetail updateLocation(ParkingOwner owner, UUID locationId,
                                                    ParkingDto.LocationRequest request) {
        ParkingLocation location = ownedLocation(owner, locationId);
        applyLocationFields(location, request);
        imageRepository.deleteByLocationId(location.getId());
        saveImages(location, request.getImageUrls());
        return detail(location);
    }

    @Transactional
    public void deleteLocation(ParkingOwner owner, UUID locationId) {
        ParkingLocation location = ownedLocation(owner, locationId);
        location.setDeletedAt(LocalDateTime.now());
        location.setOperationalStatus(LocationStatus.CLOSED);
    }

    @Transactional(readOnly = true)
    public List<ParkingDto.LocationDetail> myLocations(ParkingOwner owner) {
        return locationRepository.findByOwnerIdAndDeletedAtIsNull(owner.getId()).stream()
                .map(this::detail)
                .toList();
    }

    private void applyLocationFields(ParkingLocation location, ParkingDto.LocationRequest request) {
        location.setParkingName(request.getParkingName().trim());
        location.setDescription(request.getDescription());
        location.setFullAddress(request.getFullAddress().trim());
        location.setCity(request.getCity().trim());
        location.setState(request.getState());
        location.setPostalCode(request.getPostalCode());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setEntranceLatitude(request.getEntranceLatitude());
        location.setEntranceLongitude(request.getEntranceLongitude());
        location.setTotalCapacity(request.getTotalCapacity());
        location.setOpeningTime(request.getOpeningTime());
        location.setClosingTime(request.getClosingTime());
    }

    private void saveImages(ParkingLocation location, List<String> urls) {
        if (urls == null) {
            return;
        }
        int order = 0;
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            ParkingImage image = new ParkingImage();
            image.setLocation(location);
            image.setImageUrl(url.trim());
            image.setSortOrder(order++);
            imageRepository.save(image);
        }
    }

    // ------------------------------------------------------- floors & zones

    @Transactional
    public ParkingDto.FloorResponse createFloor(ParkingOwner owner, UUID locationId, ParkingDto.FloorRequest request) {
        ParkingLocation location = ownedLocation(owner, locationId);
        ParkingFloor floor = new ParkingFloor();
        floor.setLocation(location);
        floor.setFloorName(request.getFloorName().trim());
        floor.setFloorNumber(request.getFloorNumber());
        try {
            return toFloorResponse(floorRepository.save(floor));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Floor name already exists at this location.");
        }
    }

    @Transactional(readOnly = true)
    public List<ParkingDto.FloorResponse> listFloors(ParkingOwner owner, UUID locationId) {
        ownedLocation(owner, locationId);
        return floorRepository.findByLocationIdOrderByFloorNumberAsc(locationId).stream()
                .map(this::toFloorResponse)
                .toList();
    }

    @Transactional
    public ParkingDto.ZoneResponse createZone(ParkingOwner owner, ParkingDto.ZoneRequest request) {
        ParkingFloor floor = floorRepository.findById(request.getFloorId())
                .orElseThrow(() -> ApiException.notFound("Floor not found."));
        ownedLocation(owner, floor.getLocation().getId());
        ParkingZone zone = new ParkingZone();
        zone.setFloor(floor);
        zone.setZoneName(request.getZoneName().trim());
        try {
            return toZoneResponse(zoneRepository.save(zone));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Zone name already exists on this floor.");
        }
    }

    @Transactional(readOnly = true)
    public List<ParkingDto.ZoneResponse> listZones(ParkingOwner owner, UUID floorId) {
        ParkingFloor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> ApiException.notFound("Floor not found."));
        ownedLocation(owner, floor.getLocation().getId());
        return zoneRepository.findByFloorId(floorId).stream()
                .map(this::toZoneResponse)
                .toList();
    }

    private ParkingDto.FloorResponse toFloorResponse(ParkingFloor floor) {
        ParkingDto.FloorResponse response = new ParkingDto.FloorResponse();
        response.setId(floor.getId());
        response.setFloorName(floor.getFloorName());
        response.setFloorNumber(floor.getFloorNumber());
        return response;
    }

    private ParkingDto.ZoneResponse toZoneResponse(ParkingZone zone) {
        ParkingDto.ZoneResponse response = new ParkingDto.ZoneResponse();
        response.setId(zone.getId());
        response.setZoneName(zone.getZoneName());
        response.setFloorName(zone.getFloor().getFloorName());
        return response;
    }

    // ----------------------------------------------------------------- slots

    @Transactional
    public ParkingDto.SlotResponse createSlot(ParkingOwner owner, UUID locationId, ParkingDto.SlotRequest request) {
        ParkingLocation location = ownedLocation(owner, locationId);
        ParkingFloor floor = floorOf(location, request.getFloorId());
        ParkingZone zone = zoneOf(floor, request.getZoneId());
        VehicleType type = parseVehicleType(request.getVehicleType());
        String number = request.getSlotNumber().trim().toUpperCase(Locale.ROOT);

        if (slotRepository.existsByLocationIdAndSlotNumberIgnoreCase(location.getId(), number)) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Slot " + number + " already exists here.");
        }
        ParkingSlot slot = new ParkingSlot();
        slot.setLocation(location);
        slot.setFloor(floor);
        slot.setZone(zone);
        slot.setSlotNumber(number);
        slot.setVehicleType(type);
        return toSlotResponse(slotRepository.save(slot));
    }

    @Transactional
    public List<ParkingDto.SlotResponse> bulkCreateSlots(ParkingOwner owner, UUID locationId,
                                                        ParkingDto.BulkSlotRequest request) {
        ParkingLocation location = ownedLocation(owner, locationId);
        ParkingFloor floor = floorOf(location, request.getFloorId());
        ParkingZone zone = zoneOf(floor, request.getZoneId());
        VehicleType type = parseVehicleType(request.getVehicleType());
        String prefix = request.getPrefix().trim().toUpperCase(Locale.ROOT);

        List<ParkingDto.SlotResponse> created = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            String number = prefix + String.format("%03d", request.getStartNumber() + i);
            if (slotRepository.existsByLocationIdAndSlotNumberIgnoreCase(location.getId(), number)) {
                continue;
            }
            ParkingSlot slot = new ParkingSlot();
            slot.setLocation(location);
            slot.setFloor(floor);
            slot.setZone(zone);
            slot.setSlotNumber(number);
            slot.setVehicleType(type);
            created.add(toSlotResponse(slotRepository.save(slot)));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<ParkingDto.SlotResponse> listSlots(ParkingOwner owner, UUID locationId, String vehicleType) {
        ownedLocation(owner, locationId);
        List<ParkingSlot> slots;
        if (vehicleType == null || vehicleType.isBlank()) {
            slots = slotRepository.findByLocationIdOrderBySlotNumberAsc(locationId);
        } else {
            slots = slotRepository.findByLocationIdAndVehicleTypeAndOperationalStatusOrderBySlotNumberAsc(
                    locationId, parseVehicleType(vehicleType), SlotOperationalStatus.AVAILABLE);
        }
        return slots.stream().map(this::toSlotResponse).toList();
    }

    @Transactional
    public ParkingDto.SlotResponse updateSlot(ParkingOwner owner, UUID slotId, ParkingDto.SlotRequest request) {
        ParkingSlot slot = ownedSlot(owner, slotId);
        ParkingFloor floor = floorOf(slot.getLocation(), request.getFloorId());
        ParkingZone zone = zoneOf(floor, request.getZoneId());
        String number = request.getSlotNumber().trim().toUpperCase(Locale.ROOT);
        if (!number.equals(slot.getSlotNumber())
                && slotRepository.existsByLocationIdAndSlotNumberIgnoreCase(slot.getLocation().getId(), number)) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Slot " + number + " already exists here.");
        }
        slot.setFloor(floor);
        slot.setZone(zone);
        slot.setSlotNumber(number);
        slot.setVehicleType(parseVehicleType(request.getVehicleType()));
        return toSlotResponse(slot);
    }

    /** Disables, maintains, or re-enables a slot; maintenance windows are recorded. */
    @Transactional
    public ParkingDto.SlotResponse changeSlotStatus(ParkingOwner owner, UUID slotId,
                                                    ParkingDto.SlotStatusRequest request) {
        ParkingSlot slot = ownedSlot(owner, slotId);
        SlotOperationalStatus status;
        try {
            status = SlotOperationalStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Status must be AVAILABLE, DISABLED or MAINTENANCE.");
        }
        slot.setOperationalStatus(status);

        if (status == SlotOperationalStatus.MAINTENANCE) {
            SlotMaintenance maintenance = new SlotMaintenance();
            maintenance.setSlot(slot);
            maintenance.setReason(request.getReason() == null || request.getReason().isBlank()
                    ? "Scheduled maintenance" : request.getReason().trim());
            maintenance.setStartsAt(LocalDateTime.now());
            maintenance.setEndsAt(request.getEndsAt());
            maintenance.setCreatedBy(owner.getUser().getId());
            maintenanceRepository.save(maintenance);
        } else {
            maintenanceRepository.findOverlapping(List.of(slot.getId()),
                            LocalDateTime.now().minusYears(10), LocalDateTime.now().plusSeconds(1)).stream()
                    .filter(m -> m.getEndsAt() == null)
                    .forEach(m -> m.setEndsAt(LocalDateTime.now()));
        }
        return toSlotResponse(slot);
    }

    // --------------------------------------------------------------- pricing

    /**
     * Versioned pricing: closes the currently effective row for the same
     * (vehicle, type) and opens a new one. History is never rewritten.
     */
    @Transactional
    public ParkingDto.PricingResponse savePricing(ParkingOwner owner, UUID locationId,
                                                  ParkingDto.PricingRequest request) {
        ParkingLocation location = ownedLocation(owner, locationId);
        VehicleType vehicleType = parseVehicleType(request.getVehicleType());
        PricingType pricingType = parsePricingType(request.getPricingType());
        LocalDateTime now = LocalDateTime.now();

        pricingRepository.findEffective(location.getId(), now).stream()
                .filter(p -> p.getVehicleType() == vehicleType
                        && p.getPricingType() == pricingType
                        && p.getEffectiveUntil() == null)
                .forEach(p -> p.setEffectiveUntil(now));

        ParkingPricing pricing = new ParkingPricing();
        pricing.setLocation(location);
        pricing.setVehicleType(vehicleType);
        pricing.setPricingType(pricingType);
        pricing.setHourlyRatePaise(toPaise(request.getHourlyRateRupees()));
        pricing.setDailyRatePaise(request.getDailyRateRupees() == null
                ? null : toPaise(request.getDailyRateRupees()));
        pricing.setGracePeriodMinutes(request.getGracePeriodMinutes());
        pricing.setEffectiveFrom(now);
        return toPricingResponse(pricingRepository.save(pricing));
    }

    @Transactional(readOnly = true)
    public List<ParkingDto.PricingResponse> listPricing(ParkingOwner owner, UUID locationId) {
        ownedLocation(owner, locationId);
        return pricingRepository.findEffective(locationId, LocalDateTime.now()).stream()
                .map(this::toPricingResponse)
                .toList();
    }

    /** Current HOURLY rate (paise) per vehicle type, for cards and quotes. */
    @Transactional(readOnly = true)
    public Map<String, Long> hourlyRates(UUID locationId) {
        Map<String, Long> rates = new LinkedHashMap<>();
        for (ParkingPricing pricing : pricingRepository.findEffective(locationId, LocalDateTime.now())) {
            if (pricing.getPricingType() == PricingType.HOURLY) {
                rates.putIfAbsent(pricing.getVehicleType().name(), pricing.getHourlyRatePaise());
            }
        }
        return rates;
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public Page<ParkingDto.LocationCard> search(String city, String vehicleType,
                                                LocalDateTime start, LocalDateTime end, Pageable pageable) {
        Page<ParkingLocation> page = city == null || city.isBlank()
                ? locationRepository.findPublic(pageable)
                : locationRepository.searchPublic(city.trim(), pageable);
        VehicleType type = vehicleType == null || vehicleType.isBlank() ? null : parseVehicleType(vehicleType);
        if (start != null && end != null) {
            assertWindow(start, end);
        }
        final LocalDateTime fStart = start;
        final LocalDateTime fEnd = end;
        return page.map(location -> toCard(location, type, fStart, fEnd));
    }

    @Transactional(readOnly = true)
    public ParkingDto.LocationDetail publicDetail(UUID locationId) {
        ParkingLocation location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        if (location.getOperationalStatus() != LocationStatus.OPEN) {
            throw ApiException.notFound("Parking location not found.");
        }
        return detail(location);
    }

    /**
     * Window-based slot states for the visual grid. Physical occupancy is only
     * a display hint for "right now"; booking truth comes from holds+bookings.
     */
    @Transactional(readOnly = true)
    public ParkingDto.AvailabilityResponse availability(UUID locationId, LocalDateTime start,
                                                       LocalDateTime end, String vehicleTypeRaw) {
        ParkingLocation location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        if (location.getOperationalStatus() != LocationStatus.OPEN) {
            throw ApiException.notFound("Parking location not found.");
        }
        assertWindow(start, end);
        VehicleType filter = vehicleTypeRaw == null || vehicleTypeRaw.isBlank()
                ? null : parseVehicleType(vehicleTypeRaw);

        List<ParkingSlot> slots = slotRepository.findByLocationIdOrderBySlotNumberAsc(locationId).stream()
                .filter(s -> filter == null || s.getVehicleType() == filter)
                .toList();
        List<UUID> ids = slots.stream().map(ParkingSlot::getId).toList();

        Map<UUID, String> state = new LinkedHashMap<>();
        for (ParkingSlot slot : slots) {
            state.put(slot.getId(), slot.getOperationalStatus() == SlotOperationalStatus.AVAILABLE
                    ? "AVAILABLE" : "MAINTENANCE");
        }
        if (!ids.isEmpty()) {
            maintenanceRepository.findOverlapping(ids, start, end)
                    .forEach(m -> state.put(m.getSlot().getId(), "MAINTENANCE"));
            holdRepository.findActiveOverlaps(ids, start, end)
                    .forEach(h -> state.computeIfPresent(h.getSlot().getId(),
                            (k, v) -> v.equals("AVAILABLE") ? "RESERVED" : v));
            bookingRepository.findBlockingOverlaps(ids, start, end)
                    .forEach(b -> state.computeIfPresent(b.getSlot().getId(),
                            (k, v) -> v.equals("AVAILABLE") ? "RESERVED" : v));
            LocalDateTime now = LocalDateTime.now();
            if (!start.isAfter(now) && !end.isBefore(now)) {
                for (ParkingSlot slot : slots) {
                    if (slot.getPhysicalOccupancyStatus() == PhysicalOccupancyStatus.OCCUPIED
                            && state.get(slot.getId()).equals("AVAILABLE")) {
                        state.put(slot.getId(), "OCCUPIED");
                    }
                }
            }
        }

        ParkingDto.AvailabilityResponse response = new ParkingDto.AvailabilityResponse(
                locationId, start, end, filter == null ? null : filter.name(),
                state.values().stream().filter(s -> s.equals("AVAILABLE")).count());

        Map<UUID, ParkingDto.FloorAvailability> floors = new LinkedHashMap<>();
        Map<UUID, ParkingDto.ZoneAvailability> zones = new LinkedHashMap<>();
        for (ParkingSlot slot : slots) {
            ParkingDto.FloorAvailability floor = floors.computeIfAbsent(slot.getFloor().getId(),
                    k -> new ParkingDto.FloorAvailability(k, slot.getFloor().getFloorName()));
            if (response.getFloors().stream().noneMatch(f -> f.getFloorId().equals(floor.getFloorId()))) {
                response.getFloors().add(floor);
            }
            ParkingDto.ZoneAvailability zone = zones.computeIfAbsent(slot.getZone().getId(),
                    k -> new ParkingDto.ZoneAvailability(k, slot.getZone().getZoneName()));
            if (floor.getZones().stream().noneMatch(z -> z.getZoneId().equals(zone.getZoneId()))) {
                floor.getZones().add(zone);
            }
            zone.getSlots().add(new ParkingDto.SlotState(slot.getId(), slot.getSlotNumber(), state.get(slot.getId())));
        }
        return response;
    }

    // ----------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public Page<ParkingDto.LocationCard> listAll(Pageable pageable) {
        return locationRepository.findByDeletedAtIsNull(pageable).map(l -> toCard(l, null, null, null));
    }

    @Transactional
    public ParkingDto.LocationCard updateOperationalStatus(UUID locationId, String statusRaw) {
        ParkingLocation location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        try {
            location.setOperationalStatus(LocationStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT)));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Status must be OPEN, CLOSED or HIDDEN.");
        }
        return toCard(location, null, null, null);
    }

    // --------------------------------------------------------------- helpers

    private ParkingLocation ownedLocation(ParkingOwner owner, UUID locationId) {
        ParkingLocation location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        if (!location.getOwner().getId().equals(owner.getId())) {
            throw ApiException.forbidden("This parking location belongs to another owner.");
        }
        return location;
    }

    private ParkingFloor floorOf(ParkingLocation location, UUID floorId) {
        ParkingFloor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> ApiException.notFound("Floor not found."));
        if (!floor.getLocation().getId().equals(location.getId())) {
            throw ApiException.unprocessable("Floor does not belong to this parking location.");
        }
        return floor;
    }

    private ParkingZone zoneOf(ParkingFloor floor, UUID zoneId) {
        ParkingZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> ApiException.notFound("Zone not found."));
        if (!zone.getFloor().getId().equals(floor.getId())) {
            throw ApiException.unprocessable("Zone does not belong to this floor.");
        }
        return zone;
    }

    private ParkingSlot ownedSlot(ParkingOwner owner, UUID slotId) {
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("Slot not found."));
        if (!slot.getLocation().getOwner().getId().equals(owner.getId())) {
            throw ApiException.forbidden("This slot belongs to another owner.");
        }
        return slot;
    }

    private VehicleType parseVehicleType(String raw) {
        try {
            return VehicleType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Vehicle type must be TWO_WHEELER or CAR.");
        }
    }

    private PricingType parsePricingType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PricingType.HOURLY;
        }
        try {
            return PricingType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Pricing type must be HOURLY, DAILY, FLAT, WEEKEND or PEAK.");
        }
    }

    private long toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private void assertWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.unprocessable("End time must be after start time.");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw ApiException.unprocessable("Start time cannot be in the past.");
        }
    }

    private ParkingDto.LocationCard toCard(ParkingLocation location, VehicleType type,
                                           LocalDateTime start, LocalDateTime end) {
        ParkingDto.LocationCard card = new ParkingDto.LocationCard();
        card.setId(location.getId());
        card.setParkingName(location.getParkingName());
        card.setCity(location.getCity());
        card.setFullAddress(location.getFullAddress());
        card.setLatitude(location.getLatitude());
        card.setLongitude(location.getLongitude());
        card.setOperationalStatus(location.getOperationalStatus().name());
        card.setImageUrl(imageRepository.findByLocationIdOrderBySortOrderAsc(location.getId()).stream()
                .findFirst().map(ParkingImage::getImageUrl).orElse(null));
        Map<String, Long> rates = hourlyRates(location.getId());
        card.setHourlyRates(rates);
        if (start != null && end != null) {
            card.setFreeSlots(countFree(location.getId(), type, start, end));
        }
        return card;
    }

    private long countFree(UUID locationId, VehicleType type, LocalDateTime start, LocalDateTime end) {
        List<ParkingSlot> slots = slotRepository.findByLocationIdOrderBySlotNumberAsc(locationId).stream()
                .filter(s -> s.getOperationalStatus() == SlotOperationalStatus.AVAILABLE)
                .filter(s -> type == null || s.getVehicleType() == type)
                .toList();
        if (slots.isEmpty()) {
            return 0;
        }
        List<UUID> ids = slots.stream().map(ParkingSlot::getId).toList();
        java.util.Set<UUID> blocked = new java.util.HashSet<>();
        maintenanceRepository.findOverlapping(ids, start, end)
                .forEach(m -> blocked.add(m.getSlot().getId()));
        holdRepository.findActiveOverlaps(ids, start, end)
                .forEach(h -> blocked.add(h.getSlot().getId()));
        bookingRepository.findBlockingOverlaps(ids, start, end)
                .forEach(b -> blocked.add(b.getSlot().getId()));
        return slots.size() - blocked.size();
    }

    private ParkingDto.LocationDetail detail(ParkingLocation location) {
        ParkingDto.LocationDetail detail = new ParkingDto.LocationDetail();
        detail.setId(location.getId());
        detail.setParkingName(location.getParkingName());
        detail.setDescription(location.getDescription());
        detail.setFullAddress(location.getFullAddress());
        detail.setCity(location.getCity());
        detail.setState(location.getState());
        detail.setPostalCode(location.getPostalCode());
        detail.setLatitude(location.getLatitude());
        detail.setLongitude(location.getLongitude());
        detail.setEntranceLatitude(location.getEntranceLatitude());
        detail.setEntranceLongitude(location.getEntranceLongitude());
        detail.setTotalCapacity(location.getTotalCapacity());
        detail.setOpeningTime(location.getOpeningTime());
        detail.setClosingTime(location.getClosingTime());
        detail.setOperationalStatus(location.getOperationalStatus().name());
        detail.setImageUrls(imageRepository.findByLocationIdOrderBySortOrderAsc(location.getId()).stream()
                .map(ParkingImage::getImageUrl).toList());
        detail.setHourlyRates(hourlyRates(location.getId()));
        Double avg = reviewRepository.averageRatingByLocation(location.getId());
        detail.setAverageRating(avg == null ? null : Math.round(avg * 10.0) / 10.0);
        detail.setReviewCount(reviewRepository.countByLocationId(location.getId()));
        return detail;
    }

    private ParkingDto.SlotResponse toSlotResponse(ParkingSlot slot) {
        ParkingDto.SlotResponse response = new ParkingDto.SlotResponse();
        response.setId(slot.getId());
        response.setSlotNumber(slot.getSlotNumber());
        response.setVehicleType(slot.getVehicleType().name());
        response.setFloorName(slot.getFloor().getFloorName());
        response.setZoneName(slot.getZone().getZoneName());
        response.setOperationalStatus(slot.getOperationalStatus().name());
        response.setPhysicalOccupancyStatus(slot.getPhysicalOccupancyStatus().name());
        return response;
    }

    private ParkingDto.PricingResponse toPricingResponse(ParkingPricing pricing) {
        ParkingDto.PricingResponse response = new ParkingDto.PricingResponse();
        response.setId(pricing.getId());
        response.setVehicleType(pricing.getVehicleType().name());
        response.setPricingType(pricing.getPricingType().name());
        response.setHourlyRatePaise(pricing.getHourlyRatePaise());
        response.setDailyRatePaise(pricing.getDailyRatePaise());
        response.setGracePeriodMinutes(pricing.getGracePeriodMinutes());
        response.setEffectiveFrom(pricing.getEffectiveFrom());
        response.setEffectiveUntil(pricing.getEffectiveUntil());
        return response;
    }
}
