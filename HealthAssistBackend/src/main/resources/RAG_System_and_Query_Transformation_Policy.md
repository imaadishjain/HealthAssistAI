---
id: KB-008
title: RAG System & Query Transformation Policy
version: 1.6
department: ai-platform
facility: main-hospital
tags: ["rag","retrieval","threshold","transformers","filters","reranking","compression"]
effective_date: 2026-01-24
language: en
audience: ["ai-platform","ml-engineering","observability"]
confidentiality: internal
---

# RAG System & Query Transformation Policy
[KB-ID: KB-008]

## Retrieval Controls & Grounding
The assistant is **RAG-first**. Set `retrieval_threshold >= 0.7` and `allowEmptyContext=false`. If no suitable context is retrieved, the system should **refuse safely**, explain why, and offer **human escalation** when appropriate. Limit context to **max 6 chunks** and apply **context compression** to reduce redundancy and risk.

## Dynamic Metadata Filters
Use metadata to focus retrieval and reduce noise:
- `department = cardiology`
- `symptom_category = respiratory`
- `facility = main-hospital`
- `insurance_plan = premium`

Filters should be applied based on **routing** and user intent, then logged in the **audit trail** to demonstrate due diligence.

## Query Transformation Strategies
- **Rewrite:** Clarify vague terms (e.g., “tightness” → “chest tightness symptom check”) to align with triage rules.  
- **Compression:** Summarize verbose patient narratives to their essential clinical and logistical elements before retrieval.  
- **Translation:** Convert multilingual inputs (e.g., Kannada → English) to unify retrieval; preserve the user’s output language preference.  
- **Multi-Query Expansion:** Break general terms like “stomach pain” into “abdominal pain,” “indigestion,” and “gastric discomfort” to broaden recall while relying on reranking to select the most relevant context.

## Post-Retrieval Processing
After retrieval, **compress** long SOP passages into safe summaries, **rerank** by safety relevance (e.g., red flags first), **deduplicate** overlapping passages, and **cite** at least one KB source by ID and section. If conflicts arise, prefer **newer versions** and **department-authoritative** documents.

## Safety & Fail-Safes
If the assistant detects a mismatch between user intent and retrieved content, it should **re-query** with revised filters, or escalate. Avoid non-grounded advice; when necessary, respond with a **safe refusal** and offer a pathway to human assistance.

## Citations
Cite as: *[KB-008 §Retrieval Controls & Grounding]*, *[KB-008 §Query Transformation Strategies]*, *[KB-008 §Post-Retrieval Processing]*.
