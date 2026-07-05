import { useState } from 'react';
import { NavLink, Link } from 'react-router-dom';
import './Navbar.css';

export default function Navbar() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const toggleMenu = () => setIsMenuOpen((v) => !v);
  const closeMenu = () => setIsMenuOpen(false);

  const linkClass = ({ isActive }) => (isActive ? 'active' : '');

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <div className="navbar-brand">
          <div className="brand-icon">
            <img src="/logo.svg" alt="HealthAssist AI" width={34} height={34} />
          </div>
          <Link to="/" className="brand-name" onClick={closeMenu}>
            <span className="brand-text">HealthAssist</span>
            <span className="brand-badge">AI</span>
          </Link>
        </div>

        <button
          className="menu-toggle"
          onClick={toggleMenu}
          aria-label="Toggle navigation"
        >
          <span className={`hamburger ${isMenuOpen ? 'open' : ''}`}></span>
        </button>

        <div className={`navbar-links ${isMenuOpen ? 'open' : ''}`}>
          <NavLink to="/" end className={linkClass} onClick={closeMenu}>
            <span className="nav-label">Dashboard</span>
          </NavLink>
          <NavLink to="/chat" className={linkClass} onClick={closeMenu}>
            <span className="nav-label">AI Chat</span>
          </NavLink>
          <NavLink to="/doctors" className={linkClass} onClick={closeMenu}>
            <span className="nav-label">Doctors</span>
          </NavLink>
          <NavLink to="/appointments" className={linkClass} onClick={closeMenu}>
            <span className="nav-label">Appointments</span>
          </NavLink>
          <NavLink to="/tickets" className={linkClass} onClick={closeMenu}>
            <span className="nav-label">Tickets</span>
          </NavLink>
          <NavLink to="/audit" className={linkClass} onClick={closeMenu}>
            <span className="nav-label">Audit Trail</span>
          </NavLink>
        </div>

        <div className="navbar-status">
          <span className="status-dot"></span>
          <span className="status-text">System Online</span>
        </div>
      </div>
    </nav>
  );
}
