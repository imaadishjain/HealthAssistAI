import { Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Dashboard from './pages/Dashboard';
import Chat from './pages/Chat';
import DoctorList from './pages/DoctorList';
import Appointments from './pages/Appointments';
import Tickets from './pages/Tickets';
import Audit from './pages/Audit';
import './App.css';

export default function App() {
  return (
    <>
      <Navbar />
      <main className="app-content">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/doctors" element={<DoctorList />} />
          <Route path="/appointments" element={<Appointments />} />
          <Route path="/tickets" element={<Tickets />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Footer />
    </>
  );
}
