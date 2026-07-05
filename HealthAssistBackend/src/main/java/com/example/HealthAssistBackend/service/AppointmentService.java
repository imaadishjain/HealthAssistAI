package com.example.HealthAssistBackend.service;

import com.example.HealthAssistBackend.model.Appointment;
import com.example.HealthAssistBackend.model.Doctor;
import com.example.HealthAssistBackend.repository.AppointmentRepository;
import com.example.HealthAssistBackend.repository.DoctorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }

    /**
     * Check available doctors by department or specialty.
     */
    public List<Doctor> checkAvailability(String department, String specialty) {
        log.info("Checking doctor availability - department: {}, specialty: {}", department, specialty);

        if (department != null && !department.isBlank()) {
            return doctorRepository.findByDepartmentIgnoreCaseAndAvailableTrue(department);
        } else if (specialty != null && !specialty.isBlank()) {
            return doctorRepository.findBySpecialtyContainingIgnoreCase(specialty).stream()
                    .filter(Doctor::getAvailable)
                    .toList();
        }
        return doctorRepository.findByAvailableTrue();
    }

    /**
     * Get all doctors.
     */
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    /**
     * Get doctors by department.
     */
    public List<Doctor> getDoctorsByDepartment(String department) {
        return doctorRepository.findByDepartmentIgnoreCaseAndAvailableTrue(department);
    }

    /**
     * Create a new appointment.
     */
    @Transactional
    public Appointment createAppointment(String patientName, String patientEmail, String patientPhone,
                                          Long doctorId, String department, LocalDateTime dateTime, String reason) {
        log.info("Creating appointment for patient: {} with doctor ID: {} in department: {}", patientName, doctorId, department);

        Optional<Doctor> doctor = doctorId != null ? doctorRepository.findById(doctorId) : Optional.empty();

        Appointment appointment = new Appointment();
        appointment.setPatientName(patientName);
        appointment.setPatientEmail(patientEmail);
        appointment.setPatientPhone(patientPhone);
        appointment.setDepartment(department);
        appointment.setAppointmentDateTime(dateTime);
        appointment.setReason(reason);
        appointment.setStatus("SCHEDULED");
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());

        if (doctor.isPresent()) {
            appointment.setDoctorId(doctor.get().getId());
            appointment.setDoctorName(doctor.get().getName());
        } else {
            // Auto-assign a doctor from the department
            List<Doctor> availableDoctors = doctorRepository.findByDepartmentIgnoreCaseAndAvailableTrue(department);
            if (!availableDoctors.isEmpty()) {
                Doctor assigned = availableDoctors.get(0);
                appointment.setDoctorId(assigned.getId());
                appointment.setDoctorName(assigned.getName());
            } else {
                appointment.setDoctorName("To be assigned");
            }
        }

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment created successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Get appointments by patient name.
     */
    public List<Appointment> getAppointmentsByPatient(String patientName) {
        return appointmentRepository.findByPatientNameContainingIgnoreCase(patientName);
    }

    /**
     * Get appointments by department.
     */
    public List<Appointment> getAppointmentsByDepartment(String department) {
        return appointmentRepository.findByDepartmentIgnoreCase(department);
    }

    /**
     * Get appointments for a specific date range.
     */
    public List<Appointment> getAppointmentsByDateRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return appointmentRepository.findByAppointmentDateTimeBetween(start, end);
    }

    /**
     * Get all appointments.
     */
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    /**
     * Get a single appointment by ID.
     */
    public Optional<Appointment> getAppointmentById(Long id) {
        return appointmentRepository.findById(id);
    }

    /**
     * Cancel an appointment.
     */
    @Transactional
    public Optional<Appointment> cancelAppointment(Long id) {
        return appointmentRepository.findById(id).map(appointment -> {
            appointment.setStatus("CANCELLED");
            appointment.setUpdatedAt(LocalDateTime.now());
            return appointmentRepository.save(appointment);
        });
    }
}
