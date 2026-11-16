package com.it342g02.learnon.controller;

import com.it342g02.learnon.entity.Course;
import com.it342g02.learnon.entity.Enrollment;
import com.it342g02.learnon.service.CourseService;
import com.it342g02.learnon.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public CourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Course create(
            @Valid @RequestBody Course course,
            Authentication authentication) {
        return courseService.create(course, authentication.getName());
    }

    @GetMapping("/{id}")
    public Course getById(@PathVariable Long id, Authentication authentication) {
        return courseService.getById(id, authentication.getName());
    }

    @GetMapping
    public List<Course> getAll(Authentication authentication) {
        return courseService.getAll(authentication.getName());
    }

    @GetMapping("/owned")
    public List<Course> getOwnedCourses(Authentication authentication) {
        return courseService.getOwnedCourses(authentication.getName());
    }

    @GetMapping("/published")
    public List<Course> getPublishedCourses() {
        return courseService.getPublishedCourses();
    }

    @PutMapping("/{id}")
    public Course update(
            @PathVariable Long id,
            @Valid @RequestBody Course course,
            Authentication authentication) {
        return courseService.update(id, course, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        courseService.delete(id, authentication.getName());
    }

    @PostMapping("/{courseId}/learners")
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment enrollLearnerByEmail(
            @PathVariable Long courseId,
            @RequestParam String email,
            Authentication authentication) {
        return enrollmentService.enrollByEmail(
                authentication.getName(), courseId, email);
    }
}
