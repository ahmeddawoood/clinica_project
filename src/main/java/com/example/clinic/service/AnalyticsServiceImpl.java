package com.example.clinic.service;

import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.RatingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final InvoiceRepository invoiceRepository;
    private final RatingRepository ratingRepository;

    public AnalyticsServiceImpl(AppointmentRepository appointmentRepository,
                                 DoctorRepository doctorRepository,
                                 PatientRepository patientRepository,
                                 InvoiceRepository invoiceRepository,
                                 RatingRepository ratingRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.invoiceRepository = invoiceRepository;
        this.ratingRepository = ratingRepository;
    }

    @Override
    public AdminKpi getAdminKpi() {
        return new AdminKpi(
            patientRepository.count(),
            doctorRepository.count(),
            appointmentRepository.count(),
            appointmentRepository.countByStatus("PENDING"),
            appointmentRepository.countByStatus("CONFIRMED"),
            appointmentRepository.countByStatus("CANCELLED"),
            appointmentRepository.countByStatus("COMPLETED"),
            nullSafe(invoiceRepository.sumPaidAmount()),
            nullSafe(invoiceRepository.sumPendingAmount())
        );
    }

    @Override
    public Map<String, Long> getMonthlyAppointments(int months) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate month = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LocalDateTime start = month.atStartOfDay();
            LocalDateTime end = month.plusMonths(1).atStartOfDay();
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.US)
                    + " " + month.getYear();
            result.put(label, appointmentRepository.countInPeriod(start, end));
        }
        return result;
    }

    @Override
    public Map<String, BigDecimal> getMonthlyRevenue(int months) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate month = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LocalDateTime start = month.atStartOfDay();
            LocalDateTime end = month.plusMonths(1).atStartOfDay();
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.US)
                    + " " + month.getYear();
            BigDecimal revenue = invoiceRepository.sumPaidAmountInPeriod(start, end);
            result.put(label, nullSafe(revenue));
        }
        return result;
    }

    @Override
    public Map<Integer, Long> getBusiestHours() {
        Map<Integer, Long> result = new LinkedHashMap<>();
        IntStream.rangeClosed(8, 18).forEach(h -> result.put(h, 0L));

        appointmentRepository.countByHourOfDay().forEach(row -> {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            result.put(hour, count);
        });
        return result;
    }

    @Override
    public List<DoctorStats> getTopDoctors(int limit) {
        return doctorRepository.findAll().stream()
            .map(d -> {
                long count = appointmentRepository.findByDoctor(d).stream()
                    .filter(a -> !"CANCELLED".equals(a.getStatus()))
                    .count();
                Double avg = ratingRepository.averageByDoctor(d);
                long ratingCount = ratingRepository.countByDoctor(d);
                return new DoctorStats(
                    "Dr. " + d.getFullName(),
                    d.getSpecialty() != null ? d.getSpecialty() : "-",
                    count,
                    avg != null ? round(avg, 1) : 0.0,
                    ratingCount
                );
            })
            .sorted((a, b) -> Long.compare(b.appointmentCount(), a.appointmentCount()))
            .limit(limit)
            .toList();
    }

    @Override
    public double getCancellationRate() {
        long total = appointmentRepository.count();
        if (total == 0) return 0.0;
        long cancelled = appointmentRepository.countByStatus("CANCELLED");
        return round((double) cancelled / total * 100.0, 1);
    }

    @Override
    public Map<String, Long> getStatusBreakdown() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("PENDING",   appointmentRepository.countByStatus("PENDING"));
        map.put("CONFIRMED", appointmentRepository.countByStatus("CONFIRMED"));
        map.put("COMPLETED", appointmentRepository.countByStatus("COMPLETED"));
        map.put("CANCELLED", appointmentRepository.countByStatus("CANCELLED"));
        return map;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private double round(double value, int places) {
        return BigDecimal.valueOf(value)
            .setScale(places, RoundingMode.HALF_UP)
            .doubleValue();
    }
}
