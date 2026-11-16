package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrue();

    List<Course> findByTeacher_IdOrderByCreatedAtDesc(Long teacherId);
}
