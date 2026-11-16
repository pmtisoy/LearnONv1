package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourse_IdOrderByCreatedAtAsc(Long courseId);

    boolean existsByCourse_Id(Long courseId);
}
