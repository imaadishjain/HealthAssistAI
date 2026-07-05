package com.example.HealthAssistBackend.controller;

import com.example.HealthAssistBackend.model.Appointment;
import com.example.HealthAssistBackend.model.Doctor;
import com.example.HealthAssistBackend.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Appointment controller for checking availability and managing appointments.
 */
@RestController
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Appointments", description = "Doctor availability & appointment management")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Check doctor availability by department/specialty.
     * GET /appointments/check?department=Cardiology
     */
    @GetMapping("/appointments/check")
    public ResponseEntity<List<Doctor>> checkAvailability(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String specialty) {
        log.info("GET /appointments/check - department: {}, specialty: {}", department, specialty);
        List<Doctor> doctors = appointmentService.checkAvailability(department, specialty);
        return ResponseEntity.ok(doctors);
    }

    /**
     * Create a new appointment.
     * POST /appointments/create
     */
    @PostMapping("/appointments/create")
    public ResponseEntity<Appointment> createAppointment(@RequestBody Map<String, String> request) {
        log.info("POST /appointments/create - patient: {}", request.get("patientName"));

        Long doctorId = request.containsKey("doctorId") ? Long.parseLong(request.get("doctorId")) : null;
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(request.get("dateTime"));
        } catch (Exception e) {
            dateTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        }

        Appointment appointment = appointmentService.createAppointment(
            request.get("patientName"),
            request.get("patientEmail"),
            request.get("patientPhone"),
            doctorId,
            request.get("department"),
            dateTime,
            request.get("reason")
        );

        return ResponseEntity.ok(appointment);
    }

    /**
     * Get all appointments.
     * GET /appointments
     */
    @GetMapping("/appointments")
    public ResponseEntity<List<Appointment>> getAllAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    /**
     * Get all doctors.
     * GET /doctors
     */
    @GetMapping("/doctors")
    public ResponseEntity<List<Doctor>> getAllDoctors() {
        return ResponseEntity.ok(appointmentService.getAllDoctors());
    }

    /**
     * Get doctors by department.
     * GET /doctors/department/{department}
     */
    @GetMapping("/doctors/department/{department}")
    public ResponseEntity<List<Doctor>> getDoctorsByDepartment(@PathVariable String department) {
        return ResponseEntity.ok(appointmentService.getDoctorsByDepartment(department));
    }

    /**
     * Cancel an appointment.
     * PUT /appointments/{id}/cancel
     */
    @PutMapping("/appointments/{id}/cancel")
    public ResponseEntity<Appointment> cancelAppointment(@PathVariable Long id) {
        return appointmentService.cancelAppointment(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
