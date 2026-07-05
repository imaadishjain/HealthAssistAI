import { useEffect, useMemo, useState } from 'react';
import {
  getAllAppointments,
  getAllDoctors,
  createAppointment,
  cancelAppointment,
} from '../services/appointmentService';
import { formatDate } from '../utils/format';
import './Appointments.css';

const DEPARTMENTS = [
  'Cardiology',
  'Neurology',
  'Orthopedics',
  'Pulmonology',
  'Gastroenterology',
  'Pediatrics',
  'Emergency Medicine',
  'Dermatology',
  'Psychiatry',
  'Oncology',
  'Radiology',
  'Gynecology',
  'ENT',
  'General Medicine',
];

const STATUS_OPTIONS = ['CONFIRMED', 'CANCELLED', 'COMPLETED', 'PENDING'];

const EMPTY_FORM = {
  patientName: '',
  patientEmail: '',
  patientPhone: '',
  department: '',
  doctorId: 0,
  dateTime: '',
  reason: '',
};

function getStatusClass(status) {
  switch (status) {
    case 'CONFIRMED':
      return 'status-confirmed';
    case 'CANCELLED':
      return 'status-cancelled';
    case 'COMPLETED':
      return 'status-completed';
    default:
      return 'status-pending';
  }
}

export default function Appointments() {
  const [appointments, setAppointments] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');

  const [formData, setFormData] = useState({ ...EMPTY_FORM });

  const loadAppointments = () => {
    setIsLoading(true);
    getAllAppointments()
      .then((data) => {
        setAppointments(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load appointments.');
        setIsLoading(false);
      });
  };

  const loadDoctors = () => {
    getAllDoctors()
      .then((data) => setDoctors(data || []))
      .catch(() => {});
  };

  useEffect(() => {
    loadAppointments();
    loadDoctors();
  }, []);

  const filteredDoctors = useMemo(() => {
    if (formData.department) {
      return doctors.filter(
        (d) => d.department === formData.department && d.available
      );
    }
    return doctors.filter((d) => d.available);
  }, [doctors, formData.department]);

  const filteredAppointments = useMemo(() => {
    let list = appointments;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (a) =>
          a.patientName?.toLowerCase().includes(q) ||
          a.doctorName?.toLowerCase().includes(q) ||
          a.reason?.toLowerCase().includes(q) ||
          String(a.id).includes(q)
      );
    }
    if (statusFilter) list = list.filter((a) => a.status === statusFilter);
    if (departmentFilter)
      list = list.filter((a) => a.department === departmentFilter);
    return list;
  }, [appointments, searchQuery, statusFilter, departmentFilter]);

  const clearFilters = () => {
    setSearchQuery('');
    setStatusFilter('');
    setDepartmentFilter('');
  };

  const resetForm = () => setFormData({ ...EMPTY_FORM });

  const onDepartmentChange = (dept) => {
    setFormData((prev) => ({ ...prev, department: dept, doctorId: 0 }));
  };

  const submitForm = (e) => {
    e.preventDefault();
    if (
      !formData.patientName ||
      !formData.department ||
      !formData.doctorId ||
      !formData.dateTime
    ) {
      setErrorMsg('Please fill in all required fields.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg('');
    setSuccessMsg('');

    // Angular passes doctorId as string via ngModel; ensure number for API
    const payload = {
      ...formData,
      doctorId: Number(formData.doctorId),
    };

    createAppointment(payload)
      .then((appt) => {
        setSuccessMsg(`Appointment booked successfully! (ID: ${appt.id})`);
        setIsSubmitting(false);
        setShowForm(false);
        loadAppointments();
        resetForm();
      })
      .catch((err) => {
        setErrorMsg(err?.response?.data?.message || 'Failed to create appointment.');
        setIsSubmitting(false);
      });
  };

  const handleCancelAppointment = (id) => {
    if (!window.confirm('Are you sure you want to cancel this appointment?')) return;
    cancelAppointment(id)
      .then(() => {
        loadAppointments();
        setSuccessMsg('Appointment cancelled.');
      })
      .catch(() => setErrorMsg('Failed to cancel appointment.'));
  };

  return (
    <div className="appointments-page">
      {/* Page Hero */}
      <div className="page-hero">
        <div className="page-hero-grid">
          <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern
                id="apptGrid"
                x="0"
                y="0"
                width="32"
                height="32"
                patternUnits="userSpaceOnUse"
              >
                <circle cx="1" cy="1" r="1" fill="rgba(139,92,246,0.08)" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#apptGrid)" />
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
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
            </div>
            <div>
              <h1>Appointments</h1>
              <p>Schedule, view, and manage your healthcare appointments</p>
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
            {showForm ? 'Close' : 'New Appointment'}
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

      {/* Appointment Form */}
      {showForm && (
        <div className="form-card">
          <h2>Book New Appointment</h2>
          <form onSubmit={submitForm}>
            <div className="form-row">
              <div className="form-group">
                <label>Patient Name *</label>
                <input
                  type="text"
                  value={formData.patientName}
                  onChange={(e) =>
                    setFormData({ ...formData, patientName: e.target.value })
                  }
                  required
                />
              </div>
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  value={formData.patientEmail}
                  onChange={(e) =>
                    setFormData({ ...formData, patientEmail: e.target.value })
                  }
                />
              </div>
              <div className="form-group">
                <label>Phone</label>
                <input
                  type="tel"
                  value={formData.patientPhone}
                  onChange={(e) =>
                    setFormData({ ...formData, patientPhone: e.target.value })
                  }
                />
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Department *</label>
                <select
                  value={formData.department}
                  onChange={(e) => onDepartmentChange(e.target.value)}
                  required
                >
                  <option value="">Select Department</option>
                  {DEPARTMENTS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Doctor *</label>
                <select
                  value={formData.doctorId || 0}
                  onChange={(e) =>
                    setFormData({ ...formData, doctorId: Number(e.target.value) })
                  }
                  required
                >
                  <option value={0} disabled>
                    Select Doctor
                  </option>
                  {filteredDoctors.map((doc) => (
                    <option key={doc.id} value={doc.id}>
                      Dr. {doc.name} — {doc.specialty}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Date &amp; Time *</label>
                <input
                  type="datetime-local"
                  value={formData.dateTime}
                  onChange={(e) =>
                    setFormData({ ...formData, dateTime: e.target.value })
                  }
                  required
                />
              </div>
              <div className="form-group">
                <label>Reason</label>
                <input
                  type="text"
                  value={formData.reason}
                  onChange={(e) =>
                    setFormData({ ...formData, reason: e.target.value })
                  }
                  placeholder="Brief description..."
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary" disabled={isSubmitting}>
                {isSubmitting ? 'Booking...' : 'Book Appointment'}
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
      {!isLoading && appointments.length > 0 && (
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
                placeholder="Search by patient, doctor, or reason..."
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Statuses</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            <select
              value={departmentFilter}
              onChange={(e) => setDepartmentFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Departments</option>
              {DEPARTMENTS.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
            {(searchQuery || statusFilter || departmentFilter) && (
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
            Showing <strong>{filteredAppointments.length}</strong> of{' '}
            {appointments.length} appointment
            {appointments.length !== 1 ? 's' : ''}
          </div>
        </div>
      )}

      {/* Appointments List */}
      {isLoading && (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading appointments...</p>
        </div>
      )}

      {!isLoading && (
        <div className="appointments-list">
          {appointments.length === 0 && (
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
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              <p>No appointments yet. Book your first appointment above!</p>
            </div>
          )}

          {appointments.length > 0 && filteredAppointments.length === 0 && (
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
              <p>No appointments match your search or filters.</p>
              <button className="btn-secondary" onClick={clearFilters}>
                Clear Filters
              </button>
            </div>
          )}

          {filteredAppointments.map((appt) => (
            <div key={appt.id} className="appointment-card">
              <div className={`appt-accent accent-${getStatusClass(appt.status)}`}></div>
              <div className="appt-header">
                <span className={`status-badge ${getStatusClass(appt.status)}`}>
                  {appt.status}
                </span>
                <span className="appt-id">#{appt.id}</span>
              </div>
              <div className="appt-body">
                <div className="appt-detail">
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                    <circle cx="12" cy="7" r="4" />
                  </svg>
                  <strong>Patient:</strong> {appt.patientName}
                </div>
                <div className="appt-detail">
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                  </svg>
                  <strong>Doctor:</strong> Dr. {appt.doctorName}
                </div>
                <div className="appt-detail">
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
                    <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16" />
                  </svg>
                  <strong>Department:</strong> {appt.department}
                </div>
                <div className="appt-detail">
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  <strong>Date:</strong> {formatDate(appt.appointmentDateTime, 'medium')}
                </div>
                {appt.reason && (
                  <div className="appt-detail">
                    <svg
                      width="14"
                      height="14"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                    </svg>
                    <strong>Reason:</strong> {appt.reason}
                  </div>
                )}
              </div>
              {(appt.status === 'CONFIRMED' || appt.status === 'SCHEDULED') && (
                <div className="appt-actions">
                  <button
                    className="btn-danger"
                    onClick={() => handleCancelAppointment(appt.id)}
                  >
                    Cancel Appointment
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
