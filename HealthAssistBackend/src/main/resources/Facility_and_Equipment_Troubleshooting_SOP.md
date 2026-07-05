---
id: KB-006
title: Facility & Equipment Troubleshooting SOP
version: 1.7
department: facilities
facility: main-hospital
tags: ["equipment","ticketing","troubleshooting","safety","priority"]
effective_date: 2026-01-20
language: en
audience: ["facilities","nursing","ai-assistant"]
confidentiality: internal
---

# Facility & Equipment Troubleshooting SOP
[KB-ID: KB-006]

## Scope & Safety Principle
This SOP governs the assistant’s role in identifying possible facility or equipment issues and initiating **incident tickets**. The assistant must **not** advise end-users to perform repairs. Clinical safety always comes first: if a device affects patient care and is malfunctioning, it should be marked **“Do Not Use”** and escalated promptly.

## Typical Devices & Scenarios
- **Ultrasound not powering on:** Check if the power cable is seated and the UPS/breaker status is normal. If the unit remains down, create a **Priority P1** ticket and advise staff to relocate the patient to a working unit if clinically required.  
- **ECG paper jam or error tones:** Consult the device manual if trained; if alarms persist, remove the device from service and raise a ticket.  
- **MRI environmental alarm:** Follow posted evacuation procedures and notify Facilities immediately; raise a ticket and keep the area restricted until verified safe.

## Ticket Creation & Priority Rules
Tickets must capture the **device type**, **room**, **asset tag**, a concise **description** of the issue, and **urgency**. Any malfunction that **directly impacts patient care** is **P1**. For example, a down ultrasound in **Room 205** during high-volume clinic hours warrants **P1** with an ETA request.  
**Hotline:** Ext. 2900 for immediate risks; raise the ticket in parallel.

## Tool Contract (Example)
Payload (CreateIncidentTicketTool):  
```
{
  "facility": "main-hospital",
  "location": "Tower B, Room 205",
  "category": "equipment",
  "deviceType": "Ultrasound",
  "assetTag": "US-205-AX12",
  "description": "Not powering on; power cable reseated; UPS light amber.",
  "priority": "P1"
}
```
Return `{ ticketId, status, eta }` and present a clear, brief summary to the user.

## Communication & Follow-Up
The assistant should inform the requester that the issue is logged, communicate the **ETA** if known, and advise temporary alternatives. A calm, helpful tone reduces stress and clarifies next steps. Ensure **audit logging** of ticket details without exposing raw PHI.

## Citations
Cite as: *[KB-006 §Ticket Creation & Priority Rules]*, *[KB-006 §Typical Devices & Scenarios]*.
