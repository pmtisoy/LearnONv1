package com.it342g02.learnon.controller;

import com.it342g02.learnon.entity.ModuleType;
import com.it342g02.learnon.service.ModuleService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping("/api/courses/{courseId}/modules")
    public List<ModuleService.ModuleView> list(
            @PathVariable Long courseId,
            Authentication authentication) {
        return moduleService.list(courseId, authentication.getName());
    }

    @PostMapping(
            path = "/api/courses/{courseId}/modules",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ModuleService.ModuleView create(
            @PathVariable Long courseId,
            @RequestParam String title,
            @RequestParam ModuleType type,
            @RequestParam(required = false) String content,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        return moduleService.create(
                courseId, authentication.getName(), title, type, content, file);
    }

    @PutMapping(path = "/api/modules/{moduleId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ModuleService.ModuleView update(
            @PathVariable Long moduleId,
            @RequestParam String title,
            @RequestParam ModuleType type,
            @RequestParam(required = false) String content,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        return moduleService.update(
                moduleId, authentication.getName(), title, type, content, file);
    }

    @DeleteMapping("/api/modules/{moduleId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long moduleId,
            Authentication authentication) {
        moduleService.delete(moduleId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/modules/{moduleId}/file")
    public ResponseEntity<byte[]> download(
            @PathVariable Long moduleId,
            Authentication authentication) {
        ModuleService.PdfFile pdf = moduleService.getPdf(moduleId, authentication.getName());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(pdf.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf.content());
    }
}
