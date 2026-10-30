package com.trix.workshop.config;

import com.trix.workshop.certificate.PdfCertificateGenerator;
import com.trix.workshop.dto.CertificateDto;
import com.trix.workshop.entity.*;
import com.trix.workshop.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Component
@Order(2)
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final CertificateRepository certificateRepository;
    private final PasswordEncoder passwordEncoder;
    private final PdfCertificateGenerator pdfGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAutoMigrator databaseAutoMigrator;

    public DataInitializer(UserRepository userRepository,
                           WorkshopRepository workshopRepository,
                           RegistrationRepository registrationRepository,
                           AttendanceRepository attendanceRepository,
                           CertificateRepository certificateRepository,
                           PasswordEncoder passwordEncoder,
                           PdfCertificateGenerator pdfGenerator,
                           JdbcTemplate jdbcTemplate,
                           DatabaseAutoMigrator databaseAutoMigrator) {
        this.userRepository = userRepository;
        this.workshopRepository = workshopRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.certificateRepository = certificateRepository;
        this.passwordEncoder = passwordEncoder;
        this.pdfGenerator = pdfGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseAutoMigrator = databaseAutoMigrator;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Checking initial demo accounts and workshop inputs...");
            ensureDemoUsers();
            ensureSampleWorkshopsAndActivity();
            databaseAutoMigrator.syncPostgresSequences();
            log.info("Demo data check complete.");
        } catch (Exception e) {
            log.error("DataInitializer error: ", e);
        }
    }

    private void ensureDemoUsers() {
        // Admin Accounts
        createUserIfMissing("admin@trix.edu", "Dr. Anand Varma", "Admin@123", "ADMIN",
                "+91 94471 23456", "Computer Science and Engineering", "College of Engineering Trivandrum");
        createUserIfMissing("admin@trix.com", "Dr. Anand Varma", "admin123", "ADMIN",
                "+91 94471 23456", "Computer Science and Engineering", "College of Engineering Trivandrum");

        // Student / Participant Demo Accounts
        createUserIfMissing("alex@gmail.com", "Alex Johnson", "student123", "PARTICIPANT",
                "+91 98460 99887", "Computer Science and Engineering", "College of Engineering Trivandrum");
        createUserIfMissing("rahul.nair@trix.edu", "Rahul Nair", "Rahul@123", "PARTICIPANT",
                "+91 98460 11223", "Computer Science and Engineering", "College of Engineering Trivandrum");
        createUserIfMissing("sneha.menon@trix.edu", "Sneha Menon", "Sneha@123", "PARTICIPANT",
                "+91 97455 33445", "Computer Science and Engineering", "Model Engineering College Kochi");
        createUserIfMissing("arjun.das@trix.edu", "Arjun Das", "Arjun@123", "PARTICIPANT",
                "+91 94960 55667", "Computer Science and Engineering", "TKM College of Engineering Kollam");
        createUserIfMissing("anjali.kumar@trix.edu", "Anjali Kumar", "Anjali@123", "PARTICIPANT",
                "+91 96330 77889", "Electronics and Communication", "Govt Engineering College Barton Hill");
        createUserIfMissing("fathima.zahra@trix.edu", "Fathima Zahra", "Fathima@123", "PARTICIPANT",
                "+91 95260 99001", "Artificial Intelligence & Data Science", "Rajagiri School of Engineering");
    }

    private User createUserIfMissing(String email, String name, String password, String role,
                                     String phone, String dept, String inst) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        u.setPhone(phone);
        u.setDepartment(dept);
        u.setInstitution(inst);
        u.setAccountStatus("ACTIVE");
        try {
            return userRepository.save(u);
        } catch (Exception e) {
            log.warn("Could not save user {}: {}", email, e.getMessage());
            return u;
        }
    }

    private void ensureSampleWorkshopsAndActivity() {
        Integer workshopCount = 0;
        try {
            workshopCount = jdbcTemplate.queryForObject("SELECT count(*) FROM workshops", Integer.class);
        } catch (Exception ignored) {}

        if (workshopCount != null && workshopCount > 0) {
            log.info("Workshops already exist (count: {}). Skipping creation.", workshopCount);
            return;
        }

        log.info("Seeding realistic academic workshops and demo activities...");
        User admin = userRepository.findByEmail("admin@trix.edu")
                .or(() -> userRepository.findByEmail("admin@trix.com"))
                .orElse(null);
        Long adminId = admin != null ? admin.getUserId() : null;

        User rahul = userRepository.findByEmail("rahul.nair@trix.edu")
                .or(() -> userRepository.findByEmail("alex@gmail.com"))
                .orElse(null);
        User sneha = userRepository.findByEmail("sneha.menon@trix.edu").orElse(null);
        User arjun = userRepository.findByEmail("arjun.das@trix.edu").orElse(null);
        User anjali = userRepository.findByEmail("anjali.kumar@trix.edu").orElse(null);
        User fathima = userRepository.findByEmail("fathima.zahra@trix.edu").orElse(null);

        LocalDate today = LocalDate.now();

        // Workshop 1: Upcoming Cloud Computing
        Workshop w1 = new Workshop(null,
                "Mastering Cloud-Native Microservices with Spring Boot & Docker",
                "Comprehensive hands-on workshop covering RESTful architecture, containerization with Docker, API Gateway routing, distributed configuration, and resilient microservice communication patterns.",
                "Cloud Computing",
                "Prof. Lakshmi Priya",
                adminId,
                today.plusDays(10),
                LocalTime.of(9, 30),
                LocalTime.of(16, 30),
                "Turing Auditorium, Main Block",
                30,
                today.plusDays(8),
                "UPCOMING",
                null, null);
        w1 = workshopRepository.save(w1);

        // Workshop 2: Upcoming AI
        Workshop w2 = new Workshop(null,
                "Applied Deep Learning & Computer Vision using PyTorch",
                "Explore convolutional neural networks, transfer learning with ResNet, object detection pipelines, and deploying vision models using ONNX and FastAPI.",
                "AI & Machine Learning",
                "Dr. Harikrishnan R",
                adminId,
                today.plusDays(15),
                LocalTime.of(10, 0),
                LocalTime.of(16, 0),
                "Ada Lovelace Computing Lab",
                25,
                today.plusDays(12),
                "UPCOMING",
                null, null);
        w2 = workshopRepository.save(w2);

        // Workshop 3: Ongoing Cyber Security
        Workshop w3 = new Workshop(null,
                "Defensive Cyber Security & Ethical Hacking Essentials",
                "Practical laboratory exercises on OWASP Top 10 vulnerabilities, network packet analysis with Wireshark, secure authentication patterns, and penetration testing fundamentals.",
                "Cyber Security",
                "Er. Sanjay Varma",
                adminId,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Seminar Hall B, CSE Block",
                40,
                today.minusDays(1),
                "ONGOING",
                null, null);
        w3 = workshopRepository.save(w3);

        // Workshop 4: Completed Web Development (For certificate demos)
        Workshop w4 = new Workshop(null,
                "Hands-on Full-Stack Web Development with React & Java",
                "End-to-end full stack architecture building responsive single-page applications, integrating Spring Boot REST APIs, JDBC database access, and secure JWT session workflows.",
                "Web Technologies",
                "Prof. Lakshmi Priya",
                adminId,
                today.minusDays(5),
                LocalTime.of(9, 30),
                LocalTime.of(16, 30),
                "Central Computing Centre, Lab 3",
                35,
                today.minusDays(7),
                "COMPLETED",
                null, null);
        w4 = workshopRepository.save(w4);

        // Workshop 5: Completed IoT
        Workshop w5 = new Workshop(null,
                "Industrial IoT & Edge Intelligence with Raspberry Pi",
                "Interfacing digital sensors, MQTT communication protocols, cloud telemetry ingestion, and edge intelligence inference on embedded single-board computers.",
                "IoT & Embedded Systems",
                "Dr. George Mathew",
                adminId,
                today.minusDays(12),
                LocalTime.of(10, 0),
                LocalTime.of(16, 30),
                "Embedded Systems Research Lab",
                20,
                today.minusDays(14),
                "COMPLETED",
                null, null);
        w5 = workshopRepository.save(w5);

        // Seed Registrations & Attendance for Completed Workshop 4
        if (w4.getWorkshopId() != null) {
            try {
                if (rahul != null && rahul.getUserId() != null) {
                    Registration r1 = registrationRepository.save(new Registration(null, rahul.getUserId(), w4.getWorkshopId(), null, "APPROVED", "Confirmed seat", null, null));
                    attendanceRepository.saveOrUpdate(r1.getRegistrationId(), w4.getWorkshopId(), rahul.getUserId(), "PRESENT", "Dr. Anand Varma");
                    Certificate c1 = certificateRepository.save(new Certificate(null, r1.getRegistrationId(), rahul.getUserId(), w4.getWorkshopId(),
                            "TRIX-CERT-2026-KTU001", "V-TRIX01", "ISSUED", true, today.minusDays(4), null, null, null));
                    generatePdfQuietly(c1.getCertificateId());
                }

                if (sneha != null && sneha.getUserId() != null) {
                    Registration r2 = registrationRepository.save(new Registration(null, sneha.getUserId(), w4.getWorkshopId(), null, "APPROVED", "Early registration", null, null));
                    attendanceRepository.saveOrUpdate(r2.getRegistrationId(), w4.getWorkshopId(), sneha.getUserId(), "PRESENT", "Dr. Anand Varma");
                    Certificate c2 = certificateRepository.save(new Certificate(null, r2.getRegistrationId(), sneha.getUserId(), w4.getWorkshopId(),
                            "TRIX-CERT-2026-KTU002", "V-TRIX02", "GENERATED", true, today.minusDays(4), null, null, null));
                    generatePdfQuietly(c2.getCertificateId());
                }

                if (arjun != null && arjun.getUserId() != null) {
                    Registration r3 = registrationRepository.save(new Registration(null, arjun.getUserId(), w4.getWorkshopId(), null, "APPROVED", "Confirmed seat", null, null));
                    attendanceRepository.saveOrUpdate(r3.getRegistrationId(), w4.getWorkshopId(), arjun.getUserId(), "PRESENT", "Dr. Anand Varma");
                }

                if (anjali != null && anjali.getUserId() != null) {
                    Registration r4 = registrationRepository.save(new Registration(null, anjali.getUserId(), w4.getWorkshopId(), null, "APPROVED", "Regular attendee", null, null));
                    attendanceRepository.saveOrUpdate(r4.getRegistrationId(), w4.getWorkshopId(), anjali.getUserId(), "ABSENT", "Dr. Anand Varma");
                }

                if (fathima != null && fathima.getUserId() != null) {
                    Registration r5 = registrationRepository.save(new Registration(null, fathima.getUserId(), w4.getWorkshopId(), null, "APPROVED", "Registered online", null, null));
                    attendanceRepository.saveOrUpdate(r5.getRegistrationId(), w4.getWorkshopId(), fathima.getUserId(), "NOT_RECORDED", "SYSTEM_INIT");
                }
            } catch (Exception e) {
                log.warn("Workshop 4 registrations note: {}", e.getMessage());
            }
        }

        // Seed Registrations for Ongoing Workshop 3
        if (w3.getWorkshopId() != null) {
            try {
                if (rahul != null && rahul.getUserId() != null) {
                    Registration r6 = registrationRepository.save(new Registration(null, rahul.getUserId(), w3.getWorkshopId(), null, "APPROVED", "Enrolled", null, null));
                    attendanceRepository.saveOrUpdate(r6.getRegistrationId(), w3.getWorkshopId(), rahul.getUserId(), "NOT_RECORDED", "SYSTEM_INIT");
                }
                if (sneha != null && sneha.getUserId() != null) {
                    Registration r7 = registrationRepository.save(new Registration(null, sneha.getUserId(), w3.getWorkshopId(), null, "APPROVED", "Enrolled", null, null));
                    attendanceRepository.saveOrUpdate(r7.getRegistrationId(), w3.getWorkshopId(), sneha.getUserId(), "NOT_RECORDED", "SYSTEM_INIT");
                }
                if (arjun != null && arjun.getUserId() != null) {
                    Registration r8 = registrationRepository.save(new Registration(null, arjun.getUserId(), w3.getWorkshopId(), null, "APPROVED", "Enrolled", null, null));
                    attendanceRepository.saveOrUpdate(r8.getRegistrationId(), w3.getWorkshopId(), arjun.getUserId(), "NOT_RECORDED", "SYSTEM_INIT");
                }
            } catch (Exception e) {
                log.warn("Workshop 3 registrations note: {}", e.getMessage());
            }
        }

        // Seed Registrations for Upcoming Workshop 1
        if (w1.getWorkshopId() != null) {
            try {
                if (rahul != null && rahul.getUserId() != null) {
                    registrationRepository.save(new Registration(null, rahul.getUserId(), w1.getWorkshopId(), null, "APPROVED", "Seat reserved", null, null));
                }
                if (anjali != null && anjali.getUserId() != null) {
                    registrationRepository.save(new Registration(null, anjali.getUserId(), w1.getWorkshopId(), null, "APPROVED", "Seat reserved", null, null));
                }
            } catch (Exception e) {
                log.warn("Workshop 1 registrations note: {}", e.getMessage());
            }
        }
    }

    private void generatePdfQuietly(Long certId) {
        if (certId == null) return;
        try {
            CertificateDto dto = certificateRepository.findDtoById(certId).orElse(null);
            if (dto != null) {
                pdfGenerator.generateCertificatePdf(dto);
            }
        } catch (Exception e) {
            log.warn("Could not pre-render certificate PDF: {}", e.getMessage());
        }
    }
}
