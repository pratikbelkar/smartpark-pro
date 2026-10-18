package com.smartpark.qr;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Customer ticket reads: QR payload + printable PNG + human summary. */
@Service
@RequiredArgsConstructor
public class QrTicketService {

    private final ParkingBookingRepository bookingRepository;
    private final QrService qrService;

    @Transactional(readOnly = true)
    public QrDto.TicketResponse ticket(UUID userId, String reference) {
        ParkingBooking booking = bookingRepository.findByBookingReference(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getCustomer().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this booking.");
        }
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED
                && booking.getBookingStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.conflict(com.smartpark.exception.ErrorCode.BOOKING_CONFLICT,
                    "QR pass is available only for confirmed bookings.");
        }
        QrService.IssuedPass issued = qrService.display(booking.getId());
        QrDto.TicketResponse ticket = new QrDto.TicketResponse();
        ticket.setBookingReference(booking.getBookingReference());
        ticket.setBookingStatus(booking.getBookingStatus().name());
        ticket.setToken(issued.rawToken());
        ticket.setDataUri(qrService.renderDataUri(issued.rawToken(), 360));
        ticket.setExpiresAt(issued.pass().getExpiresAt());
        ticket.setCustomerName(booking.getCustomer().getFullName());
        ticket.setVehicleNumber(booking.getVehicle() == null ? null : booking.getVehicle().getVehicleNumber());
        ticket.setLocationName(booking.getLocation().getParkingName());
        ticket.setSlotNumber(booking.getSlot().getSlotNumber());
        ticket.setFloorName(booking.getSlot().getFloor().getFloorName());
        ticket.setZoneName(booking.getSlot().getZone().getZoneName());
        ticket.setStart(booking.getStartTime());
        ticket.setEnd(booking.getEndTime());
        return ticket;
    }

    @Transactional(readOnly = true)
    public byte[] ticketPng(UUID userId, String reference) {
        ParkingBooking booking = bookingRepository.findByBookingReference(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getCustomer().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this booking.");
        }
        QrService.IssuedPass issued = qrService.display(booking.getId());
        return qrService.renderPng(issued.rawToken(), 420);
    }
}
