package com.eventnest.config;

import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.college.CollegeVerificationStatus;
import com.eventnest.common.util.SlugUtil;
import com.eventnest.organizer.Organizer;
import com.eventnest.organizer.OrganizerRepository;
import com.eventnest.organizer.OrganizerVerificationStatus;
import com.eventnest.student.StudentProfile;
import com.eventnest.student.StudentProfileRepository;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import com.eventnest.user.UserRole;
import com.eventnest.user.UserStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development. Runs only on the {@code dev} profile AND only
 * when {@code app.seed.enabled=true}, so it can never execute on a deployed
 * instance even if the flag is misconfigured.
 *
 * <p><b>Why this is Java and not SQL:</b> account rows need a BCrypt hash, and a
 * hash must be produced by Spring's encoder - hardcoding one in a migration file
 * would put a password hash in version control and bake in whatever strength the
 * author happened to use. Everything here goes through the real repositories, so
 * the seed doubles as an integration test of the write paths.
 *
 * <p>Every insert is idempotent (checked before writing), so restarting the app
 * does not duplicate data.
 */
@Profile("dev")
@RequiredArgsConstructor
@Component
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final OrganizerRepository organizerRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            log.info("Seed data disabled (app.seed.enabled=false)");
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Seed data skipped - database already has users");
            return;
        }

        log.info("Seeding EventNest demo data...");
        var colleges = seedColleges();
        seedAdmin();
        seedOrganizers(colleges);
        seedStudents(colleges);
        log.info("Seed complete. Logins: admin@eventnest.dev / Admin@12345, "
                + "rahul@xyz.edu.in / Organizer@123, abhishek@student.edu.in / Student@123");
    }

    private List<College> seedColleges() {
        record Spec(String name, String about, String city, String district, String state,
                    double lat, double lng, boolean verified) {
        }
        List<Spec> specs = List.of(
                new Spec("Walchand College of Engineering", "Est. 1962, autonomous institute in Sangli.",
                        "Sangli", "Sangli", "Maharashtra", 16.8524, 74.5815, true),
                new Spec("Shivaji University", "One of India's oldest universities, Kolhapur.",
                        "Kolhapur", "Kolhapur", "Maharashtra", 16.7050, 74.2433, true),
                new Spec("Sanjay Ghodawat Group College", "Private engineering college, Sanaswadi.",
                        "Pune", "Pune", "Maharashtra", 18.5204, 73.8567, true),
                new Spec("MIT Campus Pune", "Institute of design, technology and management.",
                        "Pune", "Pune", "Maharashtra", 18.5298, 73.8475, true),
                new Spec("COEP Technological University", "Government engineering college, Pune.",
                        "Pune", "Pune", "Maharashtra", 18.5308, 73.8500, true),
                new Spec("Dr. D. Y. Patil College of Engineering", "Navi Mumbai campus.",
                        "Navi Mumbai", "Thane", "Maharashtra", 19.0330, 73.0297, true),
                new Spec("R. V. College of Engineering", "Bengaluru engineering college.",
                        "Bengaluru", "Bengaluru Urban", "Karnataka", 12.9698, 77.5946, true),
                new Spec("Jain University Bangalore", "Private university, global campus.",
                        "Bengaluru", "Bengaluru Urban", "Karnataka", 12.9010, 77.5990, true));

        return specs.stream().map(spec -> {
            College college = new College();
            college.setName(spec.name());
            college.setSlug(SlugUtil.slugify(spec.name()));
            college.setAbout(spec.about());
            college.setCity(spec.city());
            college.setDistrict(spec.district());
            college.setState(spec.state());
            college.setCountry("India");
            college.setLatitude(BigDecimal.valueOf(spec.lat()));
            college.setLongitude(BigDecimal.valueOf(spec.lng()));
            college.setVerificationStatus(spec.verified()
                    ? CollegeVerificationStatus.VERIFIED : CollegeVerificationStatus.PENDING);
            return collegeRepository.save(college);
        }).toList();
    }

    private void seedAdmin() {
        User admin = new User();
        admin.setEmail(properties.seed().adminEmail());
        admin.setFullName("Platform Admin");
        admin.setPasswordHash(passwordEncoder.encode(properties.seed().adminPassword()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEmailVerified(true);
        userRepository.save(admin);
    }

    /**
     * Two organizers on purpose: one APPROVED so the create-event flow is
     * immediately demoable, one PENDING so the "awaiting approval" state can be
     * seen without an admin action. That contrast is the whole trust model
     * (D19) made visible.
     */
    private void seedOrganizers(List<College> colleges) {
        record Spec(String name, String email, String password, int collegeIndex,
                    String designation, String department, OrganizerVerificationStatus status) {
        }
        List<Spec> specs = List.of(
                new Spec("Rahul Patil", properties.seed().organizerEmail(),
                        properties.seed().organizerPassword(), 1,
                        "Event Coordinator", "Computer Engineering",
                        OrganizerVerificationStatus.APPROVED),
                new Spec("Priya Deshmukh", "priya@example.edu.in", "Organizer@123", 2,
                        "Student Coordinator", "Information Technology",
                        OrganizerVerificationStatus.PENDING),
                new Spec("Aarav Sharma", "aarav@example.edu.in", "Organizer@123", 6,
                        "Technical Lead", "Electronics & Communication",
                        OrganizerVerificationStatus.APPROVED));

        for (Spec spec : specs) {
            User user = new User();
            user.setEmail(spec.email());
            user.setFullName(spec.name());
            user.setPasswordHash(passwordEncoder.encode(spec.password()));
            user.setRole(UserRole.ORGANIZER);
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);

            Organizer organizer = new Organizer();
            organizer.setUserId(user.getId());
            organizer.setCollege(colleges.get(spec.collegeIndex()));
            organizer.setDesignation(spec.designation());
            organizer.setDepartment(spec.department());
            organizer.setVerificationStatus(spec.status());
            if (spec.status() == OrganizerVerificationStatus.APPROVED) {
                // Satisfies ck_organizers_approval_audit: an APPROVED organizer must
                // record who approved it and when.
                organizer.setVerifiedAt(java.time.Instant.now());
                organizer.setVerifiedBy(findAdminId());
            }
            organizerRepository.save(organizer);
        }
    }

    private Long findAdminId() {
        return userRepository.findByEmail(properties.seed().adminEmail())
                .map(User::getId)
                .orElse(null);
    }

    private void seedStudents(List<College> colleges) {
        record Spec(String name, String email, int collegeIndex, String course,
                    int year, String city, String state) {
        }
        List<Spec> specs = List.of(
                new Spec("Abhishek Mane", properties.seed().studentEmail(), 1,
                        "B.Tech CSE", 2027, "Kolhapur", "Maharashtra"),
                new Spec("Sneha Kulkarni", "sneha@example.edu.in", 1,
                        "B.Tech IT", 2027, "Kolhapur", "Maharashtra"),
                new Spec("Aditya Joshi", "aditya@example.edu.in", 0,
                        "B.E. Mechanical", 2026, "Sangli", "Maharashtra"),
                new Spec("Meera Nair", "meera@example.edu.in", 6,
                        "B.Tech ECE", 2028, "Bengaluru", "Karnataka"),
                new Spec("Karan Mehta", "karan@example.edu.in", 3,
                        "B.Tech Computer Science", 2027, "Pune", "Maharashtra"));

        for (Spec spec : specs) {
            User user = new User();
            user.setEmail(spec.email());
            user.setFullName(spec.name());
            user.setPasswordHash(passwordEncoder.encode(properties.seed().studentPassword()));
            user.setRole(UserRole.STUDENT);
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);

            StudentProfile profile = new StudentProfile();
            profile.setUserId(user.getId());
            profile.setCollege(colleges.get(spec.collegeIndex()));
            profile.setCourse(spec.course());
            profile.setGraduationYear(spec.year());
            profile.setCity(spec.city());
            profile.setState(spec.state());
            profile.setDistrict(colleges.get(spec.collegeIndex()).getDistrict());
            profile.setSkills(new String[]{"Java", "React", "SQL"});
            studentProfileRepository.save(profile);
        }
    }
}