package com.it342g02.learnon.controller;

import com.it342g02.learnon.entity.Progress;
import com.it342g02.learnon.service.ProgressService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PutMapping
    public Progress updateProgress(
            @RequestParam Long userId,
            @RequestParam Long courseId,
            @RequestParam int completedLessons,
            @RequestParam int totalLessons,
            Authentication authentication) {
        return progressService.updateProgress(
                userId, courseId, completedLessons, totalLessons, authentication.getName());
    }

    @GetMapping("/user/{userId}/course/{courseId}")
    public Progress getByUserAndCourse(
            @PathVariable Long userId,
            @PathVariable Long courseId,
            Authentication authentication) {
        return progressService.getByUserAndCourse(
                userId, courseId, authentication.getName());
    }

    @GetMapping("/user/{userId}")
    public List<Progress> getByUser(
            @PathVariable Long userId,
            Authentication authentication) {
        return progressService.getByUser(userId, authentication.getName());
    }

    @GetMapping("/course/{courseId}")
    public List<Progress> getByCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        return progressService.getByCourse(courseId, authentication.getName());
    }
}
