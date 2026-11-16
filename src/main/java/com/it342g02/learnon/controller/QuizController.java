package com.it342g02.learnon.controller;

import com.it342g02.learnon.service.QuizService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/api/courses/{courseId}/quizzes")
    public List<QuizService.QuizSummary> list(
            @PathVariable Long courseId,
            Authentication authentication) {
        return quizService.list(courseId, authentication.getName());
    }

    @PostMapping("/api/courses/{courseId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizService.QuizView create(
            @PathVariable Long courseId,
            @RequestBody QuizService.QuizInput input,
            Authentication authentication) {
        return quizService.create(courseId, authentication.getName(), input);
    }

    @GetMapping("/api/quizzes/{quizId}")
    public QuizService.QuizView get(
            @PathVariable Long quizId,
            Authentication authentication) {
        return quizService.get(quizId, authentication.getName());
    }

    @PutMapping("/api/quizzes/{quizId}")
    public QuizService.QuizView update(
            @PathVariable Long quizId,
            @RequestBody QuizService.QuizInput input,
            Authentication authentication) {
        return quizService.update(quizId, authentication.getName(), input);
    }

    @DeleteMapping("/api/quizzes/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long quizId, Authentication authentication) {
        quizService.delete(quizId, authentication.getName());
    }

    @PostMapping("/api/quizzes/{quizId}/attempt")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizService.AttemptView startAttempt(
            @PathVariable Long quizId,
            Authentication authentication) {
        return quizService.startAttempt(quizId, authentication.getName());
    }

    @GetMapping("/api/quizzes/{quizId}/attempt")
    public QuizService.AttemptView getAttempt(
            @PathVariable Long quizId,
            Authentication authentication) {
        return quizService.getAttempt(quizId, authentication.getName());
    }

    @PutMapping("/api/quizzes/{quizId}/attempt")
    public QuizService.AttemptView saveAnswers(
            @PathVariable Long quizId,
            @RequestBody AnswerRequest request,
            Authentication authentication) {
        return quizService.saveAnswers(
                quizId,
                authentication.getName(),
                request == null ? List.of() : request.answers());
    }

    public record AnswerRequest(List<QuizService.AnswerInput> answers) {
    }
}
