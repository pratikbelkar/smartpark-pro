package com.smartpark.vehicle;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.parking.VehicleType;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final ParkingBookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<VehicleDto.VehicleResponse> list(UUID userId) {
        return vehicleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VehicleDto.VehicleResponse create(UUID userId, VehicleDto.VehicleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        String number = normalize(request.getVehicleNumber());
        if (vehicleRepository.existsByUserIdAndVehicleNumberIgnoreCase(userId, number)) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "This vehicle is already saved on your account.");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setUser(user);
        vehicle.setVehicleNumber(number);
        vehicle.setVehicleType(VehicleType.valueOf(request.getVehicleType()));
        vehicle.setVehicleModel(request.getVehicleModel());
        vehicle.setVehicleColor(request.getVehicleColor());
        vehicle.setDefaultVehicle(request.isDefaultVehicle());
        vehicle = vehicleRepository.save(vehicle);
        if (vehicle.isDefaultVehicle()) {
            unsetOtherDefaults(userId, vehicle.getId());
        }
        return toResponse(vehicle);
    }

    @Transactional
    public VehicleDto.VehicleResponse update(UUID userId, UUID vehicleId, VehicleDto.VehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        String number = normalize(request.getVehicleNumber());
        if (!number.equals(vehicle.getVehicleNumber())
                && vehicleRepository.existsByUserIdAndVehicleNumberIgnoreCase(userId, number)) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "This vehicle is already saved on your account.");
        }
        vehicle.setVehicleNumber(number);
        vehicle.setVehicleType(VehicleType.valueOf(request.getVehicleType()));
        vehicle.setVehicleModel(request.getVehicleModel());
        vehicle.setVehicleColor(request.getVehicleColor());
        vehicle.setDefaultVehicle(request.isDefaultVehicle());
        if (vehicle.isDefaultVehicle()) {
            unsetOtherDefaults(userId, vehicle.getId());
        }
        return toResponse(vehicle);
    }

    @Transactional
    public void delete(UUID userId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        boolean inUse = bookingRepository.existsByVehicleIdAndBookingStatusIn(vehicleId,
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN));
        if (inUse) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                    "This vehicle has an active booking. Cancel it before removing the vehicle.");
        }
        vehicleRepository.delete(vehicle);
    }

    private void unsetOtherDefaults(UUID userId, UUID keepId) {
        vehicleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(v -> !v.getId().equals(keepId) && v.isDefaultVehicle())
                .forEach(v -> v.setDefaultVehicle(false));
    }

    private String normalize(String raw) {
        String number = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "");
        if (!number.matches("[A-Z0-9]{4,20}")) {
            throw ApiException.unprocessable("Enter a valid vehicle registration number.");
        }
        return number;
    }

    private VehicleDto.VehicleResponse toResponse(Vehicle vehicle) {
        VehicleDto.VehicleResponse response = new VehicleDto.VehicleResponse();
        response.setId(vehicle.getId());
        response.setVehicleNumber(vehicle.getVehicleNumber());
        response.setVehicleType(vehicle.getVehicleType().name());
        response.setVehicleModel(vehicle.getVehicleModel());
        response.setVehicleColor(vehicle.getVehicleColor());
        response.setDefaultVehicle(vehicle.isDefaultVehicle());
        return response;
    }
}
