package com.nexura.app.controller;

import com.nexura.app.entity.Medication;
import com.nexura.app.entity.MedicationAdherence;
import com.nexura.app.service.MedicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medications")
@CrossOrigin(origins = "*")
public class MedicationController {

    @Autowired
    private MedicationService medicationService;

    @GetMapping
    public ResponseEntity<List<Medication>> getAll() {
        return ResponseEntity.ok(medicationService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Medication med) {
        try {
            Medication saved = medicationService.add(med);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to add medication: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Medication med) {
        try {
            Medication updated = medicationService.update(id, med);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update medication: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            medicationService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Medication deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete medication: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/taken")
    public ResponseEntity<?> markAsTaken(@PathVariable Long id) {
        try {
            MedicationAdherence adherence = medicationService.markAsTaken(id);
            return ResponseEntity.ok(adherence);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to log medication ingestion: " + e.getMessage());
        }
    }

    @GetMapping("/compliance")
    public ResponseEntity<?> getCompliance() {
        try {
            double compliance = medicationService.getWeeklyComplianceRate();
            return ResponseEntity.ok(Map.of("complianceRate", compliance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to calculate compliance rates: " + e.getMessage());
        }
    }
}
