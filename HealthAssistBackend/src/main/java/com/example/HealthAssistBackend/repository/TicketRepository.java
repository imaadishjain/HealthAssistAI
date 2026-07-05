package com.example.HealthAssistBackend.repository;

import com.example.HealthAssistBackend.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByCategory(String category);

    List<Ticket> findByStatus(String status);

    List<Ticket> findByPriority(String priority);

    List<Ticket> findByLocationContainingIgnoreCase(String location);

    List<Ticket> findByReportedBy(String reportedBy);

    List<Ticket> findByEquipmentId(String equipmentId);
}
