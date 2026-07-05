package com.example.HealthAssistBackend.controller;

import com.example.HealthAssistBackend.model.Ticket;
import com.example.HealthAssistBackend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Ticket controller for facility/equipment incident management.
 */
@RestController
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Tickets", description = "Facility / equipment incident tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Create a new incident ticket.
     * POST /ticket/create
     */
    @PostMapping("/ticket/create")
    public ResponseEntity<Ticket> createTicket(@RequestBody Map<String, String> request) {
        log.info("POST /ticket/create - title: {}", request.get("title"));

        Ticket ticket = ticketService.createTicket(
            request.get("title"),
            request.get("description"),
            request.getOrDefault("category", "EQUIPMENT"),
            request.getOrDefault("priority", "MEDIUM"),
            request.get("location"),
            request.get("equipmentId"),
            request.get("reportedBy")
        );

        return ResponseEntity.ok(ticket);
    }

    /**
     * Get all tickets.
     * GET /tickets
     */
    @GetMapping("/tickets")
    public ResponseEntity<List<Ticket>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        List<Ticket> tickets;
        if (status != null) {
            tickets = ticketService.getTicketsByStatus(status);
        } else if (category != null) {
            tickets = ticketService.getTicketsByCategory(category);
        } else {
            tickets = ticketService.getAllTickets();
        }
        return ResponseEntity.ok(tickets);
    }

    /**
     * Get a specific ticket.
     * GET /tickets/{id}
     */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<Ticket> getTicket(@PathVariable Long id) {
        return ticketService.getTicketById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update ticket status.
     * PUT /tickets/{id}/status
     */
    @PutMapping("/tickets/{id}/status")
    public ResponseEntity<Ticket> updateTicketStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return ticketService.updateTicketStatus(id, request.get("status"), request.get("resolutionNotes"))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
