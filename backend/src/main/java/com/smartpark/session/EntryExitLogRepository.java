package com.smartpark.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EntryExitLogRepository extends JpaRepository<EntryExitLog, Long> {

    List<EntryExitLog> findByBookingIdOrderByEventTimeDesc(UUID bookingId);
}
