package com.smartpark.owner;

import com.smartpark.exception.ApiException;
import com.smartpark.notification.NotificationService;
import com.smartpark.parking.OwnerService;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingOwner;
import com.smartpark.staff.StaffShift;
import com.smartpark.staff.StaffShiftRepository;
import com.smartpark.user.Role;
import com.smartpark.user.RoleRepository;
import com.smartpark.user.Roles;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRole;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minimal staff administration: grant role, roster shifts on own locations. */
@Service
@RequiredArgsConstructor
public class OwnerStaffService {

    private final OwnerService ownerService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StaffShiftRepository shiftRepository;
    private final ParkingLocationRepository locationRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStaff(UUID userId) {
        ownerService.requireApprovedOwner(userId);
        return userRoleRepository.findUsersByRoleName(Roles.PARKING_STAFF).stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(), "fullName", u.getFullName(), "email", u.getEmail()))
                .toList();
    }

    @Transactional
    public Map<String, Object> grantStaff(UUID userId, String email) {
        ownerService.requireApprovedOwner(userId);
        User staff = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.notFound("No account with that email. Ask them to register first."));
        if (!userRoleRepository.existsByUserIdAndRoleRoleName(staff.getId(), Roles.PARKING_STAFF)) {
            Role role = roleRepository.findByRoleName(Roles.PARKING_STAFF)
                    .orElseThrow(() -> new IllegalStateException("PARKING_STAFF role missing."));
            UserRole link = new UserRole();
            link.setUser(staff);
            link.setRole(role);
            userRoleRepository.save(link);
        }
        notificationService.notify(staff, "STAFF_ROLE",
                "You are now parking staff", "An owner granted you gate access. Shifts appear after rostering.");
        return Map.of("id", staff.getId(), "email", staff.getEmail());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShifts(UUID userId) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        return shiftRepository.findByLocationOwnerIdOrderByStartsAtDesc(owner.getId()).stream()
                .map(this::toShiftRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> createShift(UUID userId, UUID staffUserId, UUID locationId,
                                           LocalDateTime startsAt, LocalDateTime endsAt) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        var location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        if (!location.getOwner().getId().equals(owner.getId())) {
            throw ApiException.forbidden("This parking location belongs to another owner.");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw ApiException.unprocessable("Shift end must be after shift start.");
        }
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> ApiException.notFound("Staff account not found."));
        if (!userRoleRepository.existsByUserIdAndRoleRoleName(staff.getId(), Roles.PARKING_STAFF)) {
            throw ApiException.unprocessable("That account does not have the staff role yet.");
        }
        StaffShift shift = new StaffShift();
        shift.setStaff(staff);
        shift.setLocation(location);
        shift.setStartsAt(startsAt);
        shift.setEndsAt(endsAt);
        shift = shiftRepository.save(shift);
        return toShiftRow(shift);
    }

    @Transactional
    public void deleteShift(UUID userId, Long shiftId) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        StaffShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> ApiException.notFound("Shift not found."));
        if (!shift.getLocation().getOwner().getId().equals(owner.getId())) {
            throw ApiException.forbidden("This shift belongs to another owner.");
        }
        shiftRepository.delete(shift);
    }

    private Map<String, Object> toShiftRow(StaffShift shift) {
        return Map.of(
                "id", shift.getId(),
                "staffName", shift.getStaff().getFullName(),
                "staffEmail", shift.getStaff().getEmail(),
                "locationName", shift.getLocation().getParkingName(),
                "startsAt", shift.getStartsAt().toString(),
                "endsAt", shift.getEndsAt().toString());
    }
}
