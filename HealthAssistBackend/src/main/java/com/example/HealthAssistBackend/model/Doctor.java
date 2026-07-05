package com.example.HealthAssistBackend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(length = 200)
    private String specialty;

    @Column(nullable = false)
    private Boolean available = true;

    private String location;

    @Column(name = "next_available_slot")
    private LocalDateTime nextAvailableSlot;

    @Column(name = "consultation_fee")
    private BigDecimal consultationFee;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Doctor() {}

    public Doctor(String name, String department, String specialty, Boolean available, String location) {
        this.name = name;
        this.department = department;
        this.specialty = specialty;
        this.available = available;
        this.location = location;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getNextAvailableSlot() { return nextAvailableSlot; }
    public void setNextAvailableSlot(LocalDateTime nextAvailableSlot) { this.nextAvailableSlot = nextAvailableSlot; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
