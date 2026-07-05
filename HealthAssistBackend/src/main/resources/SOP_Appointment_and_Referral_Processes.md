---
id: KB-002
title: Appointment & Referral SOP
version: 2.1
department: administration
facility: main-hospital
tags: ["appointments","referrals","eligibility","waitlist","follow-up","telehealth"]
effective_date: 2026-01-12
language: en
audience: ["front-desk","care-coordinators","contact-center","ai-assistant"]
confidentiality: internal
---

# Appointment & Referral SOP
[KB-ID: KB-002]

## Overview
This SOP standardizes scheduling rules, referral workflows, and escalation logic to ensure timely access to care while avoiding unsafe bookings for high-risk patients. The assistant should guide users with **clear, non-clinical** language and escalate to a human when uncertainty or medical risk is detected.

## Appointment Types & Durations
Appointments are offered to align patient needs with provider capacity:
- **New Consultation (30 min):** Appropriate for first-time specialty visits; allows for history review and initial assessment.
- **Follow-Up (15 min):** Intended for ongoing management; must be booked with the **same specialty** and preferably the same physician when available.
- **Telehealth (20 min):** Offered by select specialties; appropriate for review of results, medication questions, or routine follow-ups when in-person exam is not required.
- **Procedure Slots (variable):** Booked only after a qualifying consult; nurse or physician teams confirm pre-procedure requirements.

## Booking Rules & Identification
For each booking, collect **non-sensitive identifiers** (e.g., name, DoB month/year, and contact alias) and avoid storing PHI in chat logs. The system must validate **specialty**, **doctor**, and **facility**. Next-day schedules are **released at 18:00 daily** to ensure fairness. After **two no-shows**, a deposit may be required; the assistant should present a polite reminder to set expectations without judgment.

## Referral Management
**Internal referrals** are created in the EMR and reach the destination specialty triage queue within **48 hours**. **External referrals** can be provisionally scheduled based on uploaded letters; such bookings carry a **“pending triage”** flag until the specialty approves. The assistant should explain that provisional times may shift if triage recommends a more suitable pathway.

## Follow-Up Windows by Specialty
Specialties publish typical follow-up windows to harmonize demand with clinical appropriateness:
- **Cardiology:** **1–2 weeks** post-visit, especially after medication changes or test results.
- **Orthopedics:** **2–4 weeks** for conservative management or post-procedure review.
- **Pediatrics:** As advised; often **1–2 weeks** for acute conditions or immunization follow-ups.

## Escalations & Safety
If user inputs indicate **high-risk symptoms** (e.g., chest pain with shortness of breath), the assistant must **not** proceed with routine scheduling. Instead, route to the **Emergency workflow** and hand off to a nurse as per KB-004. If a physician is unavailable for more than **14 days**, offer an **alternate provider** in the same specialty or location.

## APIs / Tool Contracts
- **Check Availability:** `/appointments/check?specialty&doctorId&dateRange&facility`  
- **Create Appointment:** `/appointments/create` with `{ patientAlias, contact, specialty, doctorId?, slotId, mode }`  
All tool calls and results should be **logged to the audit trail** and returned with **clear, user-friendly summaries**.

## Example Dialogue (Grounded)
**User:** “How do I schedule a follow-up with cardiology next week?”  
**Assistant:** “I can help check cardiology slots for next week and book a **15-minute follow-up** if your cardiologist is available. If not, I’ll suggest an alternate cardiologist. Shall I check availability now?” *[KB-002 §Follow-Up Windows]*

## Citations
Cite as: *[KB-002 §Booking Rules & Identification]*, *[KB-002 §Referral Management]*, *[KB-002 §Follow-Up Windows by Specialty]*.
