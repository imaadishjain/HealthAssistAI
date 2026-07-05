import { useEffect, useState, useMemo } from 'react';
import { getAllDoctors } from '../services/appointmentService';
import { formatDate } from '../utils/format';
import './DoctorList.css';

export default function DoctorList() {
  const [doctors, setDoctors] = useState([]);
  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [availabilityFilter, setAvailabilityFilter] = useState('all');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const loadDoctors = () => {
    setIsLoading(true);
    setError('');
    getAllDoctors()
      .then((data) => {
        setDoctors(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setError('Failed to load doctors. Please try again.');
        setIsLoading(false);
      });
  };

  useEffect(() => {
    loadDoctors();
  }, []);

  const departments = useMemo(
    () => [...new Set(doctors.map((d) => d.department))].sort(),
    [doctors]
  );

  const filteredDoctors = useMemo(() => {
    let result = doctors;
    if (selectedDepartment) {
      result = result.filter((d) => d.department === selectedDepartment);
    }
    if (searchTerm && searchTerm.trim()) {
      const term = searchTerm.trim().toLowerCase();
      result = result.filter(
        (d) =>
          (d.name || '').toLowerCase().includes(term) ||
          (d.specialty || '').toLowerCase().includes(term) ||
          (d.department || '').toLowerCase().includes(term)
      );
    }
    if (availabilityFilter === 'available') {
      result = result.filter((d) => d.available);
    } else if (availabilityFilter === 'unavailable') {
      result = result.filter((d) => !d.available);
    }
    return result;
  }, [doctors, selectedDepartment, searchTerm, availabilityFilter]);

  const availabilityClass = (available) => (available ? 'available' : 'unavailable');

  return (
    <div className="doctor-page">
      {/* Hero */}
      <div className="page-hero">
        <div className="page-hero-grid">
          <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern id="docGrid" x="0" y="0" width="32" height="32" patternUnits="userSpaceOnUse">
                <circle cx="1" cy="1" r="1" fill="rgba(16,185,129,0.1)" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#docGrid)" />
          </svg>
        </div>
        <div className="page-hero-orb"></div>
        <div className="page-hero-content">
          <div className="page-hero-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
            </svg>
          </div>
          <div>
            <h1>Our Doctors</h1>
            <p>Find and explore our team of qualified healthcare professionals</p>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="filters-bar">
        <div className="search-wrap">
          <svg className="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by specialization or department..."
            className="search-input"
          />
        </div>
        <select value={selectedDepartment} onChange={(e) => setSelectedDepartment(e.target.value)}>
          <option value="">All Departments</option>
          {departments.map((dept) => (
            <option key={dept} value={dept}>
              {dept}
            </option>
          ))}
        </select>
        <div className="availability-toggle">
          <button className={availabilityFilter === 'all' ? 'active' : ''} onClick={() => setAvailabilityFilter('all')}>
            All
          </button>
          <button className={availabilityFilter === 'available' ? 'active' : ''} onClick={() => setAvailabilityFilter('available')}>
            <span className="avail-indicator available"></span>
            Available
          </button>
          <button className={availabilityFilter === 'unavailable' ? 'active' : ''} onClick={() => setAvailabilityFilter('unavailable')}>
            <span className="avail-indicator unavailable"></span>
            Unavailable
          </button>
        </div>
      </div>

      {isLoading && (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading doctors...</p>
        </div>
      )}

      {error && (
        <div className="error-state">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ color: 'var(--danger-400)' }}>
            <circle cx="12" cy="12" r="10" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
          <p>{error}</p>
          <button onClick={loadDoctors}>Retry</button>
        </div>
      )}

      {!isLoading && !error && (
        <div className="doctors-grid">
          {filteredDoctors.map((doctor) => (
            <div key={doctor.id ?? doctor.name} className="doctor-card">
              <div className={`doctor-card-accent ${doctor.available ? 'accent-available' : 'accent-unavailable'}`}></div>
              <div className="doctor-header">
                <div className={`doctor-avatar ${doctor.available ? 'avatar-active' : 'avatar-inactive'}`}>
                  {doctor.name?.charAt(0)}
                </div>
                <div className="doctor-info">
                  <h3>Dr. {doctor.name}</h3>
                  <span className="specialization">{doctor.specialty}</span>
                </div>
                <span className={`availability-badge ${availabilityClass(doctor.available)}`}>
                  <span className="avail-dot"></span>
                  {doctor.available ? 'Available' : 'Unavailable'}
                </span>
              </div>
              <div className="doctor-details">
                <div className="detail-row">
                  <span className="label">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
                      <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16" />
                    </svg>
                    Department
                  </span>
                  <span className="value">{doctor.department}</span>
                </div>
                {doctor.nextAvailableSlot && (
                  <div className="detail-row">
                    <span className="label">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <circle cx="12" cy="12" r="10" />
                        <polyline points="12 6 12 12 16 14" />
                      </svg>
                      Next Slot
                    </span>
                    <span className="value">{formatDate(doctor.nextAvailableSlot, 'medium')}</span>
                  </div>
                )}
                <div className="detail-row">
                  <span className="label">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                      <polyline points="22 6 12 13 2 6" />
                    </svg>
                    Email
                  </span>
                  <span className="value">{doctor.email}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {!isLoading && !error && filteredDoctors.length === 0 && (
        <div className="empty-state">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ color: 'var(--gray-300)' }}>
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <p>No doctors found matching your criteria.</p>
        </div>
      )}
    </div>
  );
}
