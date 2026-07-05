---
id: KB-010
title: Tool Schemas, Agent Workflows & API Contracts
version: 2.2
department: ai-platform
facility: main-hospital
tags: ["tools","agents","contracts","workflows","audit","streaming"]
effective_date: 2026-01-28
language: en
audience: ["backend","ml-engineering","qa","observability"]
confidentiality: internal
---

# Tool Schemas, Agent Workflows & API Contracts
[KB-ID: KB-010]

## Overview
This document defines tool schemas, agent orchestration patterns, and API expectations that enable the assistant to **check availability**, **book appointments**, **triage symptoms**, and **create facility tickets**. All tools must return **machine-readable** outputs and **human-friendly** summaries to support both automation and transparency.

## Tool Schemas

### CheckDoctorAvailabilityTool
**Input** includes specialty, optional doctorId, dateRange, and facility; **output** returns a list of candidate slots. The assistant summarizes these slots, grouping by doctor and mode (in-person/telehealth), and prompts the user to choose.  
**Input Example:**
```
{
  "specialty": "cardiology",
  "doctorId": "A123?",
  "dateRange": {"from": "2026-03-03", "to": "2026-03-10"},
  "facility": "main-hospital"
}
```
**Output Example:**
```
{
  "slots": [
    {"slotId": "SLOT-001", "doctorId": "A123", "start": "2026-03-05T10:30", "mode": "in-person"},
    {"slotId": "SLOT-017", "doctorId": "A145", "start": "2026-03-06T16:00", "mode": "telehealth"}
  ]
}
```

### CreateAppointmentTool
Upon user confirmation, create an appointment with a **patientAlias** (no PHI), **contact tokens**, and the chosen **slotId**.  
**Input Example:**
```
{
  "patientAlias": "PAT-ALIAS-xyz",
  "contact": { "phone": "REDACTED", "email": "REDACTED" },
  "specialty": "cardiology",
  "doctorId": "A123",
  "slotId": "SLOT-001",
  "mode": "in-person"
}
```
**Output Example:**
```
{
  "appointmentId": "APPT-58421",
  "status": "confirmed",
  "instructions": "Arrive 15 minutes early; bring prior reports."
}
```

### CreateIncidentTicketTool
Used for facility/equipment issues; prioritize **P1** when patient care is impacted.  
**Input Example:**
```
{
  "facility": "main-hospital",
  "location": "Tower B, Room 205",
  "category": "equipment",
  "deviceType": "Ultrasound",
  "assetTag": "US-205-AX12",
  "description": "Not powering on; UPS amber; breaker reset attempted.",
  "priority": "P1"
}
```
**Output Example:**
```
{
  "ticketId": "INC-92015",
  "status": "open",
  "eta": "2h"
}
```

### TriageAssessmentTool
Assesses risk tier based on primary symptoms, onset, and red flags; output includes **riskTier**, **rationale**, and **nextStep**.  
**Output Example:**
```
{
  "riskTier": "HIGH",
  "rationale": "Chest pain + SOB + radiation are cardiac red flags",
  "nextStep": "Emergency routing",
  "recommendedDepartment": "cardiology"
}
```

### MedicalDepartmentRouterTool
Routes generalized symptom descriptions to the most suitable department with a **confidence** score.

## Agentic Workflows

### Symptom Checker Agent
1. Gather symptoms respectfully.  
2. Call **TriageAssessmentTool** → classify risk.  
3. If LOW/MED, call **MedicalDepartmentRouterTool** and **CheckDoctorAvailabilityTool** to offer appointments with a **safety disclaimer**.  
4. Cite **KB-004** for triage and **KB-003** for department info; rely on **KB-002** for booking rules.

### Emergency Routing Agent
If **HIGH RISK**, present clear instructions to seek emergency care, trigger a **nurse handoff**, and **suppress** appointment creation. Log the decision with rationale.

### Insurance Query Agent
Retrieve **KB-005** to determine coverage likelihood and pre-authorization needs; invite the patient to verify details at the Insurance Desk for exact benefits.

### Medical Equipment Fault Agent
Collect device details, apply **KB-006** safety checks, and create tickets. When the device supports patient care and is down, mark **Priority P1** and communicate ETA if available.

## API Surface & Streaming
- `/ai/chat/sync` returns a complete response when latency is acceptable.  
- `/ai/chat/async` streams tokens for a responsive UX during longer retrieval or tool calls.  
- `/ai/agent/health` orchestrates planner-driven, multi-tool workflows and returns a **workflowId** for audit retrieval.  

## Audit & Observability
`/audit/{workflowId}` returns **steps**, **tools**, **filters**, **citations**, **riskTier**, and **redactionApplied**. Include structured timestamps and versioned document IDs for compliance.

## Citations
Cite as: *[KB-010 §Tool Schemas]*, *[KB-010 §Agentic Workflows]*, *[KB-010 §API Surface & Streaming]*.
