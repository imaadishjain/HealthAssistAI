/** Format date like Angular's DatePipe. */
export function formatDate(value, format = 'medium') {
  if (!value) return '';
  const d = value instanceof Date ? value : new Date(value);
  if (isNaN(d.getTime())) return String(value);

  if (format === 'shortTime') {
    return d.toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit',
    });
  }
  // 'medium' — mimic Angular's medium: MMM d, y, h:mm:ss a
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** Format number with locale separators (mimics Angular's number pipe). */
export function formatNumber(value) {
  if (value === null || value === undefined) return '';
  const n = typeof value === 'number' ? value : Number(value);
  if (isNaN(n)) return String(value);
  return n.toLocaleString();
}
