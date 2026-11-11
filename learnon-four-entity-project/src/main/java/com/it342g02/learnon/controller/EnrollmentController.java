package com.it342g02.learnon.controller;

import com.it342g02.learnon.entity.Enrollment;
import com.it342g02.learnon.service.EnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment enroll(
            @RequestParam Long userId,
            @RequestParam Long courseId,
            Authentication authentication) {
        return enrollmentService.enroll(userId, courseId, authentication.getName());
    }

    @GetMapping("/user/{userId}")
    public List<Enrollment> getByUser(
            @PathVariable Long userId,
            Authentication authentication) {
        return enrollmentService.getByUser(userId, authentication.getName());
    }

    @GetMapping("/course/{courseId}")
    public List<Enrollment> getByCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        return enrollmentService.getByCourse(courseId, authentication.getName());
    }

    @DeleteMapping("/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @PathVariable Long enrollmentId,
            Authentication authentication) {
        enrollmentService.cancel(enrollmentId, authentication.getName());
    }
}
