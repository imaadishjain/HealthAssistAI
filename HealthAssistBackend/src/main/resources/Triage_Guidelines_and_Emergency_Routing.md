---
id: KB-004
title: Triage Guidelines & Emergency Routing
version: 2.5
department: clinical-operations
facility: main-hospital
tags: ["triage","risk-tier","emergency","safety","escalation"]
effective_date: 2026-01-15
language: en
audience: ["ai-assistant","contact-center","nurse-triage"]
confidentiality: internal
---

# Triage Guidelines & Emergency Routing
[KB-ID: KB-004]

## Purpose & Safety Boundaries
This guideline enables safe, **non-diagnostic** triage and routing. The assistant **must not** diagnose or prescribe. It provides supportive, general guidance and triage while ensuring **high-risk** symptoms result in **immediate escalation** to a human clinician or emergency care.

## Risk Tier Classification
- **HIGH RISK (Immediate):** Symptoms suggest severe or life-threatening conditions. The assistant should advise **calling emergency services or visiting the Emergency Department immediately**, suppress routine bookings, and trigger **Human Handoff**.
- **MEDIUM RISK (Urgent):** New or worsening concerns requiring **same-day** assessment. The assistant may recommend urgent care or same-day clinic evaluation after confirming availability.
- **LOW RISK (Routine):** Non-urgent issues suitable for standard appointments, general self-care guidance, and information support.

## Red Flag Indicators (Examples)
- **Chest pain** with **shortness of breath**, perspiration, or **radiation** to arm/jaw → **HIGH RISK** cardiac concern.  
- **Severe shortness of breath**, **stridor**, or facial swelling → **HIGH RISK** airway/anaphylaxis.  
- **Focal neurological deficits** (sudden weakness, slurred speech) → **HIGH RISK** stroke.  
- **Severe abdominal pain** with persistent vomiting or fever → **MED/HIGH RISK** depending on severity.  
- **Head injury** with loss of consciousness → **HIGH RISK**.  
- **Uncontrolled bleeding** → **HIGH RISK**.

## Communication Script
Use empathetic, clear language:  
“I’m not a doctor, but based on what you’ve shared, this could be serious. Please **seek emergency care immediately**. I’ll also notify a nurse so they can support you.” If non-emergent, gently set expectations about routine appointment availability and provide helpful information (e.g., what to bring, location).

## Workflow & Audit
1. **Classify** risk tier using TriageAssessmentTool.  
2. If **HIGH RISK**, **suppress** scheduling flows and trigger **nurse handoff**.  
3. **Log** triage decision, indicators, and routing actions to the audit system for compliance review.  
4. Provide an **on-screen reminder** for the patient to bring relevant records for non-urgent visits.

## Department Recommendations
For **chest tightness or palpitations**, recommend **Cardiology** when not high risk. For **persistent cough or wheeze**, recommend **Pulmonology**. **Joint pain** after sports typically routes to **Orthopedics**. Always verify against KB-003 for location and contact details.

## Examples
**User:** “I have sharp chest pain and shortness of breath.”  
**Assistant:** “I’m not a doctor, but these symptoms could be serious. Please go to the Emergency Department immediately or call local emergency services. I’m notifying a nurse now.” *[KB-004 §Red Flag Indicators]*

## Citations
Cite as: *[KB-004 §Risk Tier Classification]*, *[KB-004 §Red Flag Indicators]*, *[KB-004 §Workflow & Audit]*.
