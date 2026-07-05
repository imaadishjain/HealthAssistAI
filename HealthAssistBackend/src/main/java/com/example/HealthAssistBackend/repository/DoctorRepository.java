package com.example.HealthAssistBackend.repository;

import com.example.HealthAssistBackend.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findByDepartmentIgnoreCase(String department);

    List<Doctor> findBySpecialtyContainingIgnoreCase(String specialty);

    List<Doctor> findByDepartmentIgnoreCaseAndAvailableTrue(String department);

    List<Doctor> findByAvailableTrue();

    List<Doctor> findByNameContainingIgnoreCase(String name);

    List<Doctor> findByLocationContainingIgnoreCase(String location);
}
