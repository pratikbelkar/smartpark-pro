package com.smartpark.booking;

import com.smartpark.parking.ParkingLocation;
import com.smartpark.parking.ParkingPricing;
import com.smartpark.parking.ParkingPricingRepository;
import com.smartpark.parking.PricingType;
import com.smartpark.parking.VehicleType;
import com.smartpark.revenue.PlatformFee;
import com.smartpark.revenue.PlatformFeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backend price authority. Every quote, hold, order and extension recomputes
 * here from effective pricing + platform fees — browser input never sets money.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final ParkingPricingRepository pricingRepository;
    private final PlatformFeeRepository feeRepository;

    public record Quote(VehicleType vehicleType, long billableHours, long parkingPaise,
                        long conveniencePaise, long taxPaise, long totalPaise,
                        Map<String, Object> snapshot) {
    }

    @Transactional(readOnly = true)
    public Quote quote(UUID locationId, VehicleType vehicleType, LocalDateTime start, LocalDateTime end) {
        ParkingPricing rate = effectiveRate(locationId, vehicleType, start);
        long minutes = Duration.between(start, end).toMinutes();
        long billable = Math.max(0, minutes - rate.getGracePeriodMinutes());
        long hours = Math.max(1, (billable + 59) / 60);

        long parking = hours * rate.getHourlyRatePaise();
        if (rate.getDailyRatePaise() != null && parking > rate.getDailyRatePaise()) {
            parking = rate.getDailyRatePaise();
        }

        long convenience = feeRepository.findEffective("convenience_fee_paise", LocalDateTime.now())
                .map(PlatformFee::getValuePaise).orElse(0L);
        if (convenience == 0L) {
            convenience = 1000L;
        }
        // No tax rule configured yet: tax stays 0 and is always shown explicitly.
        long tax = 0L;
        long total = parking + convenience + tax;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("vehicleType", vehicleType.name());
        snapshot.put("pricingType", rate.getPricingType().name());
        snapshot.put("hourlyRatePaise", rate.getHourlyRatePaise());
        snapshot.put("billableHours", hours);
        snapshot.put("graceMinutes", rate.getGracePeriodMinutes());
        snapshot.put("convenienceFeePaise", convenience);
        snapshot.put("commissionPercent", feeRepository.findEffective("commission_percent", LocalDateTime.now())
                .map(f -> f.getValuePercent() == null ? BigDecimal.ZERO : f.getValuePercent())
                .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString());
        return new Quote(vehicleType, hours, parking, convenience, tax, total, snapshot);
    }

    private ParkingPricing effectiveRate(UUID locationId, VehicleType vehicleType, LocalDateTime at) {
        List<ParkingPricing> rows = pricingRepository.findEffective(locationId, at).stream()
                .filter(p -> p.getVehicleType() == vehicleType)
                .toList();
        if (rows.isEmpty()) {
            throw com.smartpark.exception.ApiException.unprocessable(
                    "No pricing configured for " + vehicleType.name() + " at this location.");
        }
        boolean weekend = at.getDayOfWeek() == DayOfWeek.SATURDAY || at.getDayOfWeek() == DayOfWeek.SUNDAY;
        for (PricingType preferred : weekend
                ? List.of(PricingType.WEEKEND, PricingType.HOURLY)
                : List.of(PricingType.HOURLY)) {
            for (ParkingPricing row : rows) {
                if (row.getPricingType() == preferred) {
                    return row;
                }
            }
        }
        return rows.get(0);
    }
}
