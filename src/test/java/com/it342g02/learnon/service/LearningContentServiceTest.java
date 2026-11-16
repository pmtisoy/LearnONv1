package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.Course;
import com.it342g02.learnon.entity.Quiz;
import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:learnon-content-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LearningContentServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizRepository quizRepository;

    @Test
    void storesPdfAndGradesSingleAttemptOnlyAfterTheDeadline() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User teacher = createUser("teacher-" + suffix + "@example.com");
        userService.changeRole(teacher.getEmail(), "TEACHER");
        User learner = createUser("learner-" + suffix + "@example.com");

        Course course = new Course();
        course.setTitle("Learning content course");
        course.setPublished(true);
        course = courseService.create(course, teacher.getEmail());
        Long courseId = course.getId();
        enrollmentService.enroll(learner.getId(), courseId, learner.getEmail());

        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
        ModuleService.ModuleView module = moduleService.create(
                courseId, teacher.getEmail(), "Lesson one",
                com.it342g02.learnon.entity.ModuleType.PDF, null, pdf);
        assertArrayEquals(pdf.getBytes(),
                moduleService.getPdf(module.id(), learner.getEmail()).content());
        assertThrows(ResponseStatusException.class,
                () -> moduleService.list(courseId, createUser("other-" + suffix + "@example.com")
                        .getEmail()));

        QuizService.QuizView createdQuiz = quizService.create(
                courseId,
                teacher.getEmail(),
                new QuizService.QuizInput(
                        "Quiz one",
                        Instant.now().plusSeconds(120),
                        List.of(new QuizService.QuestionInput(
                                "Select the correct answer",
                                List.of("A", "B", "C", "D"),
                                2))));

        assertNull(quizService.get(createdQuiz.id(), learner.getEmail())
                .questions().get(0).correctChoice());

        QuizService.AttemptView attempt =
                quizService.startAttempt(createdQuiz.id(), learner.getEmail());
        Long questionId = attempt.questions().get(0).id();
        QuizService.AttemptView saved = quizService.saveAnswers(
                createdQuiz.id(),
                learner.getEmail(),
                List.of(new QuizService.AnswerInput(questionId, 2)));
        assertFalse(saved.graded());
        assertNull(saved.questions().get(0).correctChoice());
        assertThrows(ResponseStatusException.class,
                () -> quizService.startAttempt(createdQuiz.id(), learner.getEmail()));

        Quiz quiz = quizRepository.findById(createdQuiz.id()).orElseThrow();
        quiz.setDueAt(Instant.now().minusSeconds(1));
        quizRepository.save(quiz);
        quizService.gradeDueAttempts();

        QuizService.AttemptView result =
                quizService.getAttempt(createdQuiz.id(), learner.getEmail());
        assertTrue(result.graded());
        assertEquals(1, result.score());
        assertEquals(1, result.totalQuestions());
        assertEquals(2, result.questions().get(0).correctChoice());
        assertTrue(result.questions().get(0).correct());
    }

    private User createUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("Account");
        user.setEmail(email);
        user.setPassword("test-password");
        return userService.create(user);
    }
}
