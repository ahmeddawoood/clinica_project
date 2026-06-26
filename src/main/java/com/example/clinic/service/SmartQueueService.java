package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class SmartQueueService {

    private static final Map<String, Integer> PRIORITY_SCORE = Map.of(
            "CRITICAL", 100,
            "HIGH",     75,
            "MEDIUM",   50,
            "LOW",      25
    );

    private static final int SLOT_MINUTES = 60;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public SmartQueueService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }

    
    public List<QueueEntry> getTodayQueue(Doctor doctor) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        List<Appointment> today = appointmentRepository.findTodayByDoctor(doctor, start, end);

        return today.stream()
                .map(a -> new QueueEntry(a, priorityScore(a)))
                .sorted(Comparator
                        .comparingInt(QueueEntry::getScore).reversed()
                        .thenComparing(e -> e.getAppointment().getAppointmentDate()))
                .collect(Collectors.toList());
    }

    
    public int estimateWaitMinutes(Doctor doctor, LocalDateTime requestedTime) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        List<Appointment> today = appointmentRepository.findTodayByDoctor(doctor, start, end);

        long before = today.stream()
                .filter(a -> !a.getAppointmentDate().isAfter(requestedTime))
                .count();

        return (int) (before * SLOT_MINUTES);
    }

    
    public Optional<Doctor> findBestDoctor(String specialty) {
        List<Doctor> candidates = doctorRepository.findBySpecialtyIgnoreCase(specialty);
        if (candidates.isEmpty()) return Optional.empty();

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        return candidates.stream()
                .min(Comparator.comparingInt(d ->
                        appointmentRepository.findTodayByDoctor(d, start, end).size()));
    }

    
    public int priorityScore(Appointment a) {
        int base = PRIORITY_SCORE.getOrDefault(a.getPriority(), 25);
        int hourPenalty = a.getAppointmentDate().getHour() - 8;
        return base - Math.max(0, hourPenalty);
    }

    
    public QueueStats getSystemStats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        List<Appointment> today = appointmentRepository
                .findByStatusAndAppointmentDateBetween("CONFIRMED", start, end);
        today.addAll(appointmentRepository.findByStatusAndAppointmentDateBetween("PENDING", start, end));

        long critical = today.stream().filter(a -> "CRITICAL".equals(a.getPriority())).count();
        long high     = today.stream().filter(a -> "HIGH".equals(a.getPriority())).count();
        long medium   = today.stream().filter(a -> "MEDIUM".equals(a.getPriority())).count();
        long low      = today.stream().filter(a -> "LOW".equals(a.getPriority())).count();

        return new QueueStats(today.size(), critical, high, medium, low);
    }

    public static class QueueEntry {
        private final Appointment appointment;
        private final int score;
        private int position;

        public QueueEntry(Appointment appointment, int score) {
            this.appointment = appointment;
            this.score = score;
        }

        public Appointment getAppointment() { return appointment; }
        public int getScore() { return score; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
    }

    public static class QueueStats {
        private final int total;
        private final long critical;
        private final long high;
        private final long medium;
        private final long low;

        public QueueStats(int total, long critical, long high, long medium, long low) {
            this.total = total; this.critical = critical;
            this.high = high; this.medium = medium; this.low = low;
        }

        public int getTotal()    { return total; }
        public long getCritical(){ return critical; }
        public long getHigh()    { return high; }
        public long getMedium()  { return medium; }
        public long getLow()     { return low; }
    }
}
