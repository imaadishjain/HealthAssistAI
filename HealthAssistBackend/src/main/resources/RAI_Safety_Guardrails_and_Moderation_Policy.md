---
id: KB-007
title: RAI Safety Guardrails & Moderation Policy
version: 2.3
department: responsible-ai
facility: main-hospital
tags: ["moderation","pii-redaction","policy","risk-tier","rai","escalation"]
effective_date: 2026-01-22
language: en
audience: ["ai-platform","compliance","clinical-ops"]
confidentiality: internal
---

# RAI Safety Guardrails & Moderation Policy
[KB-ID: KB-007]

## Principles & Boundaries
The assistant is designed to be **helpful, empathetic, and safe**. It must **not diagnose or prescribe**, must **ground** responses in verified hospital knowledge (RAG), and must **escalate** content that presents **medical risk** or policy concerns. PHI and PII should be **redacted** in all logs and prompts.

## Guardrail Pipeline
1. **Input Moderation:** Detect and block unsafe content (e.g., violence, explicit sexual content) and route **emergency**-like symptoms to triage per KB-004.  
2. **PII/PHI Redaction:** Replace names, MRNs, contact details with tokens before storage or model prompts.  
3. **Risk Tier Classifier:** Label exchanges as low/medium/high risk based on symptom cues; drive routing logic.  
4. **Policy Classifier:** Intercept attempts to solicit diagnosis or prescriptions; return safe policy explanations.  
5. **Output Moderation:** Validate tone, safety, and grounding before responding to the user.  
6. **Audit Logging:** Record decisions, tool calls, filters, and citations for compliance, without raw PHI.

## Language & Tone
The assistant should acknowledge user concerns and avoid minimizing distress. When declining unsafe requests, use **clear reasons** and offer **actionable alternatives** (e.g., “I can connect you to a nurse now.”).

## Human-in-the-Loop
**High-risk** outputs or ambiguous triage cases must queue a **nurse/doctor** review. The goal is to ensure clinical judgment where the model’s role is limited.

## Metrics & Continuous Improvement
Track refusal rates, escalation counts, redaction coverage, and near-miss events. Continuous auditing helps refine prompts, tools, and retrieval to lower hallucination and misrouting rates.

## Citations
Cite as: *[KB-007 §Guardrail Pipeline]*, *[KB-007 §Human-in-the-Loop]*.
