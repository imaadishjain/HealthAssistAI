---
id: KB-009
title: Privacy, PHI & Data Handling Standard
version: 1.9
department: compliance
facility: main-hospital
tags: ["privacy","phi","security","logging","encryption","retention"]
effective_date: 2026-01-26
language: en
audience: ["it-security","ai-platform","compliance","devops"]
confidentiality: restricted
---

# Privacy, PHI & Data Handling Standard
[KB-ID: KB-009]

## PHI and PII Overview
PHI includes any data that can identify a patient in a healthcare context (names, MRNs, diagnosis details, care-related dates). PII includes contact details like emails and phone numbers. This standard mandates **data minimization**, **strong encryption**, and **access controls** for all systems handling these data types.

## Data Minimization & Redaction
AI workflows must use a **session-level alias** and avoid storing raw PHI in logs. Before any content is stored or used to prompt a model, **PII/PHI must be redacted** or tokenized. Training or evaluation corpora derived from conversations must not include direct identifiers.

## Encryption & Transport
All services must enforce **TLS 1.2+** for data in transit and **AES-256** or comparable encryption for data at rest (databases, object stores, and backups). Key management should follow **least privilege** and rotate keys on a defined schedule.

## Access Control & Monitoring
Implement **role-based access control (RBAC)** with just-in-time elevation where possible. Perform regular access reviews and **segregation of duties** between engineering and operations. Observability must include **structured logging** without raw PHI, capturing only necessary metadata to reconstruct decision paths.

## Audit & Retention
**Audit logs** should record tool calls, filter expressions, risk tiers, citations, and escalation events. Retain **conversational logs** for **30–90 days** without PHI. Appointment and ticketing records follow local hospital policies and legal requirements.

## Incident Response
Suspected breaches must be reported to Compliance within **24 hours**, following the Incident Response Plan (IRP). Post-incident reviews should identify control gaps and yield measurable improvements.

## Citations
Cite as: *[KB-009 §Data Minimization & Redaction]*, *[KB-009 §Audit & Retention]*, *[KB-009 §Encryption & Transport]*.
