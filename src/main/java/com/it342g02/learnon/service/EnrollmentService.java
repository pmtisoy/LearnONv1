package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.Course;
import com.it342g02.learnon.entity.Enrollment;
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
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final ProgressRepository progressRepository;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            CourseRepository courseRepository,
            CourseService courseService,
            ProgressRepository progressRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.progressRepository = progressRepository;
    }

    public Enrollment enroll(Long userId, Long courseId, String authenticatedEmail) {
        User authenticatedUser = findUser(authenticatedEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + courseId));

        if ("LEARNER".equals(authenticatedUser.getRole())) {
            if (!authenticatedUser.getId().equals(user.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Learners can only enroll themselves.");
            }
        } else if ("TEACHER".equals(authenticatedUser.getRole())) {
            courseService.requireTeacherOwnedCourse(courseId, authenticatedEmail);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported account role.");
        }

        if (!course.isPublished() && !"TEACHER".equals(authenticatedUser.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot enroll in an unpublished course.");
        }

        if (!"LEARNER".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only learner accounts can be enrolled.");
        }

        if (enrollmentRepository.existsByUser_IdAndCourse_Id(userId, courseId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User is already enrolled in this course.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);

        return enrollmentRepository.save(enrollment);
    }

    public Enrollment enrollByEmail(String authenticatedEmail, Long courseId, String learnerEmail) {
        courseService.requireTeacherOwnedCourse(courseId, authenticatedEmail);
        User learner = userRepository.findByEmail(learnerEmail.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Learner not found."));
        if (!"LEARNER".equals(learner.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only learner accounts can be enrolled.");
        }
        return enroll(learner.getId(), courseId, authenticatedEmail);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getByUser(Long userId, String authenticatedEmail) {
        User authenticatedUser = findUser(authenticatedEmail);
        if (!"LEARNER".equals(authenticatedUser.getRole())
                || !authenticatedUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Learners can only view their own enrollments.");
        }

        return enrollmentRepository.findByUser_IdOrderByEnrolledAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getByCourse(Long courseId, String authenticatedEmail) {
        courseService.requireTeacherOwnedCourse(courseId, authenticatedEmail);
        return enrollmentRepository.findByCourse_Id(courseId);
    }

    public void cancel(Long enrollmentId, String authenticatedEmail) {
        User authenticatedUser = findUser(authenticatedEmail);
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Enrollment not found: " + enrollmentId));

        if ("TEACHER".equals(authenticatedUser.getRole())) {
            courseService.requireTeacherOwnedCourse(
                    enrollment.getCourse().getId(), authenticatedEmail);
        } else if (!"LEARNER".equals(authenticatedUser.getRole())
                || !authenticatedUser.getId().equals(enrollment.getUser().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You cannot manage this enrollment.");
        }

        progressRepository.deleteByUser_IdAndCourse_Id(
                enrollment.getUser().getId(), enrollment.getCourse().getId());
        enrollmentRepository.delete(enrollment);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user was not found."));
    }
}
