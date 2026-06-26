package com.example.clinic.service;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.DoctorSchedule;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class IntelligentAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(IntelligentAssignmentService.class);

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;

    public IntelligentAssignmentService(DoctorRepository doctorRepository,
                                         DoctorScheduleRepository scheduleRepository,
                                         AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
    }

    
    public List<DoctorRecommendation> rankDoctors(String specialty, LocalDateTime requestedTime) {
        List<Doctor> candidates = specialty != null && !specialty.isBlank()
                ? doctorRepository.findBySpecialtyIgnoreCase(specialty)
                : doctorRepository.findAll();

        int dow = requestedTime.getDayOfWeek().getValue();
        LocalDateTime dayStart = requestedTime.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd   = dayStart.plusDays(1);

        List<DoctorRecommendation> recommendations = new ArrayList<>();

        for (Doctor doctor : candidates) {
            Optional<DoctorSchedule> schedOpt = scheduleRepository.findByDoctorAndDayOfWeek(doctor, dow);
            if (schedOpt.isPresent()) {
                DoctorSchedule sched = schedOpt.get();
                if (!sched.isAvailable()) continue;
                int reqHour = requestedTime.getHour();
                if (reqHour < sched.getStartHour() || reqHour >= sched.getEndHour()) continue;
            }
            long conflicts = appointmentRepository.countConflicts(doctor, requestedTime);
            if (conflicts > 0) continue;
            int todayCount = appointmentRepository.findTodayByDoctor(doctor, dayStart, dayEnd).size();
            int score = 100 - (todayCount * 10);
            if (schedOpt.isPresent()) score += 10;

            int waitMinutes = todayCount * 30;

            recommendations.add(new DoctorRecommendation(doctor, score, todayCount, waitMinutes));
        }

        recommendations.sort(Comparator.comparingInt(DoctorRecommendation::getScore).reversed());

        log.debug("Assignment ranking for specialty='{}' at {}: {} candidates, {} available",
                specialty, requestedTime, candidates.size(), recommendations.size());

        return recommendations;
    }

    
    public Optional<Doctor> assignBestDoctor(String specialty, LocalDateTime requestedTime) {
        return rankDoctors(specialty, requestedTime).stream()
                .findFirst()
                .map(DoctorRecommendation::getDoctor);
    }

    public static class DoctorRecommendation {
        private final Doctor doctor;
        private final int score;
        private final int appointmentsToday;
        private final int estimatedWaitMinutes;

        public DoctorRecommendation(Doctor doctor, int score, int appointmentsToday, int estimatedWaitMinutes) {
            this.doctor = doctor;
            this.score = score;
            this.appointmentsToday = appointmentsToday;
            this.estimatedWaitMinutes = estimatedWaitMinutes;
        }

        public Doctor getDoctor()              { return doctor; }
        public int getScore()                  { return score; }
        public int getAppointmentsToday()      { return appointmentsToday; }
        public int getEstimatedWaitMinutes()   { return estimatedWaitMinutes; }
        public String getAvailabilityLabel()   {
            if (appointmentsToday == 0) return "Disponibil";
            if (appointmentsToday < 4) return "Timp de așteptare mic";
            return "Timp de așteptare mediu";
        }
    }
}
