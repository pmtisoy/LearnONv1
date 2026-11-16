package com.it342g02.learnon.repository;

import com.it342g02.learnon.entity.QuizAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuiz_Id(Long quizId);

    boolean existsByQuiz_IdAndLearner_Id(Long quizId, Long learnerId);

    @EntityGraph(attributePaths = {"quiz", "quiz.questions"})
    Optional<QuizAttempt> findByQuiz_IdAndLearner_Id(Long quizId, Long learnerId);

    @EntityGraph(attributePaths = {"quiz", "quiz.questions"})
    List<QuizAttempt> findByGradedAtIsNullAndQuiz_DueAtLessThanEqual(Instant dueAt);
}
