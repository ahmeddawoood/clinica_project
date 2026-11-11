package com.example.clinic.service;

import com.example.clinic.domain.Doctor;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/** Shared policy for the slot picker and transactional booking validation. */
@Service
public class AppointmentAvailabilityService {
    private final DoctorScheduleRepository schedules;
    private final AppointmentRepository appointments;

    public AppointmentAvailabilityService(DoctorScheduleRepository schedules, AppointmentRepository appointments) {
        this.schedules = schedules;
        this.appointments = appointments;
    }

    public List<String> availableSlots(Doctor doctor, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        int[] hours = workingHours(doctor, date);
        var booked = appointments.findBlockingInRange(doctor, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .filter(a -> !"CANCELLED".equals(a.getStatus()))
                .map(a -> a.getAppointmentDate()).filter(t -> t != null && t.toLocalDate().equals(date))
                .map(LocalDateTime::getHour).collect(java.util.stream.Collectors.toSet());
        return IntStream.range(hours[0], hours[1])
                .filter(h -> date.atTime(h, 0).isAfter(now) && !booked.contains(h))
                .mapToObj(h -> String.format(java.util.Locale.ROOT, "%02d:00", h)).toList();
    }

    public void requireBookableTime(Doctor doctor, LocalDateTime time) {
        if (time == null || !time.isAfter(LocalDateTime.now())) {
            throw new ClinicException("Data programării trebuie să fie în viitor.");
        }
        int[] hours = workingHours(doctor, time.toLocalDate());
        if (time.getHour() < hours[0] || time.getHour() >= hours[1]) {
            throw new ClinicException("Ora aleasă nu este în programul doctorului (implicit 08:00–18:00).");
        }
        if (time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new ClinicException("Alege un interval disponibil la oră fixă.");
        }
    }

    private int[] workingHours(Doctor doctor, LocalDate date) {
        return schedules.findByDoctorAndDayOfWeek(doctor, date.getDayOfWeek().getValue())
                .map(s -> s.isAvailable() ? new int[]{s.getStartHour(), s.getEndHour()} : new int[]{0, 0})
                .orElseGet(() -> new int[]{8, 18});
    }
}
