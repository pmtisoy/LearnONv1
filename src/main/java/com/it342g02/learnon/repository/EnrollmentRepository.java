package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.Enrollment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByCourse_Id(Long courseId);

    @EntityGraph(attributePaths = {"user", "course"})
    List<Enrollment> findByUser_IdOrderByEnrolledAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "course"})
    List<Enrollment> findByCourse_Id(Long courseId);
}
