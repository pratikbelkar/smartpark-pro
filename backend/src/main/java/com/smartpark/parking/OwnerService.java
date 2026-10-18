package com.smartpark.parking;

import com.smartpark.audit.AuditService;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.notification.NotificationService;
import com.smartpark.security.AuthUser;
import com.smartpark.user.Role;
import com.smartpark.user.RoleRepository;
import com.smartpark.user.Roles;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRole;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Owner onboarding (admin-approved) + tenant resolution for owner APIs. */
@Service
@RequiredArgsConstructor
public class OwnerService {

    private final ParkingOwnerRepository ownerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public ParkingDto.OwnerResponse onboard(UUID userId, ParkingDto.OnboardRequest request) {
        if (ownerRepository.findByUserId(userId).isPresent()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "An owner profile already exists for this account.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));

        ParkingOwner owner = new ParkingOwner();
        owner.setUser(user);
        owner.setBusinessName(request.getBusinessName().trim());
        owner.setBusinessEmail(request.getBusinessEmail());
        owner.setBusinessPhone(request.getBusinessPhone());
        owner.setBusinessAddress(request.getBusinessAddress());
        owner.setVerificationStatus(VerificationStatus.PENDING);
        owner = ownerRepository.save(owner);

        auditService.log(userId, "OWNER_ONBOARDED", "parking_owners", owner.getId().toString(), Map.of());
        return toResponse(owner);
    }

    /** Resolves the caller's approved owner profile or 403s. */
    @Transactional(readOnly = true)
    public ParkingOwner requireApprovedOwner(UUID userId) {
        ParkingOwner owner = ownerRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("No parking-owner profile on this account."));
        if (owner.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw ApiException.forbidden("Owner profile is " + owner.getVerificationStatus().name().toLowerCase()
                    + ". It must be approved before managing parking.");
        }
        return owner;
    }

    @Transactional(readOnly = true)
    public ParkingDto.OwnerResponse myProfile(UUID userId) {
        ParkingOwner owner = ownerRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("No parking-owner profile on this account."));
        return toResponse(owner);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public Page<ParkingDto.OwnerResponse> list(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return ownerRepository.findAll(pageable).map(this::toResponse);
        }
        VerificationStatus parsed = parseStatus(status);
        return ownerRepository.findByVerificationStatus(parsed, pageable).map(this::toResponse);
    }

    @Transactional
    public ParkingDto.OwnerResponse updateStatus(UUID ownerId, String statusRaw, AuthUser admin) {
        ParkingOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Owner not found."));
        VerificationStatus status = parseStatus(statusRaw);
        owner.setVerificationStatus(status);

        if (status == VerificationStatus.APPROVED
                && !userRoleRepository.existsByUserIdAndRoleRoleName(owner.getUser().getId(), Roles.PARKING_OWNER)) {
            Role role = roleRepository.findByRoleName(Roles.PARKING_OWNER)
                    .orElseThrow(() -> new IllegalStateException("PARKING_OWNER role missing."));
            UserRole link = new UserRole();
            link.setUser(owner.getUser());
            link.setRole(role);
            userRoleRepository.save(link);
        }

        auditService.log(admin.getId(), "OWNER_STATUS_" + status.name(), "parking_owners",
                owner.getId().toString(), Map.of());
        notificationService.notify(owner.getUser(), "OWNER_VERIFICATION",
                "Owner profile " + status.name().toLowerCase(),
                "Your parking-owner profile was " + status.name().toLowerCase() + " by the platform team.");
        return toResponse(owner);
    }

    private VerificationStatus parseStatus(String raw) {
        try {
            return VerificationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Status must be PENDING, APPROVED, SUSPENDED or REJECTED.");
        }
    }

    private ParkingDto.OwnerResponse toResponse(ParkingOwner owner) {
        ParkingDto.OwnerResponse response = new ParkingDto.OwnerResponse();
        response.setId(owner.getId());
        response.setBusinessName(owner.getBusinessName());
        response.setBusinessEmail(owner.getBusinessEmail());
        response.setBusinessPhone(owner.getBusinessPhone());
        response.setBusinessAddress(owner.getBusinessAddress());
        response.setVerificationStatus(owner.getVerificationStatus().name());
        response.setCreatedAt(owner.getCreatedAt());
        return response;
    }
}
