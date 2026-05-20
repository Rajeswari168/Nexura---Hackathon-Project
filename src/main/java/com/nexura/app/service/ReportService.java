package com.nexura.app.service;

import com.nexura.app.entity.MedicalReport;
import com.nexura.app.entity.User;
import com.nexura.app.repository.MedicalReportRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    @Autowired
    private MedicalReportRepository reportRepository;

    @Autowired
    private UserService userService;

    @Value("${nexura.upload.dir}")
    private String uploadDir;

    public MedicalReport saveReport(MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();

        // 1. File validation
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf") &&
                !contentType.equals("image/png") &&
                !contentType.equals("image/jpeg") &&
                !contentType.equals("image/jpg"))) {
            throw new IllegalArgumentException("Unsupported file type. Only PDF and PNG/JPEG/JPG images are allowed.");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 10MB.");
        }

        // 2. Secure file naming and folder creations
        File uploadFolder = new File(uploadDir);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String secureFilename = UUID.randomUUID().toString() + fileExtension;
        
        Path targetPath = Paths.get(uploadDir).resolve(secureFilename);
        Files.copy(file.getInputStream(), targetPath);

        // 3. Database persistence
        MedicalReport report = new MedicalReport();
        report.setUser(user);
        report.setFileName(originalFilename);
        report.setFilePath("/uploads/" + secureFilename);
        report.setFileType(contentType);

        return reportRepository.save(report);
    }

    public List<MedicalReport> getReports() {
        User user = userService.getCurrentUser();
        return reportRepository.findByUserIdOrderByUploadedAtDesc(user.getId());
    }

    public MedicalReport getReportById(Long id) {
        User user = userService.getCurrentUser();
        MedicalReport report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical report not found"));

        if (!report.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized document access");
        }

        return report;
    }

    public void deleteReport(Long id) {
        MedicalReport report = getReportById(id);

        // Delete from local disk
        try {
            String filename = report.getFilePath().substring(report.getFilePath().lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).resolve(filename);
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            // Log local disk deletion failure gracefully
        }

        reportRepository.delete(report);
    }

    public String extractTextFromPdf(MedicalReport report) {
        if (!"application/pdf".equals(report.getFileType())) {
            return "";
        }

        String filename = report.getFilePath().substring(report.getFilePath().lastIndexOf("/") + 1);
        Path path = Paths.get(uploadDir).resolve(filename);
        File pdfFile = path.toFile();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            return "Failed to parse PDF document text: " + e.getMessage();
        }
    }
}
