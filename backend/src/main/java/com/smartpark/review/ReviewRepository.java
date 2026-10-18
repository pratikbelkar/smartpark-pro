package com.smartpark.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByLocationIdOrderByCreatedAtDesc(UUID locationId, Pageable pageable);

    Page<Review> findByLocationOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.location.id = :locationId")
    Double averageRatingByLocation(UUID locationId);

    long countByLocationId(UUID locationId);
}
