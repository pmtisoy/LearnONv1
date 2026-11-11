package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.Course;
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
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProgressRepository progressRepository;

    public CourseService(
            CourseRepository courseRepository,
            UserRepository userRepository,
            EnrollmentRepository enrollmentRepository,
            ProgressRepository progressRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
    }

    public Course create(Course course, String email) {
        User teacher = requireTeacher(email);
        course.setId(null);
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }

    @Transactional(readOnly = true)
    public Course getById(Long id, String email) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + id));
        User user = findUser(email);
        if ("TEACHER".equals(user.getRole())) {
            requireOwner(course, user);
        } else if (!course.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found: " + id);
        }
        return course;
    }

    @Transactional(readOnly = true)
    public List<Course> getAll(String email) {
        User user = findUser(email);
        if ("TEACHER".equals(user.getRole())) {
            return courseRepository.findByTeacher_IdOrderByCreatedAtDesc(user.getId());
        }
        return courseRepository.findByPublishedTrue();
    }

    @Transactional(readOnly = true)
    public List<Course> getOwnedCourses(String email) {
        User teacher = requireTeacher(email);
        return courseRepository.findByTeacher_IdOrderByCreatedAtDesc(teacher.getId());
    }

    @Transactional(readOnly = true)
    public List<Course> getPublishedCourses() {
        return courseRepository.findByPublishedTrue();
    }

    public Course update(Long id, Course updatedCourse, String email) {
        User teacher = requireTeacher(email);
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + id));
        requireOwner(course, teacher);
        course.setTitle(updatedCourse.getTitle());
        course.setDescription(updatedCourse.getDescription());
        course.setPublished(updatedCourse.isPublished());
        return courseRepository.save(course);
    }

    public void delete(Long id, String email) {
        User teacher = requireTeacher(email);
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + id));
        requireOwner(course, teacher);
        if (enrollmentRepository.existsByCourse_Id(id)
                || progressRepository.existsByCourse_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Course has enrollment or progress records; remove those records before deleting it.");
        }
        courseRepository.delete(course);
    }

    @Transactional(readOnly = true)
    public Course requireTeacherOwnedCourse(Long courseId, String email) {
        User teacher = requireTeacher(email);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Course not found: " + courseId));
        requireOwner(course, teacher);
        return course;
    }

    private User requireTeacher(String email) {
        User user = findUser(email);
        if (!"TEACHER".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Teacher access is required.");
        }
        return user;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user was not found."));
    }

    private void requireOwner(Course course, User teacher) {
        if (course.getTeacher() == null
                || !course.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only manage your own courses.");
        }
    }
}
