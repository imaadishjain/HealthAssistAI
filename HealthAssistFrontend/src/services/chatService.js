import { api, API_BASE_URL } from './api';

const AI_BASE = `${API_BASE_URL}/ai`;

/** Synchronous chat - full response. */
export function chatSync(request) {
  return api.post('/ai/chat/sync', request).then((r) => r.data);
}

/** Agentic health chat with full orchestrator pipeline. */
export function agentChat(request) {
  return api.post('/ai/agent/health', request).then((r) => r.data);
}

/**
 * Streaming chat using Server-Sent Events. Returns an unsubscribe fn.
 * The `onToken` callback receives incremental text tokens.
 */
export function chatStream(message, onToken, onDone) {
  const url = `${AI_BASE}/chat/async?message=${encodeURIComponent(message)}`;
  const eventSource = new EventSource(url);

  eventSource.onmessage = (event) => {
    try {
      const parsed = JSON.parse(event.data);
      onToken(parsed.token);
    } catch {
      // Fallback for non-JSON data
      onToken(event.data);
    }
  };

  eventSource.onerror = () => {
    eventSource.close();
    onDone?.();
  };

  return () => eventSource.close();
}
