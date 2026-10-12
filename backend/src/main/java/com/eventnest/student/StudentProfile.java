package com.eventnest.student;

import com.eventnest.college.College;
import com.eventnest.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The student side of the profile (ARCHITECTURE.md section 4.2).
 *
 * <p>{@code user_id} is both PK and FK - the standard way to express a 1:1 in
 * SQL. It also guarantees you can never end up with two profiles for one login.
 *
 * <p>{@code city} and {@code state} here are what drive "Near You" for a logged-in
 * student, which is why they live on the profile rather than only on the college.
 *
 * <p>Skills are a native PostgreSQL {@code TEXT[]} rather than a child table: the
 * value is always read as a whole set and never queried relationally, so a join
 * would cost more than it returns. The trade-off is that we cannot say "students
 * with skill X" with a foreign key - an accepted one for a 6-value tag list, but
 * if skills ever became a shared, curated vocabulary with its own page, they would
 * be promoted to a table.
 */
@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile extends Auditable {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", foreignKey = @ForeignKey(name = "fk_student_profiles_college"))
    private College college;

    @Column(name = "course", length = 80)
    private String course;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "district", length = 80)
    private String district;

    @Column(name = "state", length = 80)
    private String state;

    // Hibernate 6 maps String[] to a native PostgreSQL array from columnDefinition
    // alone. @Array is for java.sql.Array (JDBC blobs of primitives) and would be
    // the wrong annotation here.
    @Column(name = "skills", columnDefinition = "text[]")
    private String[] skills = new String[0];

    public List<String> skillList() {
        return skills == null ? List.of() : Arrays.asList(skills);
    }
}
