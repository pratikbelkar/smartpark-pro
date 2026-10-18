package com.smartpark.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

    List<StaffShift> findByStaffIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            UUID staffId, LocalDateTime now1, LocalDateTime now2);

    List<StaffShift> findByLocationOwnerIdOrderByStartsAtDesc(UUID ownerId);
}
