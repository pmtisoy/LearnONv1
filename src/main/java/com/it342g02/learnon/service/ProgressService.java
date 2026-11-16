package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.Course;
import com.it342g02.learnon.entity.Progress;
import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.repository.CourseRepository;
import com.it342g02.learnon.repository.EnrollmentRepository;
import com.it342g02.learnon.repository.ProgressRepository;
import com.it342g02.learnon.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseService courseService;

    public ProgressService(
            ProgressRepository progressRepository,
            UserRepository userRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            CourseService courseService) {
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseService = courseService;
    }

    public Progress updateProgress(
            Long userId,
            Long courseId,
            int completedLessons,
            int totalLessons,
            String authenticatedEmail) {

        if (completedLessons < 0 || totalLessons < 0
                || completedLessons > totalLessons) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid lesson progress values.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));

        User authenticatedUser = findUser(authenticatedEmail);
        if (!"LEARNER".equals(authenticatedUser.getRole())
                || !authenticatedUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Learners can only update their own progress.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + courseId));

        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(userId, courseId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Enroll in the course before updating progress.");
        }

        Progress progress = progressRepository
                .findByUser_IdAndCourse_Id(userId, courseId)
                .orElseGet(Progress::new);

        progress.setUser(user);
        progress.setCourse(course);
        progress.setCompletedLessons(completedLessons);
        progress.setTotalLessons(totalLessons);

        double percentage = totalLessons == 0
                ? 0.0
                : (completedLessons * 100.0) / totalLessons;

        progress.setCompletionPercentage(percentage);

        return progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public Progress getByUserAndCourse(Long userId, Long courseId, String authenticatedEmail) {
        User authenticatedUser = findUser(authenticatedEmail);
        if ("TEACHER".equals(authenticatedUser.getRole())) {
            courseService.requireTeacherOwnedCourse(courseId, authenticatedEmail);
        } else if (!"LEARNER".equals(authenticatedUser.getRole())
                || !authenticatedUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You cannot view this progress record.");
        }
        return progressRepository.findByUser_IdAndCourse_Id(userId, courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Progress record not found."));
    }

    @Transactional(readOnly = true)
    public List<Progress> getByUser(Long userId, String authenticatedEmail) {
        User authenticatedUser = findUser(authenticatedEmail);
        if (!"LEARNER".equals(authenticatedUser.getRole())
                || !authenticatedUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Learners can only view their own progress.");
        }

        return progressRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public List<Progress> getByCourse(Long courseId, String authenticatedEmail) {
        courseService.requireTeacherOwnedCourse(courseId, authenticatedEmail);
        return progressRepository.findByCourse_Id(courseId);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user was not found."));
    }
}
