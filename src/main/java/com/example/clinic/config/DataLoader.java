package com.example.clinic.config;

import com.example.clinic.domain.*;
import com.example.clinic.dto.RegisterRequest;
import com.example.clinic.repository.*;
import com.example.clinic.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private static final Random RNG = new Random(42);

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final RatingRepository ratingRepository;
    private final MessageRepository messageRepository;

    public DataLoader(UserService userService, UserRepository userRepository, PasswordEncoder passwordEncoder,
                      DoctorRepository doctorRepository, PatientRepository patientRepository,
                      DoctorScheduleRepository scheduleRepository,
                      AppointmentRepository appointmentRepository,
                      MedicalRecordRepository medicalRecordRepository,
                      PrescriptionRepository prescriptionRepository,
                      InvoiceRepository invoiceRepository,
                      RatingRepository ratingRepository,
                      MessageRepository messageRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.ratingRepository = ratingRepository;
        this.messageRepository = messageRepository;
    }

    private static final String[] PRENUME_M = {
        "Andrei", "Mihai", "Alexandru", "Cristian", "Vlad", "Razvan", "Bogdan", "Stefan",
        "Adrian", "Catalin", "Florin", "Gabriel", "Ionut", "Marius", "Nicolae", "Octavian",
        "Paul", "Radu", "Sergiu", "Tudor", "Vasile", "Ciprian", "Daniel", "George"
    };

    private static final String[] PRENUME_F = {
        "Maria", "Ana", "Elena", "Ioana", "Cristina", "Andreea", "Diana", "Roxana",
        "Alexandra", "Bianca", "Carmen", "Daniela", "Florentina", "Gabriela", "Iulia",
        "Laura", "Mihaela", "Nicoleta", "Oana", "Raluca", "Simona", "Teodora", "Valentina"
    };

    private static final String[] NUME = {
        "Popescu", "Ionescu", "Popa", "Stoica", "Dumitrescu", "Stan", "Diaconu",
        "Marin", "Tanase", "Munteanu", "Constantin", "Radu", "Gheorghe", "Toma",
        "Nistor", "Florea", "Iliescu", "Mocanu", "Lazar", "Ungureanu", "Calin",
        "Niculescu", "Petrescu", "Vasilescu", "Manole", "Albu", "Sava", "Voicu"
    };

    private static final String[] ORASE = {
        "Bucuresti", "Cluj-Napoca", "Timisoara", "Iasi", "Constanta", "Brasov", "Craiova",
        "Galati", "Ploiesti", "Oradea", "Sibiu", "Arad", "Pitesti", "Bacau"
    };

    private static final String[] STRAZI = {
        "Str. Florilor", "Bd. Unirii", "Str. Victoriei", "Str. Mihai Eminescu",
        "Bd. 1 Decembrie", "Str. Stefan cel Mare", "Aleea Castanilor", "Str. Garii",
        "Bd. Republicii", "Str. Pacii", "Str. Independentei", "Bd. Carol I"
    };

    private static final String[] GRUPE_SANGE = {"A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "0-"};

    private static final String[] ALERGII = {
        null, null, null, 
        "Penicilina", "Polen", "Ibuprofen", "Aspirina", "Lactoza", "Praf", "Par de animale",
        "Capsuni", "Nuci", "Penicilina, Aspirina"
    };
    private static final String[] SIMPTOME = {
        "Dureri de cap frecvente, oboseala",
        "Palpitatii, lipsa de aer la efort",
        "Ameteli si probleme de echilibru",
        "Febra 38.5, tuse seacă",
        "Control periodic tensiune arteriala",
        "Control de rutina",
        "Durere abdominala persistenta",
        "Dureri lombare cronice",
        "Insomnie si anxietate",
        "Tuse persistenta de 2 saptamani",
        "Eruptie cutanata, mancarime",
        "Probleme cu vederea, ochi obositi",
        "Greata si voma dimineata",
        "Durere in piept la efort",
        "Senzatie de oboseala cronica",
        "Migrene puternice saptamanale",
        "Control diabet zaharat",
        "Dureri articulare mana dreapta"
    };

    private static final String[] DIAGNOSTICE = {
        "Cefalee de tensiune cronica",
        "Tahicardie sinusala functionala",
        "Vertij periferic - suspiciune VPPB",
        "Rinofaringita virala acuta",
        "Hipertensiune arteriala gradul I",
        "Gastrita cronica",
        "Lombosciatica",
        "Sindrom anxios usor",
        "Bronsita acuta",
        "Dermatita atopica",
        "Astenopie acomodativa",
        "Reflux gastroesofagian",
        "Angina pectorala stabila",
        "Sindrom de oboseala cronica",
        "Migrena cu aura",
        "Diabet zaharat tip II - control",
        "Artralgie - probabil suprasolicitare"
    };

    private static final String[] TRATAMENTE = {
        "Analgezice la nevoie, reducerea stresului, somn regulat 7-8h",
        "Betablocant (Metoprolol 25mg/zi), evitarea cafelei si stresului",
        "Manevre de repozitionare Epley, Betahistina 16mg x2/zi",
        "Paracetamol 500mg la febra, Vitamina C, hidratare abundenta",
        "Ramipril 5mg/zi, dieta hiposodata, exercitiu fizic moderat",
        "Omeprazol 20mg/zi, dieta usoara, mese mici si dese",
        "Antiinflamatoare, fizioterapie 10 sedinte",
        "Magneziu + B6, exercitii relaxare, evaluare la 4 saptamani",
        "Sirop expectorant, hidratare, repaus 5 zile",
        "Crema cu hidrocortizon 1%, hidratare piele",
        "Picaturi lacrimi artificiale, pauze regulate de la ecran"
    };

    private static final String[] MEDICAMENTE = {
        "Ibuprofen 400mg - 1 comprimat la nevoie (max 3/zi)",
        "Paracetamol 500mg - alternativ la Ibuprofen",
        "Magneziu 375mg - 1 comprimat/seara",
        "Metoprolol 25mg - 1 comprimat dimineata",
        "Betahistina 16mg - 1 comprimat x2/zi",
        "Ramipril 5mg - 1 comprimat dimineata",
        "Omeprazol 20mg - 1 comprimat dimineata, inainte de masa",
        "Diclofenac 50mg - 1 comprimat la nevoie",
        "Vitamina D3 2000UI - 1 comprimat/zi",
        "Sirop Sinecod - 1 lingurita x3/zi"
    };

    private static final String[] RATING_COMENTARII = {
        "Doctor foarte profesionist, recomand!",
        "Consultatie buna, mi-a explicat totul detaliat.",
        "Punctual si atent la detalii.",
        "Tratamentul recomandat a fost eficient.",
        "Foarte multumit/a de consultatie.",
        "Doctor amabil si rabdator.",
        "Recomandari clare, tratament functional.",
        "Apreciez profesionalismul.",
        "A oferit timp suficient pentru intrebari.",
        "Echipa primitoare, consultatie ok.",
        null, null, null  
    };
    private static final String[][] MESAJE = {
        {"Intrebare despre tratament",
         "Buna ziua, doctore. Pot sa va intreb daca pot lua si Paracetamol pe langa tratamentul prescris?"},
        {"Intrebare despre doza",
         "Buna, am o intrebare despre doza. Mai exact, cate comprimate sa iau pe zi?"},
        {"Rezultate analize",
         "Mi-au sosit rezultatele analizelor. Cum procedam mai departe?"},
        {"Modificare ora programare",
         "Va rog sa imi confirmati daca pot muta programarea cu o ora mai tarziu."},
        {"Confirmare prezenta",
         "Confirm ca voi fi prezent/a la programarea de saptamana viitoare. Multumesc."},
        {"Multumesc pentru consultatie",
         "Multumesc pentru consultatia de ieri, ma simt mult mai bine."},
        {"Reactie adversa medicament",
         "Am observat o reactie cutanata dupa ce am luat medicamentul prescris. Ce sa fac?"},
        {"Programare urmare control",
         "Buna ziua, as dori sa imi programez un control de urmare. Cand aveti disponibilitate?"}
    };

    @Override
    public void run(String... args) {
        if (doctorRepository.count() > 0) return;

        log.info("Loading initial dataset...");
        createAdminUser("admin@clinic.com", "admin123");
        List<Doctor> doctors = new ArrayList<>();
        doctors.add(createDoctor("doctor1@clinic.com", "doctor123", "Alexandru", "Popescu",  "Medicina Generala", "0721111001"));
        doctors.add(createDoctor("doctor2@clinic.com", "doctor123", "Maria",     "Ionescu",  "Cardiologie",       "0721111002"));
        doctors.add(createDoctor("doctor3@clinic.com", "doctor123", "Andrei",    "Radu",     "Neurologie",        "0721111003"));
        doctors.add(createDoctor("doctor4@clinic.com", "doctor123", "Elena",     "Moldovan", "Pediatrie",         "0721111004"));
        doctors.add(createDoctor("doctor5@clinic.com", "doctor123", "Mihai",     "Dumitrescu", "Dermatologie",      "0721111005"));
        doctors.add(createDoctor("doctor6@clinic.com", "doctor123", "Cristina",  "Stoica",     "Ginecologie",       "0721111006"));
        doctors.add(createDoctor("doctor7@clinic.com", "doctor123", "Razvan",    "Marin",      "Ortopedie",         "0721111007"));
        doctors.add(createDoctor("doctor8@clinic.com", "doctor123", "Diana",     "Tanase",     "Oftalmologie",      "0721111008"));
        doctors.add(createDoctor("doctor9@clinic.com", "doctor123", "Bogdan",    "Munteanu",   "ORL",               "0721111009"));
        doctors.add(createDoctor("doctor10@clinic.com","doctor123", "Roxana",    "Diaconu",    "Endocrinologie",    "0721111010"));
        doctors.add(createDoctor("doctor11@clinic.com","doctor123", "Catalin",   "Stan",       "Gastroenterologie", "0721111011"));
        doctors.add(createDoctor("doctor12@clinic.com","doctor123", "Andreea",   "Florea",     "Psihiatrie",        "0721111012"));

        for (Doctor d : doctors) seedSchedules(d);
        List<Patient> patients = new ArrayList<>();
        patients.add(createPatient("mihai@email.com",   "patient123", "Mihai",   "Constantin", "0744221001", "M", LocalDate.of(1988, 3, 14), "A+",  "Penicilina",    "Str. Florilor 12, Bucuresti"));
        patients.add(createPatient("ana@email.com",     "patient123", "Ana",     "Dumitrescu", "0744221002", "F", LocalDate.of(1995, 7, 22), "0+",  null,            "Bd. Unirii 45, Cluj-Napoca"));
        patients.add(createPatient("george@email.com",  "patient123", "George",  "Popa",       "0744221003", "M", LocalDate.of(1975, 11, 5), "B-",  "Ibuprofen",     "Str. Victoriei 8, Timisoara"));
        patients.add(createPatient("raluca@email.com",  "patient123", "Raluca",  "Niculescu",  "0744221004", "F", LocalDate.of(2001, 2, 18), "AB+", null,            "Str. Mihai Eminescu 3, Iasi"));

        for (int i = 0; i < 60; i++) {
            patients.add(generatePatient(i));
        }
        List<Appointment> allAppointments = generateAppointments(doctors, patients);

        int pastCompleted = 0, futureConfirmed = 0, cancelled = 0;
        for (Appointment a : allAppointments) {
            switch (a.getStatus()) {
                case "COMPLETED" -> pastCompleted++;
                case "CONFIRMED", "PENDING" -> futureConfirmed++;
                case "CANCELLED" -> cancelled++;
            }
        }
        for (Appointment a : allAppointments) {
            if ("COMPLETED".equals(a.getStatus()) && RNG.nextDouble() < 0.7) {
                createMedicalRecord(a);
                if (RNG.nextDouble() < 0.8) {
                    createPrescription(a);
                }
            }
        }
        for (Appointment a : allAppointments) {
            if (!"CANCELLED".equals(a.getStatus()) && RNG.nextDouble() < 0.85) {
                String status = "COMPLETED".equals(a.getStatus()) ? "PAID" : (RNG.nextDouble() < 0.6 ? "PAID" : "PENDING");
                createInvoice(a, status);
            }
        }
        for (Appointment a : allAppointments) {
            if ("COMPLETED".equals(a.getStatus()) && RNG.nextDouble() < 0.5) {
                createRating(a);
            }
        }
        generateMessages(doctors, patients);
        for (int i = 0; i < 5; i++) {
            createStandaloneInvoice(patients.get(i));
        }

        log.info("Initial dataset loaded: {} doctors, {} patients, {} appointments ({} completed, {} upcoming, {} cancelled)",
                doctors.size(), patients.size(), allAppointments.size(),
                pastCompleted, futureConfirmed, cancelled);
    }

    private Patient generatePatient(int idx) {
        boolean isMale = RNG.nextBoolean();
        String firstName = pick(isMale ? PRENUME_M : PRENUME_F);
        String lastName  = pick(NUME);
        String email     = removeDiacritics(firstName.toLowerCase()) + "." +
                           removeDiacritics(lastName.toLowerCase()) +
                           (idx + 10) + "@email.com";
        String phone     = "07" + (40 + RNG.nextInt(60)) + String.format("%06d", RNG.nextInt(1000000));
        LocalDate dob    = LocalDate.of(1940 + RNG.nextInt(70),
                                        1 + RNG.nextInt(12),
                                        1 + RNG.nextInt(28));
        String blood     = pick(GRUPE_SANGE);
        String allergies = pick(ALERGII);
        String address   = pick(STRAZI) + " " + (1 + RNG.nextInt(200)) + ", " + pick(ORASE);

        return createPatient(email, "patient123", firstName, lastName, phone,
                isMale ? "M" : "F", dob, blood, allergies, address);
    }

    private List<Appointment> generateAppointments(List<Doctor> doctors, List<Patient> patients) {
        List<Appointment> result = new ArrayList<>();
        for (int i = 0; i < 220; i++) {
            Patient p = pick(patients);
            Doctor d  = pick(doctors);
            int daysAgo  = 1 + RNG.nextInt(360);
            int hour     = 8 + RNG.nextInt(9);  
            LocalDateTime when = LocalDateTime.now().minusDays(daysAgo).withHour(hour).withMinute(0).withSecond(0).withNano(0);
            double r = RNG.nextDouble();
            String status;
            if (r < 0.75)      status = "COMPLETED";
            else if (r < 0.90) status = "CANCELLED";
            else               continue;

            String notes = pick(SIMPTOME);
            Appointment a = createAppointment(p, d, when, status, notes);
            result.add(a);
        }
        for (int i = 0; i < 90; i++) {
            Patient p = pick(patients);
            Doctor d  = pick(doctors);
            int daysAhead = 1 + RNG.nextInt(60);
            int hour      = 8 + RNG.nextInt(9);
            LocalDateTime when = LocalDateTime.now().plusDays(daysAhead).withHour(hour).withMinute(0).withSecond(0).withNano(0);
            if (appointmentRepository.countConflicts(d, when) > 0) continue;
            double r = RNG.nextDouble();
            String status;
            if (r < 0.5)      status = "CONFIRMED";
            else if (r < 0.8) status = "PENDING";
            else              status = "PENDING_PAYMENT";

            Appointment a = createAppointment(p, d, when, status, pick(SIMPTOME));
            result.add(a);
        }

        return result;
    }

    private void createMedicalRecord(Appointment a) {
        MedicalRecord r = new MedicalRecord();
        r.setPatient(a.getPatient());
        r.setDoctor(a.getDoctor());
        r.setTitle("Consultatie - " + a.getDoctor().getSpecialty());
        r.setSymptoms(a.getNotes());
        r.setDiagnosis(pick(DIAGNOSTICE));
        r.setTreatment(pick(TRATAMENTE));
        r.setNotes("Pacient cooperant. Reevaluare la " + (2 + RNG.nextInt(6)) + " saptamani.");
        medicalRecordRepository.save(r);
    }

    private void createPrescription(Appointment a) {
        Prescription p = new Prescription();
        p.setPatient(a.getPatient());
        p.setDoctor(a.getDoctor());
        p.setAppointment(a);
        p.setDiagnosis(pick(DIAGNOSTICE));
        int nrMeds = 1 + RNG.nextInt(3);
        StringBuilder meds = new StringBuilder();
        for (int i = 0; i < nrMeds; i++) {
            if (i > 0) meds.append("\n");
            meds.append(pick(MEDICAMENTE));
        }
        p.setMedications(meds.toString());
        p.setInstructions("Respectati dozele. La reactii adverse contactati medicul.");
        prescriptionRepository.save(p);
    }

    private void createInvoice(Appointment a, String status) {
        Invoice inv = new Invoice();
        inv.setPatient(a.getPatient());
        inv.setAppointment(a);
        BigDecimal amount = new BigDecimal(100 + RNG.nextInt(200) + "." + (RNG.nextInt(10)) + "0");
        inv.setAmount(amount);
        inv.setDescription("Consultatie " + a.getDoctor().getSpecialty().toLowerCase());
        inv.setStatus(status);
        if ("PAID".equals(status)) {
            inv.setPaidAt(a.getAppointmentDate().plusHours(1));
        }
        invoiceRepository.save(inv);
    }

    private void createStandaloneInvoice(Patient p) {
        Invoice inv = new Invoice();
        inv.setPatient(p);
        inv.setAmount(new BigDecimal(50 + RNG.nextInt(150)));
        inv.setDescription("Analize laborator");
        inv.setStatus(RNG.nextBoolean() ? "PAID" : "PENDING");
        if ("PAID".equals(inv.getStatus())) inv.setPaidAt(LocalDateTime.now().minusDays(RNG.nextInt(60)));
        invoiceRepository.save(inv);
    }

    private void createRating(Appointment a) {
        Rating r = new Rating();
        r.setAppointment(a);
        r.setDoctor(a.getDoctor());
        r.setPatient(a.getPatient());
        double rnd = RNG.nextDouble();
        int stars;
        if (rnd < 0.6)      stars = 5;
        else if (rnd < 0.85) stars = 4;
        else if (rnd < 0.95) stars = 3;
        else                stars = 1 + RNG.nextInt(2);
        r.setStars(stars);
        r.setComment(pick(RATING_COMENTARII));
        ratingRepository.save(r);
    }

    private void generateMessages(List<Doctor> doctors, List<Patient> patients) {
        for (int i = 0; i < 40; i++) {
            Patient p = pick(patients);
            Doctor d  = pick(doctors);
            String[] pereche = pick(MESAJE);
            Message m = new Message();
            m.setSender(p.getUser());
            m.setReceiver(d.getUser());
            m.setSubject(pereche[0]);
            m.setBody(pereche[1]);
            m.setRead(RNG.nextBoolean());
            messageRepository.save(m);
            if (RNG.nextDouble() < 0.4) {
                Message reply = new Message();
                reply.setSender(d.getUser());
                reply.setReceiver(p.getUser());
                reply.setSubject("Re: " + m.getSubject());
                reply.setBody("Buna ziua, va recomand sa urmati tratamentul prescris. " +
                              "Daca apar simptome noi, programati o consultatie.");
                reply.setRead(false);
                reply.setParent(m);
                messageRepository.save(reply);
            }
        }
    }

    private <T> T pick(T[] arr) {
        return arr[RNG.nextInt(arr.length)];
    }

    private <T> T pick(List<T> list) {
        return list.get(RNG.nextInt(list.size()));
    }

    private String removeDiacritics(String s) {
        return s.replace("ă","a").replace("â","a").replace("î","i")
                .replace("ș","s").replace("ț","t").replace("Ă","A").replace("Â","A")
                .replace("Î","I").replace("Ș","S").replace("Ț","T");
    }

    private void createAdminUser(String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) return;

        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }

    private void createUser(String email, String password, String role,
                            String firstName, String lastName, String phone, String specialty) {
        if (userRepository.findByEmail(email).isPresent()) return;
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setPhone(phone);
        req.setSpecialty(specialty);
        userService.register(req);
    }

    private Doctor createDoctor(String email, String password, String firstName,
                                String lastName, String specialty, String phone) {
        createUser(email, password, "DOCTOR", firstName, lastName, phone, specialty);
        var user = userRepository.findByEmail(email).orElseThrow();
        return doctorRepository.findByUser(user).orElseThrow();
    }

    private Patient createPatient(String email, String password, String firstName, String lastName,
                                  String phone, String gender, LocalDate dob,
                                  String bloodType, String allergies, String address) {
        createUser(email, password, "PATIENT", firstName, lastName, phone, null);
        var user = userRepository.findByEmail(email).orElseThrow();
        Patient p = patientRepository.findByUser(user).orElseThrow();
        p.setGender(gender);
        p.setDateOfBirth(dob);
        p.setBloodType(bloodType);
        p.setAllergies(allergies);
        p.setAddress(address);
        return patientRepository.save(p);
    }

    private Appointment createAppointment(Patient patient, Doctor doctor,
                                          LocalDateTime date, String status, String notes) {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(date);
        a.setStatus(status);
        a.setNotes(notes);
        a.setPriority(inferPriority(notes));
        if (!"CANCELLED".equals(status) && !"PENDING_PAYMENT".equals(status)) {
            a.setBookingFeePaid(true);
        }
        return appointmentRepository.save(a);
    }

    private String inferPriority(String notes) {
        if (notes == null || notes.isBlank()) return "LOW";
        String n = notes.toLowerCase();
        if (n.contains("piept") || n.contains("palpitat")) return "HIGH";
        if (n.contains("durere") || n.contains("febra") || n.contains("tuse") ||
            n.contains("ameteal") || n.contains("vertij")) return "MEDIUM";
        return "LOW";
    }

    private void seedSchedules(Doctor doctor) {
        for (int dow = 1; dow <= 5; dow++) {
            if (scheduleRepository.findByDoctorAndDayOfWeek(doctor, dow).isPresent()) continue;
            DoctorSchedule sched = new DoctorSchedule();
            sched.setDoctor(doctor);
            sched.setDayOfWeek(dow);
            sched.setStartHour(9);
            sched.setEndHour(17);
            sched.setAvailable(true);
            scheduleRepository.save(sched);
        }
    }
}
