package com.example.HealthAssistBackend.repository;

import com.example.HealthAssistBackend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientNameContainingIgnoreCase(String patientName);

    List<Appointment> findByDoctorNameContainingIgnoreCase(String doctorName);

    List<Appointment> findByDepartmentIgnoreCase(String department);

    List<Appointment> findByStatus(String status);

    List<Appointment> findByAppointmentDateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Appointment> findByDoctorIdAndAppointmentDateTimeBetween(Long doctorId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findByPatientNameContainingIgnoreCaseAndStatus(String patientName, String status);
}
