package com.example.clinic.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AnalyticsService {

    
    AdminKpi getAdminKpi();

    
    Map<String, Long> getMonthlyAppointments(int months);

    
    Map<String, BigDecimal> getMonthlyRevenue(int months);

    
    Map<Integer, Long> getBusiestHours();

    
    List<DoctorStats> getTopDoctors(int limit);

    
    double getCancellationRate();

    
    Map<String, Long> getStatusBreakdown();

    record AdminKpi(
        long totalPatients,
        long totalDoctors,
        long totalAppointments,
        long pendingAppointments,
        long confirmedAppointments,
        long cancelledAppointments,
        long completedAppointments,
        BigDecimal totalRevenue,
        BigDecimal pendingRevenue
    ) {}

    record DoctorStats(
        String name,
        String specialty,
        long appointmentCount,
        double averageRating,
        long ratingCount
    ) {}
}
