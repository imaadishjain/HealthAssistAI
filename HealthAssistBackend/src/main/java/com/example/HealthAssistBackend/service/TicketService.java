package com.example.HealthAssistBackend.service;

import com.example.HealthAssistBackend.model.Ticket;
import com.example.HealthAssistBackend.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Create a new facility/equipment incident ticket.
     */
    @Transactional
    public Ticket createTicket(String title, String description, String category,
                                String priority, String location, String equipmentId, String reportedBy) {
        log.info("Creating ticket: {} at location: {} reported by: {}", title, location, reportedBy);

        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCategory(category != null ? category : "EQUIPMENT");
        ticket.setPriority(priority != null ? priority : "MEDIUM");
        ticket.setLocation(location);
        ticket.setEquipmentId(equipmentId);
        ticket.setReportedBy(reportedBy);
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Get all tickets.
     */
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    /**
     * Get tickets by status.
     */
    public List<Ticket> getTicketsByStatus(String status) {
        return ticketRepository.findByStatus(status);
    }

    /**
     * Get tickets by category.
     */
    public List<Ticket> getTicketsByCategory(String category) {
        return ticketRepository.findByCategory(category);
    }

    /**
     * Get tickets by priority.
     */
    public List<Ticket> getTicketsByPriority(String priority) {
        return ticketRepository.findByPriority(priority);
    }

    /**
     * Update ticket status.
     */
    @Transactional
    public Optional<Ticket> updateTicketStatus(Long id, String status, String resolutionNotes) {
        return ticketRepository.findById(id).map(ticket -> {
            ticket.setStatus(status);
            if (resolutionNotes != null) {
                ticket.setResolutionNotes(resolutionNotes);
            }
            ticket.setUpdatedAt(LocalDateTime.now());
            return ticketRepository.save(ticket);
        });
    }

    /**
     * Get ticket by ID.
     */
    public Optional<Ticket> getTicketById(Long id) {
        return ticketRepository.findById(id);
    }
}
