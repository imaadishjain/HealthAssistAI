import { api } from './api';

/** Create a new incident ticket. */
export function createTicket(request) {
  return api.post('/ticket/create', request).then((r) => r.data);
}

/** Get all tickets with optional filters. */
export function getAllTickets(status, category) {
  const params = {};
  if (status) params.status = status;
  if (category) params.category = category;
  return api.get('/tickets', { params }).then((r) => r.data);
}

/** Get a specific ticket. */
export function getTicket(id) {
  return api.get(`/tickets/${id}`).then((r) => r.data);
}

/** Update ticket status. */
export function updateTicketStatus(id, status, resolutionNotes) {
  return api
    .put(`/tickets/${id}/status`, { status, resolutionNotes })
    .then((r) => r.data);
}
