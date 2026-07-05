import { Link } from 'react-router-dom';
import './Footer.css';

export default function Footer() {
  const currentYear = new Date().getFullYear();

  const quickLinks = [
    { label: 'AI Chat', route: '/chat' },
    { label: 'Find Doctors', route: '/doctors' },
    { label: 'Appointments', route: '/appointments' },
    { label: 'Report Issue', route: '/tickets' },
    { label: 'Audit Trail', route: '/audit' },
  ];

  const resources = [
    { label: 'Patient Rights', href: '#' },
    { label: 'Insurance Guide', href: '#' },
    { label: 'Triage Guidelines', href: '#' },
    { label: 'Visiting Hours', href: '#' },
    { label: 'Discharge Process', href: '#' },
  ];

  return (
    <footer className="footer">
      {/* Decorative top border */}
      <div className="footer-gradient-border"></div>

      <div className="footer-inner">
        {/* Main grid */}
        <div className="footer-grid">
          {/* Brand Column */}
          <div className="footer-brand-col">
            <div className="footer-brand">
              <div className="footer-logo">
                <img src="/logo.svg" alt="HealthAssist AI" width={32} height={32} />
              </div>
              <div className="footer-brand-text">
                <span className="footer-brand-name">HealthAssist</span>
                <span className="footer-brand-badge">AI</span>
              </div>
            </div>
            <p className="footer-tagline">
              AI-powered healthcare assistant with enterprise-grade safety guardrails.
              Providing intelligent triage, scheduling, and clinical support.
            </p>
            <div className="footer-certifications">
              <div className="cert-badge">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
                <span>HIPAA Compliant</span>
              </div>
              <div className="cert-badge">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M9 12l2 2 4-4" />
                  <circle cx="12" cy="12" r="10" />
                </svg>
                <span>SOC 2 Certified</span>
              </div>
            </div>
          </div>

          {/* Quick Links */}
          <div className="footer-links-col">
            <h4>Platform</h4>
            <ul>
              {quickLinks.map((link) => (
                <li key={link.route}>
                  <Link to={link.route}>{link.label}</Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Resources */}
          <div className="footer-links-col">
            <h4>Resources</h4>
            <ul>
              {resources.map((res) => (
                <li key={res.label}>
                  <a href={res.href}>{res.label}</a>
                </li>
              ))}
            </ul>
          </div>

          {/* Contact & Status */}
          <div className="footer-links-col">
            <h4>Contact</h4>
            <ul className="footer-contact">
              <li>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6 19.79 19.79 0 01-3.07-8.67A2 2 0 014.11 2h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z" />
                </svg>
                <span>1-800-HEALTH-AI</span>
              </li>
              <li>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                  <polyline points="22,6 12,13 2,6" />
                </svg>
                <span>support@healthassist.ai</span>
              </li>
              <li>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                <span>24/7 Support Available</span>
              </li>
            </ul>
            <div className="footer-status-indicator">
              <span className="status-pulse"></span>
              <span>All Systems Operational</span>
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="footer-bottom">
          <p>&copy; {currentYear} HealthAssist AI. All rights reserved.</p>
          <div className="footer-bottom-links">
            <a href="#">Privacy Policy</a>
            <a href="#">Terms of Service</a>
            <a href="#">Cookie Policy</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
