package com.example.clinic.service;

import com.example.clinic.domain.*;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppointmentAvailabilityServiceTest {
    final DoctorScheduleRepository schedules = mock(DoctorScheduleRepository.class);
    final AppointmentRepository appointments = mock(AppointmentRepository.class);
    final AppointmentAvailabilityService service = new AppointmentAvailabilityService(schedules, appointments);
    final Doctor doctor = new Doctor();
    final LocalDate date = LocalDate.now().plusDays(3);

    void schedule(boolean available) {
        DoctorSchedule s = new DoctorSchedule();
        s.setAvailable(available); s.setStartHour(10); s.setEndHour(12);
        when(schedules.findByDoctorAndDayOfWeek(doctor, date.getDayOfWeek().getValue())).thenReturn(Optional.of(s));
    }
    @Test void pickerAndBookingRespectConfiguredHours() {
        schedule(true);
        assertThat(service.availableSlots(doctor,date)).containsExactly("10:00","11:00");
        assertThatCode(() -> service.requireBookableTime(doctor,date.atTime(10,0))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireBookableTime(doctor,date.atTime(9,0))).isInstanceOf(ClinicException.class);
        assertThatThrownBy(() -> service.requireBookableTime(doctor,date.atTime(12,0))).isInstanceOf(ClinicException.class);
    }
    @Test void unavailableDayCannotBeBooked() {
        schedule(false);
        assertThat(service.availableSlots(doctor,date)).isEmpty();
        assertThatThrownBy(() -> service.requireBookableTime(doctor,date.atTime(10,0))).isInstanceOf(ClinicException.class);
    }
    @Test void rejectsForgedPartialHourAndPastTimes() {
        assertThatThrownBy(() -> service.requireBookableTime(doctor,date.atTime(10,30))).isInstanceOf(ClinicException.class);
        assertThatThrownBy(() -> service.requireBookableTime(doctor,LocalDateTime.now().minusMinutes(1))).isInstanceOf(ClinicException.class);
        assertThat(service.availableSlots(doctor,LocalDate.now().minusDays(1))).isEmpty();
    }
    @Test void pendingPaymentAndLegacyPartialHourBlockSlot() {
        schedule(true);
        Appointment a = new Appointment();a.setStatus("PENDING_PAYMENT");a.setAppointmentDate(date.atTime(10,30));
        when(appointments.findBlockingInRange(eq(doctor),any(),any())).thenReturn(List.of(a));
        assertThat(service.availableSlots(doctor,date)).containsExactly("11:00");
    }
}
