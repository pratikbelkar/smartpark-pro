package com.smartpark.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingBookingRepository extends JpaRepository<ParkingBooking, UUID> {

    Optional<ParkingBooking> findByBookingReference(String bookingReference);

    Page<ParkingBooking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<ParkingBooking> findByVehicle_VehicleNumberIgnoreCaseAndBookingStatusInOrderByStartTimeDesc(
            String vehicleNumber, java.util.Collection<BookingStatus> statuses);

    Page<ParkingBooking> findByLocationOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Page<ParkingBooking> findByLocationOwnerIdAndLocationIdOrderByCreatedAtDesc(
            UUID ownerId, UUID locationId, Pageable pageable);

    Page<ParkingBooking> findByBookingStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);


    boolean existsByVehicleIdAndBookingStatusIn(UUID vehicleId, java.util.Collection<BookingStatus> statuses);

    @Query("""
            select b from ParkingBooking b
            where b.slot.id in :slotIds
              and b.bookingStatus in (com.smartpark.booking.BookingStatus.CONFIRMED,
                                      com.smartpark.booking.BookingStatus.CHECKED_IN)
              and b.startTime < :end and b.endTime > :start
            """)
    List<ParkingBooking> findBlockingOverlaps(List<UUID> slotIds, LocalDateTime start, LocalDateTime end);

    @Query("""
            select b from ParkingBooking b
            where b.location.id = :locationId
              and b.bookingStatus in (com.smartpark.booking.BookingStatus.CONFIRMED,
                                      com.smartpark.booking.BookingStatus.CHECKED_IN)
              and b.startTime < :end and b.endTime > :start
            """)
    List<ParkingBooking> findBlockingOverlapsForLocation(UUID locationId, LocalDateTime start,
                                                         LocalDateTime end);

    @Query("""
            select b from ParkingBooking b
            where b.bookingStatus = com.smartpark.booking.BookingStatus.PENDING_PAYMENT
              and b.hold is not null
              and b.hold.status = com.smartpark.booking.HoldStatus.EXPIRED
            """)
    List<ParkingBooking> findUnpaidWithExpiredHold();

    @Query("""
            select b from ParkingBooking b
            where b.bookingStatus = com.smartpark.booking.BookingStatus.CONFIRMED
              and b.actualEntryTime is null
            """)
    List<ParkingBooking> findConfirmedAwaitingEntry();

    List<ParkingBooking> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("""
            select b from ParkingBooking b
            where b.location.owner.id = :ownerId
              and b.createdAt between :from and :to
            """)
    List<ParkingBooking> findByOwnerAndCreatedBetween(UUID ownerId, LocalDateTime from, LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("""
            select b from ParkingBooking b
            where b.location.owner.id = :ownerId
              and (:locationId is null or b.location.id = :locationId)
              and b.startTime >= :from and b.startTime < :to
            order by b.startTime desc
            """)
    List<ParkingBooking> findByOwnerFiltered(UUID ownerId, UUID locationId,
                                             LocalDateTime from, LocalDateTime to);
}
