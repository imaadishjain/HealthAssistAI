import { api } from './api';

/** Get all recent audit logs. */
export function getAllAudits() {
  return api.get('/audit/all').then((r) => r.data);
}

/** Get all distinct workflow IDs for dropdown selection. */
export function getWorkflowIds() {
  return api.get('/audit/workflows').then((r) => r.data);
}

/** Get audit trail for a specific workflow. */
export function getAuditByWorkflow(workflowId) {
  return api.get(`/audit/${workflowId}`).then((r) => r.data);
}

/** Get all escalated audit entries. */
export function getEscalatedAudits() {
  return api.get('/audit/escalated').then((r) => r.data);
}

/** Get PII-detected audits. */
export function getPiiAudits() {
  return api.get('/audit/pii').then((r) => r.data);
}

/** Get moderation-flagged audits. */
export function getModerationFlaggedAudits() {
  return api.get('/audit/moderation').then((r) => r.data);
}
