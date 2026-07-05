import { api } from './api';

/** Check doctor availability by department/specialty. */
export function checkAvailability(department, specialty) {
  const params = {};
  if (department) params.department = department;
  if (specialty) params.specialty = specialty;
  return api.get('/appointments/check', { params }).then((r) => r.data);
}

/** Create a new appointment. */
export function createAppointment(request) {
  return api.post('/appointments/create', request).then((r) => r.data);
}

/** Get all appointments. */
export function getAllAppointments() {
  return api.get('/appointments').then((r) => r.data);
}

/** Get all doctors. */
export function getAllDoctors() {
  return api.get('/doctors').then((r) => r.data);
}

/** Get doctors by department. */
export function getDoctorsByDepartment(department) {
  return api.get(`/doctors/department/${department}`).then((r) => r.data);
}

/** Cancel an appointment. */
export function cancelAppointment(id) {
  return api.put(`/appointments/${id}/cancel`, {}).then((r) => r.data);
}
