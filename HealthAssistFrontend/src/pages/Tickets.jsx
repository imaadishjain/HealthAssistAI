import { useEffect, useMemo, useState } from 'react';
import {
  getAllTickets,
  createTicket,
  updateTicketStatus,
} from '../services/ticketService';
import { formatDate } from '../utils/format';
import './Tickets.css';

const CATEGORIES = [
  'Equipment Malfunction',
  'Facility Issue',
  'Safety Hazard',
  'IT System',
  'Maintenance',
  'Other',
];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

const EMPTY_FORM = {
  title: '',
  description: '',
  category: '',
  priority: 'MEDIUM',
  location: '',
  equipmentId: '',
  reportedBy: '',
};

function getPriorityClass(priority) {
  return 'priority-' + priority.toLowerCase();
}
function getStatusClass(status) {
  return 'status-' + status.toLowerCase().replace('_', '-');
}

export default function Tickets() {
  const [tickets, setTickets] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const [formData, setFormData] = useState({ ...EMPTY_FORM });

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');

  const loadTickets = () => {
    setIsLoading(true);
    getAllTickets()
      .then((data) => {
        setTickets(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load tickets.');
        setIsLoading(false);
      });
  };

  useEffect(() => {
    loadTickets();
  }, []);

  const filteredTickets = useMemo(() => {
    let list = tickets;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (t) =>
          t.title?.toLowerCase().includes(q) ||
          t.description?.toLowerCase().includes(q) ||
          t.reportedBy?.toLowerCase().includes(q) ||
          t.location?.toLowerCase().includes(q) ||
          String(t.id).includes(q)
      );
    }
    if (statusFilter) list = list.filter((t) => t.status === statusFilter);
    if (priorityFilter) list = list.filter((t) => t.priority === priorityFilter);
    if (categoryFilter) list = list.filter((t) => t.category === categoryFilter);
    return list;
  }, [tickets, searchQuery, statusFilter, priorityFilter, categoryFilter]);

  const clearFilters = () => {
    setSearchQuery('');
    setStatusFilter('');
    setPriorityFilter('');
    setCategoryFilter('');
  };

  const resetForm = () => setFormData({ ...EMPTY_FORM });

  const submitForm = (e) => {
    e.preventDefault();
    if (!formData.title || !formData.category) {
      setErrorMsg('Please fill in title and category.');
      return;
    }
    setIsSubmitting(true);
    setErrorMsg('');
    setSuccessMsg('');

    createTicket(formData)
      .then((ticket) => {
        setSuccessMsg(`Ticket created! (ID: ${ticket.id})`);
        setIsSubmitting(false);
        setShowForm(false);
        loadTickets();
        resetForm();
      })
      .catch((err) => {
        setErrorMsg(err?.response?.data?.message || 'Failed to create ticket.');
        setIsSubmitting(false);
      });
  };

  const handleUpdateStatus = (ticket, newStatus) => {
    updateTicketStatus(ticket.id, newStatus)
      .then(() => {
        loadTickets();
        setSuccessMsg(`Ticket #${ticket.id} updated to ${newStatus}.`);
      })
      .catch(() => setErrorMsg('Failed to update ticket status.'));
  };

  return (
    <div className="tickets-page">
      {/* Page Hero */}
      <div className="page-hero">
        <div className="page-hero-grid">
          <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern
                id="tickGrid"
                x="0"
                y="0"
                width="32"
                height="32"
                patternUnits="userSpaceOnUse"
              >
                <circle cx="1" cy="1" r="1" fill="rgba(245,158,11,0.1)" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#tickGrid)" />
          </svg>
        </div>
        <div className="page-hero-orb"></div>
        <div className="page-hero-content">
          <div className="page-hero-left">
            <div className="page-hero-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="white"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4.5c-.77-.833-2.694-.833-3.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
            </div>
            <div>
              <h1>Incident Tickets</h1>
              <p>Report and track facility, equipment, and safety issues</p>
            </div>
          </div>
          <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
            {!showForm && (
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            )}
            {showForm && (
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            )}
            {showForm ? 'Close' : 'New Ticket'}
          </button>
        </div>
      </div>

      {/* Messages */}
      {successMsg && (
        <div className="alert success">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          {successMsg}
        </div>
      )}
      {errorMsg && (
        <div className="alert error">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
          {errorMsg}
        </div>
      )}

      {/* Ticket Form */}
      {showForm && (
        <div className="form-card">
          <h2>Report an Issue</h2>
          <form onSubmit={submitForm}>
            <div className="form-row">
              <div className="form-group">
                <label>Title *</label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  placeholder="Brief issue summary..."
                  required
                />
              </div>
              <div className="form-group">
                <label>Category *</label>
                <select
                  value={formData.category}
                  onChange={(e) =>
                    setFormData({ ...formData, category: e.target.value })
                  }
                  required
                >
                  <option value="">Select Category</option>
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-group full-width">
              <label>Description</label>
              <textarea
                value={formData.description}
                onChange={(e) =>
                  setFormData({ ...formData, description: e.target.value })
                }
                rows={3}
                placeholder="Describe the issue in detail..."
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Priority</label>
                <select
                  value={formData.priority}
                  onChange={(e) =>
                    setFormData({ ...formData, priority: e.target.value })
                  }
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Reported By</label>
                <input
                  type="text"
                  value={formData.reportedBy}
                  onChange={(e) =>
                    setFormData({ ...formData, reportedBy: e.target.value })
                  }
                  placeholder="Your name..."
                />
              </div>
              <div className="form-group">
                <label>Location</label>
                <input
                  type="text"
                  value={formData.location}
                  onChange={(e) =>
                    setFormData({ ...formData, location: e.target.value })
                  }
                  placeholder="e.g., Building A, Floor 3"
                />
              </div>
              <div className="form-group">
                <label>Equipment ID</label>
                <input
                  type="text"
                  value={formData.equipmentId || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, equipmentId: e.target.value })
                  }
                  placeholder="e.g., MRI-001"
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary" disabled={isSubmitting}>
                {isSubmitting ? 'Creating...' : 'Submit Ticket'}
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  setShowForm(false);
                  resetForm();
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Search & Filter Bar */}
      {!isLoading && tickets.length > 0 && (
        <div className="controls-bar">
          <div className="search-row">
            <div className="search-input-wrap">
              <svg
                className="search-icon"
                width="16"
                height="16"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth="2"
              >
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by title, description, or reporter..."
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Priorities</option>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Categories</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            {(searchQuery || statusFilter || priorityFilter || categoryFilter) && (
              <button className="btn-clear" onClick={clearFilters}>
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
                Clear
              </button>
            )}
          </div>
          <div className="results-count">
            Showing <strong>{filteredTickets.length}</strong> of {tickets.length}{' '}
            ticket{tickets.length !== 1 ? 's' : ''}
          </div>
        </div>
      )}

      {/* Loading */}
      {isLoading && (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading tickets...</p>
        </div>
      )}

      {/* Ticket List */}
      {!isLoading && (
        <div className="tickets-list">
          {tickets.length === 0 && (
            <div className="empty-state">
              <svg
                width="40"
                height="40"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                style={{ color: 'var(--gray-300)' }}
              >
                <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
              <p>No tickets yet. Report an issue above!</p>
            </div>
          )}

          {tickets.length > 0 && filteredTickets.length === 0 && (
            <div className="empty-state">
              <svg
                width="40"
                height="40"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                style={{ color: 'var(--gray-300)' }}
              >
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <p>No tickets match your search or filters.</p>
              <button className="btn-secondary" onClick={clearFilters}>
                Clear Filters
              </button>
            </div>
          )}

          {filteredTickets.map((ticket) => (
            <div key={ticket.id} className="ticket-card">
              <div
                className={`ticket-accent ${getPriorityClass(ticket.priority)}`}
              ></div>
              <div className="ticket-header">
                <div className="ticket-title-row">
                  <span
                    className={`priority-badge ${getPriorityClass(ticket.priority)}`}
                  >
                    {ticket.priority}
                  </span>
                  <h3>{ticket.title}</h3>
                </div>
                <span className="ticket-id">#{ticket.id}</span>
              </div>
              {ticket.description && (
                <p className="ticket-desc">{ticket.description}</p>
              )}
              <div className="ticket-meta">
                <span className="meta-item">
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path d="M20.59 13.41l-7.17 7.17a2 2 0 01-2.83 0L2 12V2h10l8.59 8.59a2 2 0 010 2.82z" />
                    <line x1="7" y1="7" x2="7.01" y2="7" />
                  </svg>
                  {ticket.category}
                </span>
                {ticket.location && (
                  <span className="meta-item">
                    <svg
                      width="13"
                      height="13"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z" />
                      <circle cx="12" cy="10" r="3" />
                    </svg>
                    {ticket.location}
                  </span>
                )}
                {ticket.equipmentId && (
                  <span className="meta-item">
                    <svg
                      width="13"
                      height="13"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35" />
                    </svg>
                    {ticket.equipmentId}
                  </span>
                )}
                <span className="meta-item">
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  {formatDate(ticket.createdAt, 'medium')}
                </span>
              </div>
              <div className="ticket-footer">
                <span className={`status-badge ${getStatusClass(ticket.status)}`}>
                  {ticket.status}
                </span>
                <div className="status-actions">
                  {STATUSES.map((s) => (
                    <button
                      key={s}
                      disabled={ticket.status === s}
                      className="status-btn"
                      onClick={() => handleUpdateStatus(ticket, s)}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
