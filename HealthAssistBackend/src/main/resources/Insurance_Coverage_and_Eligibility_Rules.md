---
id: KB-005
title: Insurance Coverage & Eligibility Rules
version: 2.0
department: insurance
facility: main-hospital
tags: ["insurance","eligibility","preauth","copay","deductible","billing"]
effective_date: 2026-01-18
language: en
audience: ["insurance-desk","contact-center","ai-assistant"]
confidentiality: internal
---

# Insurance Coverage & Eligibility Rules
[KB-ID: KB-005]

## Introduction
This document explains how to guide insurance-related questions safely, using **policy-led** statements rather than patient-specific benefits. The assistant should avoid definitive coverage promises and instead provide **coverage likelihood**, **pre-authorization requirements**, and **co-pay estimates**, while encouraging a final verification at the Insurance Desk.

## Plan Types & Typical Coverage
- **Standard:** Outpatient consults generally covered; some imaging (e.g., MRI/CT) often requires **pre-authorization** and may involve a **co-pay** (around 20%).  
- **Premium:** Broader coverage, including **telehealth** visits, and frequently reduced or no co-pay for imaging.  
- **Self-Pay:** Patients without insurance may request a **cost estimate** from Billing.

The assistant should frame responses as **policy guidance**: “Based on your **plan type**, MRI often requires pre-authorization under Standard plans. Our Insurance Desk can confirm your specific benefits.”

## Verification Workflow
1. **Collect plan type** and intended **service** (consult, MRI, lab). Avoid PHI in logs; use a **session alias**.  
2. Check the **coverage matrix**: does the plan typically cover the requested service?  
3. Determine if **pre-authorization** applies (e.g., MRI under Standard).  
4. Provide a **non-binding estimate** for co-pay/coverage range and guide the user to the Insurance Desk for exact confirmation.

## Telehealth Considerations
Telehealth is usually covered under **Premium** plans and may be covered under **Standard** for select specialties. Policy changes may occur; therefore, the assistant should present telehealth coverage as **likely** or **variable**, depending on plan and specialty.

## Responses & Safety
The assistant should never request sensitive identifiers in chat. When asked, “Does my plan cover MRI?” the correct approach is to state policy **tendencies** and immediately offer a **desk verification**. The goal is to be helpful, transparent, and avoid misrepresentations that could create financial harm.

## Integration & API
**Insurance Query Agent** may accept `{ planType, serviceType, department? }` and return `{ coverageLikely, preauthRequired, copayEstimateRange }`. All decisions and explanations should be **audited** for transparency and post-hoc review.

## Citations
Cite as: *[KB-005 §Plan Types & Typical Coverage]*, *[KB-005 §Verification Workflow]*, *[KB-005 §Responses & Safety]*.
