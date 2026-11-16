package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.*;
import com.it342g02.learnon.repository.EnrollmentRepository;
import com.it342g02.learnon.repository.QuizAttemptRepository;
import com.it342g02.learnon.repository.QuizRepository;
import com.it342g02.learnon.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public QuizService(
            QuizRepository quizRepository,
            QuizAttemptRepository attemptRepository,
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            CourseService courseService) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    public QuizView create(Long courseId, String email, QuizInput input) {
        Course course = courseService.requireTeacherOwnedCourse(courseId, email);
        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        updateQuizContent(quiz, input);
        return toView(quizRepository.save(quiz), true);
    }

    public QuizView update(Long quizId, String email, QuizInput input) {
        Quiz quiz = findQuiz(quizId);
        courseService.requireTeacherOwnedCourse(quiz.getCourse().getId(), email);
        ensureEditable(quiz);
        updateQuizContent(quiz, input);
        return toView(quizRepository.save(quiz), true);
    }

    public void delete(Long quizId, String email) {
        Quiz quiz = findQuiz(quizId);
        courseService.requireTeacherOwnedCourse(quiz.getCourse().getId(), email);
        if (attemptRepository.existsByQuiz_Id(quizId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A quiz with learner attempts cannot be deleted.");
        }
        quizRepository.delete(quiz);
    }

    public List<QuizSummary> list(Long courseId, String email) {
        User user = findUser(email);
        boolean teacher = "TEACHER".equals(user.getRole());
        if (teacher) {
            courseService.requireTeacherOwnedCourse(courseId, email);
        } else {
            requireLearnerEnrollment(user, courseId);
        }
        return quizRepository.findByCourse_IdOrderByDueAtAsc(courseId)
                .stream()
                .map(quiz -> summary(quiz, user, teacher))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizView get(Long quizId, String email) {
        Quiz quiz = findQuiz(quizId);
        User user = findUser(email);
        boolean teacher = "TEACHER".equals(user.getRole());
        if (teacher) {
            courseService.requireTeacherOwnedCourse(quiz.getCourse().getId(), email);
        } else {
            requireLearnerEnrollment(user, quiz.getCourse().getId());
        }
        return toView(quiz, teacher);
    }

    public AttemptView startAttempt(Long quizId, String email) {
        Quiz quiz = findQuiz(quizId);
        User learner = requireLearner(quiz.getCourse().getId(), email);
        if (!Instant.now().isBefore(quiz.getDueAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "The quiz deadline has passed.");
        }
        if (attemptRepository.existsByQuiz_IdAndLearner_Id(quizId, learner.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You have already started your one attempt.");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setLearner(learner);
        return toAttemptView(attemptRepository.save(attempt));
    }

    public AttemptView getAttempt(Long quizId, String email) {
        Quiz quiz = findQuiz(quizId);
        User learner = requireLearner(quiz.getCourse().getId(), email);
        QuizAttempt attempt = findAttempt(quizId, learner.getId());
        gradeIfDue(attempt);
        return toAttemptView(attempt);
    }

    public AttemptView saveAnswers(Long quizId, String email, List<AnswerInput> inputs) {
        Quiz quiz = findQuiz(quizId);
        User learner = requireLearner(quiz.getCourse().getId(), email);
        QuizAttempt attempt = findAttempt(quizId, learner.getId());
        gradeIfDue(attempt);
        if (attempt.getGradedAt() != null) {
            return toAttemptView(attempt);
        }

        Map<Long, QuizQuestion> questionsById = new HashMap<>();
        quiz.getQuestions().forEach(question -> questionsById.put(question.getId(), question));
        Set<Long> answeredQuestionIds = new HashSet<>();
        List<QuizAnswer> answers = new ArrayList<>();

        for (AnswerInput input : inputs == null ? List.<AnswerInput>of() : inputs) {
            if (input == null || input.questionId() == null
                    || input.selectedChoice() == null
                    || input.selectedChoice() < 1 || input.selectedChoice() > 4
                    || !answeredQuestionIds.add(input.questionId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Answers must select one of four choices per question.");
            }
            QuizQuestion question = questionsById.get(input.questionId());
            if (question == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "An answer references a question outside this quiz.");
            }
            QuizAnswer answer = new QuizAnswer();
            answer.setQuestion(question);
            answer.setSelectedChoice(input.selectedChoice());
            answers.add(answer);
        }

        attempt.replaceAnswers(answers);
        return toAttemptView(attemptRepository.save(attempt));
    }

    @Scheduled(fixedDelayString = "${learnon.quiz.grading-interval-ms:30000}")
    public void gradeDueAttempts() {
        List<QuizAttempt> dueAttempts =
                attemptRepository.findByGradedAtIsNullAndQuiz_DueAtLessThanEqual(Instant.now());
        dueAttempts.forEach(this::grade);
    }

    private void gradeIfDue(QuizAttempt attempt) {
        if (attempt.getGradedAt() == null
                && !Instant.now().isBefore(attempt.getQuiz().getDueAt())) {
            grade(attempt);
        }
    }

    private void grade(QuizAttempt attempt) {
        if (attempt.getGradedAt() != null) {
            return;
        }
        Map<Long, Integer> selectedByQuestion = new HashMap<>();
        attempt.getAnswers().forEach(answer ->
                selectedByQuestion.put(answer.getQuestion().getId(), answer.getSelectedChoice()));
        int score = 0;
        for (QuizQuestion question : attempt.getQuiz().getQuestions()) {
            if (Objects.equals(
                    selectedByQuestion.get(question.getId()), question.getCorrectChoice())) {
                score++;
            }
        }
        attempt.setScore(score);
        attempt.setTotalQuestions(attempt.getQuiz().getQuestions().size());
        attempt.setGradedAt(Instant.now());
        attemptRepository.save(attempt);
    }

    private void updateQuizContent(Quiz quiz, QuizInput input) {
        if (input == null || input.title() == null || input.title().isBlank()
                || input.title().trim().length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A quiz title of at most 150 characters is required.");
        }
        if (input.dueAt() == null || !input.dueAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The quiz due date must be in the future.");
        }
        if (input.questions() == null || input.questions().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A quiz must contain at least one question.");
        }

        List<QuizQuestion> questions = new ArrayList<>();
        for (QuestionInput questionInput : input.questions()) {
            if (questionInput == null
                    || questionInput.prompt() == null
                    || questionInput.prompt().isBlank()
                    || questionInput.choices() == null
                    || questionInput.choices().size() != 4
                    || questionInput.choices().stream().anyMatch(
                            choice -> choice == null || choice.isBlank() || choice.length() > 500)
                    || questionInput.correctChoice() == null
                    || questionInput.correctChoice() < 1
                    || questionInput.correctChoice() > 4) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each question needs text, exactly four choices, and one correct choice.");
            }
            QuizQuestion question = new QuizQuestion();
            question.setPrompt(questionInput.prompt().trim());
            question.setChoice1(questionInput.choices().get(0).trim());
            question.setChoice2(questionInput.choices().get(1).trim());
            question.setChoice3(questionInput.choices().get(2).trim());
            question.setChoice4(questionInput.choices().get(3).trim());
            question.setCorrectChoice(questionInput.correctChoice());
            questions.add(question);
        }

        quiz.setTitle(input.title().trim());
        quiz.setDueAt(input.dueAt());
        quiz.replaceQuestions(questions);
    }

    private void ensureEditable(Quiz quiz) {
        if (attemptRepository.existsByQuiz_Id(quiz.getId())
                || !Instant.now().isBefore(quiz.getDueAt())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A quiz cannot be changed after an attempt or its deadline.");
        }
    }

    private QuizSummary summary(Quiz quiz, User user, boolean teacher) {
        if (teacher) {
            return new QuizSummary(
                    quiz.getId(), quiz.getTitle(), quiz.getDueAt(), quiz.getQuestions().size(),
                    false, false, null, null);
        }
        Optional<QuizAttempt> attempt = attemptRepository
                .findByQuiz_IdAndLearner_Id(quiz.getId(), user.getId());
        attempt.ifPresent(this::gradeIfDue);
        QuizAttempt value = attempt.orElse(null);
        return new QuizSummary(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDueAt(),
                quiz.getQuestions().size(),
                value != null,
                value != null && value.getGradedAt() != null,
                value == null ? null : value.getScore(),
                value == null ? null : value.getTotalQuestions());
    }

    private QuizView toView(Quiz quiz, boolean includeCorrectAnswers) {
        return new QuizView(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDueAt(),
                quiz.getQuestions().stream()
                        .map(question -> questionView(question, null, includeCorrectAnswers, null))
                        .toList());
    }

    private AttemptView toAttemptView(QuizAttempt attempt) {
        Map<Long, QuizAnswer> answersByQuestion = new HashMap<>();
        attempt.getAnswers().forEach(answer ->
                answersByQuestion.put(answer.getQuestion().getId(), answer));
        boolean revealAnswers = attempt.getGradedAt() != null;
        List<QuestionView> questions = attempt.getQuiz().getQuestions().stream()
                .map(question -> {
                    QuizAnswer answer = answersByQuestion.get(question.getId());
                    Integer selected = answer == null ? null : answer.getSelectedChoice();
                    return questionView(question, selected, revealAnswers, selected);
                })
                .toList();
        return new AttemptView(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getTitle(),
                attempt.getQuiz().getDueAt(),
                attempt.getStartedAt(),
                revealAnswers,
                attempt.getScore(),
                attempt.getTotalQuestions(),
                questions);
    }

    private QuestionView questionView(
            QuizQuestion question,
            Integer selectedChoice,
            boolean includeCorrectAnswer,
            Integer selectedForResult) {
        Integer correctChoice = includeCorrectAnswer ? question.getCorrectChoice() : null;
        Boolean correct = includeCorrectAnswer && selectedForResult != null
                ? Objects.equals(selectedForResult, question.getCorrectChoice())
                : null;
        return new QuestionView(
                question.getId(),
                question.getPrompt(),
                List.of(
                        question.getChoice1(),
                        question.getChoice2(),
                        question.getChoice3(),
                        question.getChoice4()),
                selectedChoice,
                correctChoice,
                correct);
    }

    private Quiz findQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Quiz not found: " + quizId));
    }

    private QuizAttempt findAttempt(Long quizId, Long learnerId) {
        return attemptRepository.findByQuiz_IdAndLearner_Id(quizId, learnerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No attempt exists for this quiz."));
    }

    private User requireLearner(Long courseId, String email) {
        User user = findUser(email);
        if (!"LEARNER".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only learners can attempt quizzes.");
        }
        requireLearnerEnrollment(user, courseId);
        return user;
    }

    private void requireLearnerEnrollment(User user, Long courseId) {
        if (!"LEARNER".equals(user.getRole())
                || !enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), courseId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Enroll in the course to access its quizzes.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user was not found."));
    }

    public record QuizInput(String title, Instant dueAt, List<QuestionInput> questions) {
    }

    public record QuestionInput(String prompt, List<String> choices, Integer correctChoice) {
    }

    public record AnswerInput(Long questionId, Integer selectedChoice) {
    }

    public record QuizSummary(
            Long id,
            String title,
            Instant dueAt,
            int questionCount,
            boolean attempted,
            boolean graded,
            Integer score,
            Integer totalQuestions) {
    }

    public record QuizView(
            Long id,
            String title,
            Instant dueAt,
            List<QuestionView> questions) {
    }

    public record AttemptView(
            Long id,
            Long quizId,
            String title,
            Instant dueAt,
            Instant startedAt,
            boolean graded,
            Integer score,
            Integer totalQuestions,
            List<QuestionView> questions) {
    }

    public record QuestionView(
            Long id,
            String prompt,
            List<String> choices,
            Integer selectedChoice,
            Integer correctChoice,
            Boolean correct) {
    }
}
