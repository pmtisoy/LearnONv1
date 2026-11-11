package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.Progress;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends JpaRepository<Progress, Long> {

    @EntityGraph(attributePaths = {"user", "course"})
    Optional<Progress> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByCourse_Id(Long courseId);

    void deleteByUser_IdAndCourse_Id(Long userId, Long courseId);

    @EntityGraph(attributePaths = {"user", "course"})
    List<Progress> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "course"})
    List<Progress> findByCourse_Id(Long courseId);
}
