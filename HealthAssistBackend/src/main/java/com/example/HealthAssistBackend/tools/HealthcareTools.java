package com.example.HealthAssistBackend.tools;

import com.example.HealthAssistBackend.model.Appointment;
import com.example.HealthAssistBackend.model.Doctor;
import com.example.HealthAssistBackend.model.Ticket;
import com.example.HealthAssistBackend.model.TriageResult;
import com.example.HealthAssistBackend.model.RiskTier;
import com.example.HealthAssistBackend.service.AppointmentService;
import com.example.HealthAssistBackend.service.TicketService;
import com.example.HealthAssistBackend.guardrail.RiskClassifierService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Healthcare AI tools exposed as Spring AI function beans.
 * Each tool can be invoked by the LLM during conversation to perform actions.
 */
@Configuration
public class HealthcareTools {

    private final AppointmentService appointmentService;
    private final TicketService ticketService;
    private final RiskClassifierService riskClassifierService;

    public HealthcareTools(AppointmentService appointmentService,
                            TicketService ticketService,
                            RiskClassifierService riskClassifierService) {
        this.appointmentService = appointmentService;
        this.ticketService = ticketService;
        this.riskClassifierService = riskClassifierService;
    }

    // ======== Tool Request/Response Records ========

    public record DoctorAvailabilityRequest(String department, String specialty) {}
    public record DoctorAvailabilityResponse(List<DoctorInfo> availableDoctors, String message) {}
    public record DoctorInfo(Long id, String name, String department, String specialty, String location, String nextAvailableSlot) {}

    public record CreateAppointmentRequest(String patientName, String patientEmail, String patientPhone,
                                            Long doctorId, String department, String dateTime, String reason) {}
    public record CreateAppointmentResponse(Long appointmentId, String doctorName, String department,
                                             String dateTime, String status, String message) {}

    public record CreateTicketRequest(String title, String description, String category, String priority,
                                       String location, String equipmentId, String reportedBy) {}
    public record CreateTicketResponse(Long ticketId, String status, String priority, String message) {}

    public record TriageRequest(String symptoms, String patientAge, String additionalInfo) {}
    public record TriageResponse(String severity, String riskTier, String recommendedDepartment,
                                  String recommendation, boolean escalationRequired, String disclaimer) {}

    public record DepartmentRouterRequest(String symptoms, String queryType) {}
    public record DepartmentRouterResponse(String recommendedDepartment, String reason, List<String> alternativeDepartments) {}

    public record LookupAppointmentsRequest(Long id, String patientName, String department, String date, String status) {}
    public record LookupAppointmentsResponse(List<AppointmentInfo> appointments, int total, String message) {}
    public record AppointmentInfo(Long id, String patientName, String doctorName, String department,
                                   String dateTime, String status, String reason) {}

    public record LookupTicketsRequest(Long id, String category, String priority, String reportedBy, String status) {}
    public record LookupTicketsResponse(List<TicketInfo> tickets, int total, String message) {}
    public record TicketInfo(Long id, String title, String category, String priority, String status,
                              String location, String reportedBy, String createdAt) {}

    // ======== Tool Beans ========

    @Bean
    @Description("Check availability of doctors by department or specialty. Returns list of available doctors with their next available appointment slots.")
    public Function<DoctorAvailabilityRequest, DoctorAvailabilityResponse> checkDoctorAvailability() {
        return request -> {
            List<Doctor> doctors = appointmentService.checkAvailability(request.department(), request.specialty());

            List<DoctorInfo> doctorInfos = doctors.stream()
                .map(d -> new DoctorInfo(d.getId(), d.getName(), d.getDepartment(), d.getSpecialty(),
                    d.getLocation(), d.getNextAvailableSlot() != null ? d.getNextAvailableSlot().toString() : "Contact front desk"))
                .collect(Collectors.toList());

            String message = doctorInfos.isEmpty()
                ? "No available doctors found for the specified criteria. Please try a different department or contact the front desk."
                : doctorInfos.size() + " doctor(s) available.";

            return new DoctorAvailabilityResponse(doctorInfos, message);
        };
    }

