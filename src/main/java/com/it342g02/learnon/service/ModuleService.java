package com.it342g02.learnon.service;

import com.it342g02.learnon.entity.Course;
import com.it342g02.learnon.entity.CourseModule;
import com.it342g02.learnon.entity.ModuleType;
import com.it342g02.learnon.entity.User;
import com.it342g02.learnon.repository.CourseModuleRepository;
import com.it342g02.learnon.repository.EnrollmentRepository;
import com.it342g02.learnon.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ModuleService {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private final CourseModuleRepository moduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;
    private final long maxPdfBytes;

    public ModuleService(
            CourseModuleRepository moduleRepository,
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            CourseService courseService,
            @Value("${learnon.upload.max-pdf-bytes:20971520}") long maxPdfBytes) {
        this.moduleRepository = moduleRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.maxPdfBytes = maxPdfBytes;
    }

    @Transactional(readOnly = true)
    public List<ModuleView> list(Long courseId, String email) {
        requireCourseAccess(courseId, email);
        return moduleRepository.findByCourse_IdOrderByCreatedAtAsc(courseId)
                .stream()
                .map(this::toView)
                .toList();
    }

    public ModuleView create(
            Long courseId,
            String email,
            String title,
            ModuleType type,
            String content,
            MultipartFile file) {
        Course course = courseService.requireTeacherOwnedCourse(courseId, email);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        applyContent(module, title, type, content, file, false);
        return toView(moduleRepository.save(module));
    }

    public ModuleView update(
            Long moduleId,
            String email,
            String title,
            ModuleType type,
            String content,
            MultipartFile file) {
        CourseModule module = findModule(moduleId);
        courseService.requireTeacherOwnedCourse(module.getCourse().getId(), email);
        applyContent(module, title, type, content, file, true);
        return toView(moduleRepository.save(module));
    }

    public void delete(Long moduleId, String email) {
        CourseModule module = findModule(moduleId);
        courseService.requireTeacherOwnedCourse(module.getCourse().getId(), email);
        moduleRepository.delete(module);
    }

    @Transactional(readOnly = true)
    public PdfFile getPdf(Long moduleId, String email) {
        CourseModule module = findModule(moduleId);
        requireCourseAccess(module.getCourse().getId(), email);
        if (module.getType() != ModuleType.PDF || module.getFileData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found.");
        }
        return new PdfFile(module.getFileName(), module.getFileData());
    }

    private void requireCourseAccess(Long courseId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user was not found."));
        if ("TEACHER".equals(user.getRole())) {
            courseService.requireTeacherOwnedCourse(courseId, email);
            return;
        }
        if (!"LEARNER".equals(user.getRole())
                || !enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), courseId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Enroll in the course to view its modules.");
        }
    }

    private CourseModule findModule(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Module not found: " + moduleId));
    }

    private void applyContent(
            CourseModule module,
            String title,
            ModuleType type,
            String content,
            MultipartFile file,
            boolean preserveExistingFile) {
        if (title == null || title.isBlank() || title.trim().length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A module title of at most 150 characters is required.");
        }
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A module type is required.");
        }

        module.setTitle(title.trim());
        module.setType(type);

        switch (type) {
            case TEXT -> {
                if (content == null || content.isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Text modules require lesson content.");
                }
                module.setContent(content.trim());
                module.setFileName(null);
                module.setFileData(null);
            }
            case LINK -> {
                validateLink(content);
                module.setContent(content.trim());
                module.setFileName(null);
                module.setFileData(null);
            }
            case PDF -> {
                if (file == null || file.isEmpty()) {
                    if (!preserveExistingFile || module.getFileData() == null) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "PDF modules require a PDF upload.");
                    }
                    return;
                }
                if (file.getSize() > maxPdfBytes) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "PDF exceeds the maximum size of " + maxPdfBytes + " bytes.");
                }
                String originalName = file.getOriginalFilename();
                if (originalName == null
                        || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Only PDF files are accepted.");
                }
                try {
                    byte[] data = file.getBytes();
                    if (!hasPdfSignature(data)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "The uploaded file is not a valid PDF.");
                    }
                    module.setFileData(data);
                    module.setFileName(safeFileName(originalName));
                    module.setContent(null);
                } catch (IOException exception) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "The PDF upload could not be read.", exception);
                }
            }
        }
    }

    private void validateLink(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid web URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Link modules require a valid http or https URL.");
        }
    }

    private boolean hasPdfSignature(byte[] data) {
        if (data.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (data[index] != PDF_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private String safeFileName(String originalName) {
        String normalized = originalName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private ModuleView toView(CourseModule module) {
        return new ModuleView(
                module.getId(),
                module.getTitle(),
                module.getType(),
                module.getContent(),
                module.getFileName());
    }

    public record ModuleView(
            Long id,
            String title,
            ModuleType type,
            String content,
            String fileName) {
    }

    public record PdfFile(String fileName, byte[] content) {
    }
}
