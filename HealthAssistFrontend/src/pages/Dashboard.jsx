import { useNavigate } from 'react-router-dom';
import { formatNumber } from '../utils/format';
import './Dashboard.css';

export default function Dashboard() {
  const navigate = useNavigate();

  const navigateTo = (route) => navigate(route);
  const startChat = (prompt) =>
    navigate(`/chat?message=${encodeURIComponent(prompt)}`);

  /* ─── Stats bar ─── */
  const stats = [
    { value: '24/7', label: 'AI Availability', icon: 'pulse' },
    { value: '50+', label: 'Specialists', icon: 'doctors' },
    { value: '15', label: 'Departments', icon: 'departments' },
    { value: '< 2min', label: 'Avg Response', icon: 'speed' },
  ];

  /* ─── Showcase sections (alternating L / R) ─── */
  const showcaseSections = [
    {
      title: 'AI-Powered Triage',
      subtitle: 'Smart Symptom Assessment',
      description:
        'Our AI engine analyzes symptoms in real-time, cross-referencing with clinical guidelines to provide instant triage recommendations. Powered by GPT with RAG-enhanced medical knowledge.',
      highlights: [
        { label: 'Risk Classification', value: '4-tier severity scoring' },
        { label: 'Response Time', value: 'Under 2 seconds' },
        { label: 'Knowledge Base', value: '7 clinical documents' },
        { label: 'Accuracy Rate', value: '94.7% triage match' },
      ],
      cardTitle: 'Doctor Availability',
      cardType: 'doctors',
      route: '/doctors',
      doctors: [
        { name: 'Dr. Sarah Chen', dept: 'Cardiology', available: true, time: '9:00 AM - 5:00 PM' },
        { name: 'Dr. James Wilson', dept: 'Neurology', available: true, time: '10:00 AM - 6:00 PM' },
        { name: 'Dr. Emily Park', dept: 'Orthopedics', available: false, time: 'Next: Tomorrow 8 AM' },
        { name: 'Dr. Michael Ross', dept: 'Pediatrics', available: true, time: '8:00 AM - 4:00 PM' },
      ],
      gradient: 'blue',
    },
    {
      title: 'Smart Scheduling',
      subtitle: 'Effortless Appointment Booking',
      description:
        'Book appointments directly through the AI chat or our scheduling interface. The system automatically checks doctor availability, prevents conflicts, and sends confirmations.',
      highlights: [
        { label: 'Booking Channels', value: 'Chat + Direct UI' },
        { label: 'Auto-matching', value: 'Dept + Specialty filter' },
        { label: 'Conflict Check', value: 'Real-time validation' },
        { label: 'Reminders', value: 'Email + SMS alerts' },
      ],
      cardTitle: 'Recent Appointments',
      cardType: 'appointments',
      route: '/appointments',
      appointments: [
        { patient: 'John Smith', doctor: 'Dr. Chen', dept: 'Cardiology', time: 'Today 2:30 PM', status: 'Confirmed' },
        { patient: 'Maria Garcia', doctor: 'Dr. Wilson', dept: 'Neurology', time: 'Today 4:00 PM', status: 'Confirmed' },
        { patient: 'Robert Lee', doctor: 'Dr. Park', dept: 'Orthopedics', time: 'Tomorrow 9:00 AM', status: 'Pending' },
      ],
      gradient: 'purple',
    },
    {
      title: 'Enterprise Guardrails',
      subtitle: 'Safety & Compliance Built-in',
      description:
        'Every AI interaction passes through multi-layer safety guardrails including PII detection, content moderation, medical disclaimer injection, and full audit trail logging.',
      highlights: [
        { label: 'PII Detection', value: 'Auto-redact SSN, emails' },
        { label: 'Moderation', value: 'Content safety filtering' },
        { label: 'Audit Trail', value: 'Full interaction logging' },
        { label: 'Compliance', value: 'HIPAA-ready framework' },
      ],
      cardTitle: 'Live Guardrail Status',
      cardType: 'guardrails',
      route: '/audit',
      guardrails: [
        { name: 'PII Scanner', status: 'Active', checks: 1247 },
        { name: 'Content Moderation', status: 'Active', checks: 1247 },
        { name: 'Medical Disclaimer', status: 'Active', checks: 892 },
        { name: 'Risk Classifier', status: 'Active', checks: 1103 },
      ],
      gradient: 'green',
    },
  ];

  /* ─── Feature cards ─── */
  const features = [
    {
      title: 'AI Health Chat',
      description: 'Ask questions about symptoms, departments, visiting hours, and more.',
      route: '/chat',
      gradient: 'linear-gradient(135deg, #3b82f6, #6366f1)',
      iconPath:
        'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z',
    },
    {
      title: 'Doctor Availability',
      description: 'Find available doctors by department or specialty.',
      route: '/doctors',
      gradient: 'linear-gradient(135deg, #10b981, #06b6d4)',
      iconPath:
        'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
    },
    {
      title: 'Appointments',
      description: 'Schedule, view, or manage your appointments.',
      route: '/appointments',
      gradient: 'linear-gradient(135deg, #8b5cf6, #ec4899)',
      iconPath: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z',
    },
    {
      title: 'Report Issues',
      description: 'Report facility or equipment issues for quick resolution.',
      route: '/tickets',
      gradient: 'linear-gradient(135deg, #f59e0b, #ef4444)',
      iconPath:
        'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4.5c-.77-.833-2.694-.833-3.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z',
    },
    {
      title: 'Audit Trail',
      description: 'View AI workflow decisions and compliance logs.',
      route: '/audit',
      gradient: 'linear-gradient(135deg, #ef4444, #f97316)',
      iconPath:
        'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01m-.01 4h.01',
    },
  ];

  const samplePrompts = [
    {
      text: 'I have a sharp chest pain and shortness of breath.',
      icon: 'M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z',
    },
    {
      text: 'How do I schedule a follow-up with cardiology?',
      icon: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z',
    },
    {
      text: 'Does my insurance cover MRI scans?',
      icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z',
    },
    {
      text: 'The ultrasound machine in Room 205 is not powering on.',
      icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z',
    },
    { text: 'What are the visiting hours for the ICU?', icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z' },
    {
      text: 'I need directions to the Neurology department.',
      icon: 'M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z',
    },
  ];

  const renderStatIcon = (icon) => {
    switch (icon) {
      case 'pulse':
        return (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
            <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
          </svg>
        );
      case 'doctors':
        return (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
            <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
            <circle cx="9" cy="7" r="4" />
          </svg>
        );
      case 'departments':
        return (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
            <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16" />
          </svg>
        );
      case 'speed':
        return (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
          </svg>
        );
      default:
        return null;
    }
  };

  return (
    <div className="dashboard">
      {/* ════════ HERO ════════ */}
      <section className="hero">
        {/* Decorative grid background */}
        <div className="hero-grid-bg">
          <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern id="heroGrid" x="0" y="0" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(59,130,246,0.06)" strokeWidth="1" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#heroGrid)" />
          </svg>
        </div>
        {/* Gradient orbs */}
        <div className="hero-orb hero-orb-1"></div>
        <div className="hero-orb hero-orb-2"></div>

        <div className="hero-inner">
          <div className="hero-content">
            <div className="hero-badge">
              <span className="badge-dot"></span>
              AI-Powered Healthcare Platform
            </div>
            <h1>
              Intelligent Healthcare
              <br />
              with <span className="text-gradient">HealthAssist AI</span>
            </h1>
            <p>
              Enterprise-grade AI assistant delivering real-time triage, smart scheduling, and clinical decision support —
              powered by Spring AI and Azure OpenAI with multi-layer safety guardrails.
            </p>
            <div className="hero-actions">
              <button className="btn-hero-primary" onClick={() => navigateTo('/chat')}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                </svg>
                Start AI Consultation
              </button>
              <button className="btn-hero-secondary" onClick={() => navigateTo('/doctors')}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
                </svg>
                Browse Doctors
              </button>
            </div>
          </div>
          <div className="hero-visual">
            <div className="hero-card">
              <div className="hero-card-icon">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                </svg>
              </div>
              <div className="hero-card-rings">
                <div className="ring ring-1"></div>
                <div className="ring ring-2"></div>
                <div className="ring ring-3"></div>
              </div>
            </div>
            {/* Floating mini-cards */}
            <div className="floating-card fc-1">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth={2}>
                <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
              <span>Guardrails Active</span>
            </div>
            <div className="floating-card fc-2">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#6366f1" strokeWidth={2}>
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
              <span>RAG-Enhanced</span>
            </div>
          </div>
        </div>
      </section>

      {/* ════════ STATS BAR ════════ */}
      <section className="stats-bar">
        {stats.map((s) => (
          <div className="stat-item" key={s.label}>
            <div className="stat-icon">{renderStatIcon(s.icon)}</div>
            <div className="stat-info">
              <div className="stat-value">{s.value}</div>
              <div className="stat-label">{s.label}</div>
            </div>
          </div>
        ))}
      </section>

      {/* ════════ SHOWCASE SECTIONS (alternating L/R) ════════ */}
      {showcaseSections.map((section, i) => (
        <section key={section.title} className={`showcase ${i % 2 !== 0 ? 'reversed' : ''}`}>
          {/* Decorative grid */}
          <div className="showcase-grid-bg">
            <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <pattern id={`sGrid${i}`} x="0" y="0" width="32" height="32" patternUnits="userSpaceOnUse">
                  <circle cx="1" cy="1" r="1" fill="rgba(59,130,246,0.08)" />
                </pattern>
              </defs>
              <rect width="100%" height="100%" fill={`url(#sGrid${i})`} />
            </svg>
          </div>

          <div className="showcase-content">
            <div className="showcase-label">{section.subtitle}</div>
            <h2>{section.title}</h2>
            <p>{section.description}</p>
            <div className="highlight-grid">
              {section.highlights.map((h) => (
                <div key={h.label} className="highlight-item">
                  <div className="highlight-label">{h.label}</div>
                  <div className="highlight-value">{h.value}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="showcase-card clickable-card" onClick={() => navigateTo(section.route)}>
            <div className={`sc-card-header sc-header-${section.gradient}`}>
              <h3>{section.cardTitle}</h3>
              <span className="sc-view-all">View All →</span>
            </div>
            <div className="sc-card-body">
              {section.cardType === 'doctors' &&
                section.doctors.map((doc) => (
                  <div className="sc-row" key={doc.name}>
                    <div className={`sc-row-avatar ${doc.available ? 'avatar-available' : 'avatar-unavailable'}`}>
                      {doc.name.charAt(4)}
                    </div>
                    <div className="sc-row-info">
                      <div className="sc-row-name">{doc.name}</div>
                      <div className="sc-row-sub">{doc.dept}</div>
                    </div>
                    <div className="sc-row-status">
                      <span className={`sc-dot ${doc.available ? 'dot-green' : 'dot-red'}`}></span>
                      <span className="sc-time">{doc.time}</span>
                    </div>
                  </div>
                ))}

              {section.cardType === 'appointments' &&
                section.appointments.map((appt) => (
                  <div className="sc-row" key={appt.patient}>
                    <div className="sc-row-avatar avatar-purple">{appt.patient.charAt(0)}</div>
                    <div className="sc-row-info">
                      <div className="sc-row-name">{appt.patient}</div>
                      <div className="sc-row-sub">
                        {appt.doctor} — {appt.dept}
                      </div>
                    </div>
                    <div className="sc-row-status">
                      <span className={`sc-badge ${appt.status === 'Confirmed' ? 'badge-green' : 'badge-yellow'}`}>
                        {appt.status}
                      </span>
                      <span className="sc-time">{appt.time}</span>
                    </div>
                  </div>
                ))}

              {section.cardType === 'guardrails' &&
                section.guardrails.map((g) => (
                  <div className="sc-row" key={g.name}>
                    <div className="sc-row-avatar avatar-green">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={2.5}>
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                      </svg>
                    </div>
                    <div className="sc-row-info">
                      <div className="sc-row-name">{g.name}</div>
                      <div className="sc-row-sub">{formatNumber(g.checks)} checks processed</div>
                    </div>
                    <div className="sc-row-status">
                      <span className="sc-badge badge-green">{g.status}</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        </section>
      ))}

      {/* ════════ FEATURES GRID ════════ */}
      <section className="features-section">
        <div className="section-header">
          <div className="section-label">Platform Capabilities</div>
          <h2>Everything you need in one place</h2>
          <p>Explore the full suite of AI-driven healthcare tools</p>
        </div>
        <div className="features-grid">
          {features.map((f) => (
            <div className="feature-card" key={f.title} onClick={() => navigateTo(f.route)}>
              <div className="feature-icon-wrap" style={{ background: f.gradient }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d={f.iconPath} />
                </svg>
              </div>
              <div className="feature-body">
                <h3>{f.title}</h3>
                <p>{f.description}</p>
              </div>
              <div className="feature-arrow">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ════════ QUICK START PROMPTS ════════ */}
      <section className="prompts-section">
        <div className="section-header">
          <div className="section-label">Quick Start</div>
          <h2>Try these sample queries</h2>
          <p>Experience HealthAssist AI with real-world healthcare scenarios</p>
        </div>
        <div className="prompts-grid">
          {samplePrompts.map((p) => (
            <button className="prompt-chip" key={p.text} onClick={() => startChat(p.text)}>
              <div className="prompt-icon-wrap">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d={p.icon} />
                </svg>
              </div>
              <span className="prompt-text">{p.text}</span>
              <span className="prompt-arrow">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M7 17l9.2-9.2M17 17V8H8" />
                </svg>
              </span>
            </button>
          ))}
        </div>
      </section>

      {/* ════════ DISCLAIMER ════════ */}
      <div className="disclaimer-banner">
        <div className="disclaimer-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <div className="disclaimer-content">
          <strong>Medical Disclaimer</strong>
          <p>
            HealthAssist AI provides general health information only. It is not a substitute for professional medical
            advice, diagnosis, or treatment. In case of emergency, call 911 immediately.
          </p>
        </div>
      </div>
    </div>
  );
}
