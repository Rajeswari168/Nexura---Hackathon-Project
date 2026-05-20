package com.nexura.app.controller;

import com.nexura.app.entity.MedicalReport;
import com.nexura.app.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Value("${nexura.upload.dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadReport(@RequestParam("file") MultipartFile file) {
        try {
            MedicalReport saved = reportService.saveReport(file);
            return ResponseEntity.ok(Map.of(
                    "reportId", saved.getId(),
                    "fileName", saved.getFileName(),
                    "uploadStatus", "SUCCESS"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<MedicalReport>> getReports() {
        return ResponseEntity.ok(reportService.getReports());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable Long id) {
        try {
            reportService.deleteReport(id);
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete report: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> previewReport(@PathVariable Long id) {
        try {
            MedicalReport report = reportService.getReportById(id);
            String filename = report.getFilePath().substring(report.getFilePath().lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(report.getFileType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + report.getFileName() + "\"")
                        .body(resource);
            } else {
                throw new RuntimeException("Could not read file");
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
