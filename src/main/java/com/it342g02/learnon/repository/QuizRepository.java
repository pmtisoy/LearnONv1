package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCourse_IdOrderByDueAtAsc(Long courseId);

    boolean existsByCourse_Id(Long courseId);
}