    @Bean
    @Description("Create a new appointment for a patient with a specific doctor or department. Requires patient name, department, and preferred date/time.")
    public Function<CreateAppointmentRequest, CreateAppointmentResponse> createAppointment() {
        return request -> {
            LocalDateTime dateTime;
            try {
                dateTime = LocalDateTime.parse(request.dateTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                dateTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            }

            Appointment apt = appointmentService.createAppointment(
                request.patientName(), request.patientEmail(), request.patientPhone(),
                request.doctorId(), request.department(), dateTime, request.reason()
            );

            return new CreateAppointmentResponse(
                apt.getId(), apt.getDoctorName(), apt.getDepartment(),
                apt.getAppointmentDateTime().toString(), apt.getStatus(),
                "Appointment successfully scheduled. Please arrive 15 minutes early. Bring your insurance card and photo ID."
            );
        };
    }

    @Bean
    @Description("Create an incident or facility ticket for equipment malfunction, room issues, or other facility problems. Requires title, description, and location.")
    public Function<CreateTicketRequest, CreateTicketResponse> createIncidentTicket() {
        return request -> {
            Ticket ticket = ticketService.createTicket(
                request.title(), request.description(), request.category(),
                request.priority(), request.location(), request.equipmentId(), request.reportedBy()
            );

            return new CreateTicketResponse(
                ticket.getId(), ticket.getStatus(), ticket.getPriority(),
                "Incident ticket #" + ticket.getId() + " created. Our facilities team has been notified and will respond based on priority level: " + ticket.getPriority() + "."
            );
        };
    }

    @Bean
    @Description("Perform a triage assessment based on patient symptoms. Classifies risk level and recommends appropriate department. IMPORTANT: Always include disclaimer that this is not a medical diagnosis.")
    public Function<TriageRequest, TriageResponse> triageAssessment() {
        return request -> {
            String fullDescription = request.symptoms();
            if (request.additionalInfo() != null) {
                fullDescription += " " + request.additionalInfo();
            }
            if (request.patientAge() != null) {
                fullDescription += " Patient age: " + request.patientAge();
            }

            RiskClassifierService.RiskClassification risk = riskClassifierService.classify(fullDescription);
            String department = mapSymptomsToDepartment(request.symptoms());

            TriageResult result;
            switch (risk.riskTier()) {
                case CRITICAL -> result = TriageResult.critical(List.of(request.symptoms()));
                case HIGH -> result = TriageResult.highRisk(department, risk.reasoning(), List.of(request.symptoms()));
                case MEDIUM -> result = TriageResult.mediumRisk(department, risk.reasoning(), List.of(request.symptoms()));
                default -> result = TriageResult.lowRisk(department, risk.reasoning(), List.of(request.symptoms()));
            }

            return new TriageResponse(
                result.severity(), result.riskTier().name(), result.recommendedDepartment(),
                result.recommendation(), result.escalationRequired(), result.disclaimer()
            );
        };
    }

    @Bean
    @Description("Route a medical query to the most appropriate hospital department based on symptoms or query type.")
    public Function<DepartmentRouterRequest, DepartmentRouterResponse> medicalDepartmentRouter() {
        return request -> {
            String department = mapSymptomsToDepartment(request.symptoms());
            List<String> alternatives = getAlternativeDepartments(department);
            String reason = "Based on the described symptoms/query, the " + department +
                " department is the most appropriate first point of contact.";

            return new DepartmentRouterResponse(department, reason, alternatives);
        };
    }

    @Bean
    @Description("Look up existing appointments. Can filter by appointment ID, patient name, department (e.g. Cardiology, Oncology), date (YYYY-MM-DD format), and/or status (SCHEDULED, COMPLETED, CANCELLED). Returns appointment details including doctor, department, date/time.")
    public Function<LookupAppointmentsRequest, LookupAppointmentsResponse> lookupAppointments() {
        return request -> {
            List<Appointment> appointments;

            // ID-based lookup takes highest priority
            if (request.id() != null) {
                appointments = appointmentService.getAppointmentById(request.id())
                    .map(List::of).orElse(List.of());
            } else if (request.department() != null && !request.department().isBlank()) {
                appointments = appointmentService.getAppointmentsByDepartment(request.department());
            } else if (request.patientName() != null && !request.patientName().isBlank()) {
                appointments = appointmentService.getAppointmentsByPatient(request.patientName());
            } else {
                appointments = appointmentService.getAllAppointments();
            }

            // Apply additional filters on top of the primary result
            if (request.status() != null && !request.status().isBlank()) {
                appointments = appointments.stream()
                    .filter(a -> a.getStatus().equalsIgnoreCase(request.status()))
                    .toList();
            }
            if (request.date() != null && !request.date().isBlank()) {
                try {
                    java.time.LocalDate targetDate = java.time.LocalDate.parse(request.date());
                    appointments = appointments.stream()
                        .filter(a -> a.getAppointmentDateTime() != null
                            && a.getAppointmentDateTime().toLocalDate().equals(targetDate))
                        .toList();
                } catch (Exception ignored) { /* skip date filter if unparseable */ }
            }

            List<AppointmentInfo> infos = appointments.stream()
                .map(a -> new AppointmentInfo(a.getId(), a.getPatientName(), a.getDoctorName(),
                    a.getDepartment(),
                    a.getAppointmentDateTime() != null ? a.getAppointmentDateTime().toString() : "N/A",
                    a.getStatus(), a.getReason()))
                .collect(Collectors.toList());

            String message = infos.isEmpty()
                ? "No appointments found matching the given criteria."
                : infos.size() + " appointment(s) found.";

            return new LookupAppointmentsResponse(infos, infos.size(), message);
        };
    }

    @Bean
    @Description("Look up existing facility/equipment tickets. Can filter by ticket ID, category (e.g. Equipment Malfunction, Room Issue), priority (CRITICAL, HIGH, MEDIUM, LOW), reporter name, and/or status (OPEN, IN_PROGRESS, RESOLVED, CLOSED). Returns ticket details including title, category, priority, and status.")
    public Function<LookupTicketsRequest, LookupTicketsResponse> lookupTickets() {
        return request -> {
            List<Ticket> tickets;

            // ID-based lookup takes highest priority
            if (request.id() != null) {
                tickets = ticketService.getTicketById(request.id())
                    .map(List::of).orElse(List.of());
            } else if (request.category() != null && !request.category().isBlank()) {
                tickets = ticketService.getTicketsByCategory(request.category());
            } else if (request.priority() != null && !request.priority().isBlank()) {
                tickets = ticketService.getTicketsByPriority(request.priority());
            } else if (request.reportedBy() != null && !request.reportedBy().isBlank()) {
                tickets = ticketService.getAllTickets().stream()
                    .filter(t -> t.getReportedBy() != null && t.getReportedBy().toLowerCase().contains(request.reportedBy().toLowerCase()))
                    .toList();
            } else {
                tickets = ticketService.getAllTickets();
            }

            // Apply additional status filter on top of primary result
            if (request.status() != null && !request.status().isBlank()) {
                tickets = tickets.stream()
                    .filter(t -> t.getStatus().equalsIgnoreCase(request.status()))
                    .toList();
            }

            List<TicketInfo> infos = tickets.stream()
                .map(t -> new TicketInfo(t.getId(), t.getTitle(), t.getCategory(), t.getPriority(),
                    t.getStatus(), t.getLocation(), t.getReportedBy(),
                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : "N/A"))
                .collect(Collectors.toList());

            String message = infos.isEmpty()
                ? "No tickets found matching the given criteria."
                : infos.size() + " ticket(s) found.";

            return new LookupTicketsResponse(infos, infos.size(), message);
        };
    }

    // ======== Helper Methods ========

    private String mapSymptomsToDepartment(String symptoms) {
        if (symptoms == null) return "General Medicine";
        String lower = symptoms.toLowerCase();

        if (lower.contains("chest") || lower.contains("heart") || lower.contains("cardiac") || lower.contains("palpitation"))
            return "Cardiology";
        if (lower.contains("head") || lower.contains("neuro") || lower.contains("seizure") || lower.contains("migraine") || lower.contains("stroke"))
            return "Neurology";
        if (lower.contains("bone") || lower.contains("joint") || lower.contains("fracture") || lower.contains("sprain") || lower.contains("orthop"))
            return "Orthopedics";
        if (lower.contains("breath") || lower.contains("lung") || lower.contains("cough") || lower.contains("asthma") || lower.contains("respiratory"))
            return "Pulmonology";
        if (lower.contains("stomach") || lower.contains("digest") || lower.contains("abdomen") || lower.contains("gastric") || lower.contains("liver"))
            return "Gastroenterology";
        if (lower.contains("child") || lower.contains("infant") || lower.contains("pediatric") || lower.contains("baby"))
            return "Pediatrics";
        if (lower.contains("skin") || lower.contains("rash") || lower.contains("derma") || lower.contains("itch"))
            return "Dermatology";
        if (lower.contains("mental") || lower.contains("anxiety") || lower.contains("depression") || lower.contains("psychiatric"))
            return "Psychiatry";
        if (lower.contains("cancer") || lower.contains("tumor") || lower.contains("oncol"))
            return "Oncology";
        if (lower.contains("eye") || lower.contains("vision") || lower.contains("ophthal"))
            return "Ophthalmology";
        if (lower.contains("ear") || lower.contains("nose") || lower.contains("throat") || lower.contains("sinus")
            || java.util.regex.Pattern.compile("\\bent\\b").matcher(lower).find())
            return "ENT";
        if (lower.contains("pregnan") || lower.contains("gynec") || lower.contains("menstr") || lower.contains("obstet"))
            return "Gynecology";
        if (lower.contains("emergency") || lower.contains("trauma") || lower.contains("accident") || lower.contains("severe"))
            return "Emergency Medicine";
        if (lower.contains("x-ray") || lower.contains("mri") || lower.contains("ct scan") || lower.contains("imaging") || lower.contains("ultrasound"))
            return "Radiology";

        return "General Medicine";
    }

    private List<String> getAlternativeDepartments(String primary) {
        return switch (primary) {
            case "Cardiology" -> List.of("Emergency Medicine", "General Medicine");
            case "Neurology" -> List.of("Emergency Medicine", "Psychiatry");
            case "Pulmonology" -> List.of("Emergency Medicine", "General Medicine");
            case "Gastroenterology" -> List.of("General Medicine", "Oncology");
            case "Emergency Medicine" -> List.of("General Medicine");
            default -> List.of("General Medicine", "Emergency Medicine");
        };
    }
}
