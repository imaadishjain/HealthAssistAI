import { useEffect, useRef, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useChat } from '../context/ChatContext';
import { agentChat, chatSync } from '../services/chatService';
import { formatDate } from '../utils/format';
import './Chat.css';

/** Convert lightweight markdown (bold, italic, newlines, lists) to HTML */
function formatMarkdown(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/^- (.+)$/gm, '• $1')
    .replace(/\n/g, '<br>');
}

function getRiskClass(riskTier) {
  switch (riskTier) {
    case 'CRITICAL':
      return 'risk-critical';
    case 'HIGH':
      return 'risk-high';
    case 'MEDIUM':
      return 'risk-medium';
    default:
      return 'risk-low';
  }
}

function formLabel(formType) {
  const labels = {
    APPOINTMENT_FORM: '📅 Appointment Request',
    SYMPTOM_FORM: '🩺 Symptom Report',
    EQUIPMENT_FORM: '🔧 Equipment / Facility Issue',
    INSURANCE_FORM: '💰 Insurance Query',
    CANCEL_FORM: '❌ Cancel Appointment',
  };
  return labels[formType] || 'Form';
}

const departments = [
  '',
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

const capabilities = [
  { icon: '🩺', title: 'Symptom Assessment', desc: 'AI-powered triage & guidance', prompt: 'I have a severe headache and mild fever since yesterday', gradient: 'linear-gradient(135deg, #3b82f6, #6366f1)' },
  { icon: '👨‍⚕️', title: 'Find Doctors', desc: 'Browse available specialists', prompt: 'Show me available cardiologists', gradient: 'linear-gradient(135deg, #10b981, #06b6d4)' },
  { icon: '📅', title: 'Book Appointment', desc: 'Schedule your next visit', prompt: 'I want to book an appointment with a neurologist', gradient: 'linear-gradient(135deg, #8b5cf6, #ec4899)' },
  { icon: '📋', title: 'My Appointments', desc: 'View your scheduled visits', prompt: 'Show my scheduled appointments', gradient: 'linear-gradient(135deg, #6366f1, #8b5cf6)' },
  { icon: '🩺', title: 'Check Symptoms', desc: 'Report & assess your symptoms', prompt: 'I want to report my symptoms', gradient: 'linear-gradient(135deg, #ef4444, #dc2626)' },
  { icon: '💰', title: 'Insurance Help', desc: 'Check coverage & benefits', prompt: 'What does the Premium insurance plan cover?', gradient: 'linear-gradient(135deg, #f59e0b, #ef4444)' },
  { icon: '🎫', title: 'My Tickets', desc: 'Check reported issues status', prompt: 'Show my reported tickets', gradient: 'linear-gradient(135deg, #ec4899, #f59e0b)' },
  { icon: '🔧', title: 'Report Issues', desc: 'Log facility or equipment problems', prompt: 'I want to report a broken wheelchair on floor 3', gradient: 'linear-gradient(135deg, #ef4444, #f59e0b)' },
];

const samplePrompts = [
  "I've been having chest pain for 2 days",
  'Which doctors are available today?',
  'Show my scheduled appointments',
  'I want to cancel an appointment',
  'Show my reported tickets',
  'Does Standard plan cover MRI scans?',
];

const persistentActions = [
  { icon: '📅', label: 'Schedule Appointment', prompt: 'I want to book an appointment' },
  { icon: '📋', label: 'My Appointments', prompt: 'Show my scheduled appointments' },
  { icon: '❌', label: 'Cancel Appointment', prompt: 'I want to cancel an appointment' },
  { icon: '🎫', label: 'My Tickets', prompt: 'Show my reported tickets' },
  { icon: '🔧', label: 'Report Issue', prompt: 'I want to report a facility or equipment issue' },
  { icon: '💰', label: 'Insurance Help', prompt: 'What does my insurance plan cover?' },
];

export default function Chat() {
  const {
    messages,
    setMessages,
    addMessage,
    saveToStorage,
    clearHistory,
    formData,
    setFormData,
    useStreaming,
    setUseStreaming,
    useAgent,
    setUseAgent,
    isLoading,
    setIsLoading,
  } = useChat();

  const [userInput, setUserInput] = useState('');
  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [showRaiPanel, setShowRaiPanel] = useState(false);
  const [aiConsentGiven, setAiConsentGiven] = useState(
    () => localStorage.getItem('ai_consent_accepted') === 'true'
  );

  const chatContainer = useRef(null);
  const [searchParams] = useSearchParams();
  const initialMsgProcessed = useRef(false);

  const showWelcome = messages.length === 0;

  /* ─── Auto-send from ?message= param ─── */
  useEffect(() => {
    if (initialMsgProcessed.current) return;
    const msg = searchParams.get('message');
    if (msg) {
      initialMsgProcessed.current = true;
      setUserInput(msg);
      // defer to allow state to settle
      setTimeout(() => sendMessage(msg), 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  /* ─── Scroll to bottom ─── */
  const scrollToBottom = () => {
    if (showWelcome) return;
    setTimeout(() => {
      if (chatContainer.current) {
        chatContainer.current.scrollTop = chatContainer.current.scrollHeight;
      }
    }, 100);
  };

  useEffect(() => {
    scrollToBottom();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages.length]);

  const acceptAiConsent = () => {
    setAiConsentGiven(true);
    localStorage.setItem('ai_consent_accepted', 'true');
  };

  const clearChat = () => {
    clearHistory();
  };

  const useSuggestion = (text) => {
    setUserInput(text);
    setTimeout(() => sendMessage(text), 0);
  };

  /**
   * Build conversation history from recent messages for LLM context.
   * Keeps the last 20 messages, excludes the current user message.
   */
  const buildChatHistory = (excludeLast = true) => {
    const MAX_HISTORY = 20;
    const pastMessages = excludeLast ? messages.slice(0, -1) : messages;
    const recent = pastMessages.slice(-MAX_HISTORY);
    return recent
      .filter((m) => m.content && m.content.trim().length > 0)
      .map((m) => ({ role: m.sender, content: m.content.substring(0, 500) }));
  };

  const handleError = () => {
    addMessage({
      content:
        "⚠️ Sorry, something went wrong. If you're experiencing a medical emergency, please call **911** immediately. For other inquiries, please try again or call our front desk at **(555) 123-4567**.",
      sender: 'assistant',
      timestamp: new Date(),
    });
    setIsLoading(false);
    saveToStorage();
    scrollToBottom();
  };

  /** Common helper – detects form responses; optionally uses typewriter effect */
  const pushAssistantMessage = (response, skipTypewriter = false) => {
    const fullContent = response.response || '';
    const baseMsg = {
      content: '',
      sender: 'assistant',
      timestamp: new Date(),
      citations: response.citations,
      riskTier: response.riskTier,
      escalated: response.escalated,
      workflowId: response.workflowId,
      disclaimer: response.disclaimer,
      formType: response.formType || undefined,
      formFields: response.formFields || undefined,
      formSubmitted: false,
      quickReplies: response.quickReplies || undefined,
      quickReplyClicked: false,
    };

    // Add message and grab its index for updates
    let insertedIndex = -1;
    setMessages((prev) => {
      insertedIndex = prev.length;
      return [...prev, baseMsg];
    });

    // Pre-populate empty form model when a form arrives
    if (response.formType && response.formFields) {
      const fresh = {};
      response.formFields.forEach((f) => {
        fresh[f.name] = '';
      });
      setFormData(fresh);
    }

    setIsLoading(false);

    // Show content as a block (sync) or with typewriter effect (agent/stream)
    if (skipTypewriter || !fullContent || response.formType) {
      setMessages((prev) =>
        prev.map((m, i) => (i === insertedIndex || (insertedIndex < 0 && m === baseMsg) ? { ...m, content: fullContent } : m))
      );
      saveToStorage();
      scrollToBottom();
    } else {
      // Typewriter streaming effect
      let idx = 0;
      const chunkSize = 3;
      const speed = 15;
      const timer = setInterval(() => {
        idx = Math.min(idx + chunkSize, fullContent.length);
        const partial = fullContent.substring(0, idx);
        setMessages((prev) => {
          const targetIdx = insertedIndex >= 0 ? insertedIndex : prev.length - 1;
          return prev.map((m, i) => (i === targetIdx ? { ...m, content: partial } : m));
        });
        scrollToBottom();
        if (idx >= fullContent.length) {
          clearInterval(timer);
          saveToStorage();
        }
      }, speed);
    }
  };

  const sendMessage = (overrideMessage) => {
    const raw = overrideMessage ?? userInput;
    const message = raw.trim();
    if (!message || isLoading) return;

    addMessage({ content: message, sender: 'user', timestamp: new Date() });
    saveToStorage();
    setUserInput('');
    setIsLoading(true);
    scrollToBottom();

    const request = {
      message,
      department: selectedDepartment || undefined,
      chatHistory: buildChatHistory(false), // no exclude — the latest hasn't been added to state yet
    };

    const useSync = !useAgent && !useStreaming;
    const call = useAgent || useStreaming ? agentChat : chatSync;

    if (useAgent) {
      // Use agentChat (same as sendAgentMessage in Angular)
      agentChat(request).then((r) => pushAssistantMessage(r)).catch(handleError);
    } else if (useStreaming) {
      // Angular version used chatSync in streaming path too (as fallback)
      chatSync(request).then((r) => pushAssistantMessage(r)).catch(handleError);
    } else if (useSync) {
      chatSync(request).then((r) => pushAssistantMessage(r, true)).catch(handleError);
    } else {
      call(request).then((r) => pushAssistantMessage(r)).catch(handleError);
    }
  };

  const onKeyPress = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendMessage();
    }
  };

  /* ═══════════════  FORM SUBMISSION  ═══════════════ */

  const submitForm = (msg, msgIndex) => {
    if (!msg.formType || !msg.formFields) return;

    // Validate required fields
    const missing = msg.formFields.filter(
      (f) => f.required && !formData[f.name]?.trim()
    );
    if (missing.length > 0) {
      alert(
        'Please fill in all required fields: ' +
          missing.map((f) => f.label).join(', ')
      );
      return;
    }

    // Mark form as submitted (hides the form)
    setMessages((prev) =>
      prev.map((m, i) => (i === msgIndex ? { ...m, formSubmitted: true } : m))
    );

    // Build a readable summary for the chat
    const summaryLines = msg.formFields
      .filter((f) => formData[f.name]?.trim())
      .map((f) => `• ${f.label}: ${formData[f.name]}`);

    addMessage({
      content: `📝 **Form Submitted — ${formLabel(msg.formType)}**\n${summaryLines.join('\n')}`,
      sender: 'user',
      timestamp: new Date(),
    });
    saveToStorage();

    // Build the structured payload for the backend
    const pairs = msg.formFields
      .filter((f) => formData[f.name]?.trim())
      .map((f) => `${f.name}=${formData[f.name]}`);
    const payload = `[FORM_SUBMIT:${msg.formType}] ${pairs.join(' | ')}`;

    setIsLoading(true);
    scrollToBottom();

    const request = {
      message: payload,
      department: formData['department'] || selectedDepartment || undefined,
      chatHistory: buildChatHistory(false),
    };

    agentChat(request).then((r) => pushAssistantMessage(r)).catch(handleError);

    // Reset form data
    setFormData({});
  };

  const cancelForm = (msgIndex) => {
    setMessages((prev) =>
      prev.map((m, i) => (i === msgIndex ? { ...m, formSubmitted: true } : m))
    );
    setFormData({});
    addMessage({
      content: '❌ Form cancelled. Feel free to ask me anything else!',
      sender: 'assistant',
      timestamp: new Date(),
    });
    saveToStorage();
    scrollToBottom();
  };

  /* ═══════════════  QUICK REPLIES  ═══════════════ */

  const clickQuickReply = (msgIndex, msg, qr) => {
    if (msg.quickReplyClicked || isLoading) return;
    setMessages((prev) =>
      prev.map((m, i) => (i === msgIndex ? { ...m, quickReplyClicked: true } : m))
    );
    setUserInput(qr.payload);
    setTimeout(() => sendMessage(qr.payload), 0);
  };

  const setFormField = (name, value) => {
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  return (
    <>
      {/* ═══════════  AI CONSENT BANNER  ═══════════ */}
      {!aiConsentGiven && (
        <div className="ai-consent-overlay">
          <div className="ai-consent-modal">
            <div className="consent-icon">🤖</div>
            <h2>AI-Powered Health Assistant</h2>
            <p className="consent-intro">Before you begin, please review and acknowledge the following:</p>
            <ul className="consent-points">
              <li><strong>AI-Generated Content:</strong> All responses are generated by artificial intelligence and may not always be accurate.</li>
              <li><strong>Not Medical Advice:</strong> This assistant does not replace professional medical consultation. Always consult a qualified healthcare provider.</li>
              <li><strong>Data Handling:</strong> Your inputs are processed through safety guardrails including PII redaction, content moderation, and risk classification.</li>
              <li><strong>Human Escalation:</strong> High-risk queries are flagged and escalated for human review.</li>
              <li><strong>Audit Trail:</strong> All interactions are logged for safety, quality, and accountability purposes.</li>
            </ul>
            <div className="consent-actions">
              <button className="consent-accept-btn" onClick={acceptAiConsent}>
                I Understand — Continue
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="chat-page">
        {/* Sidebar Controls */}
        <div className="chat-sidebar">
          <h3>Chat Settings</h3>

          <div className="control-group">
            <label>Mode</label>
            <div className="toggle-group">
              <button
                className={useAgent && !useStreaming ? 'active' : ''}
                onClick={() => {
                  setUseAgent(true);
                  setUseStreaming(false);
                }}
              >
                Agent
              </button>
              <button
                className={!useAgent && !useStreaming ? 'active' : ''}
                onClick={() => {
                  setUseAgent(false);
                  setUseStreaming(false);
                }}
              >
                Sync
              </button>
              <button
                className={useStreaming ? 'active' : ''}
                onClick={() => {
                  setUseStreaming(true);
                  setUseAgent(false);
                }}
              >
                Stream
              </button>
            </div>
          </div>

          <div className="control-group">
            <label>Department Filter</label>
            <select
              value={selectedDepartment}
              onChange={(e) => setSelectedDepartment(e.target.value)}
            >
              <option value="">All Departments</option>
              {departments.map((d) => d && <option key={d} value={d}>{d}</option>)}
            </select>
          </div>

          <div className="mode-info">
            {useAgent && (
              <div>
                <strong>Agent Mode</strong> — Full orchestrator with routing, tools, RAG, and guardrails
              </div>
            )}
            {!useAgent && !useStreaming && (
              <div>
                <strong>Sync Mode</strong> — Direct RAG-enhanced chat response
              </div>
            )}
            {useStreaming && (
              <div>
                <strong>Stream Mode</strong> — Real-time streaming response
              </div>
            )}
          </div>

          {messages.length > 0 && (
            <button className="clear-chat-btn" onClick={clearChat}>
              🗑️ Clear Chat History
            </button>
          )}

          {/* Responsible AI Panel */}
          <div className="rai-panel">
            <button className="rai-toggle" onClick={() => setShowRaiPanel((v) => !v)}>
              <span className="rai-shield">🛡️</span>
              <span>Responsible AI</span>
              <span className={`rai-chevron ${showRaiPanel ? 'open' : ''}`}>▾</span>
            </button>
            {showRaiPanel && (
              <div className="rai-content">
                <div className="rai-item"><span className="rai-check">✅</span> Content Moderation</div>
                <div className="rai-item"><span className="rai-check">✅</span> PII Detection &amp; Redaction</div>
                <div className="rai-item"><span className="rai-check">✅</span> Prompt Injection Defense</div>
                <div className="rai-item"><span className="rai-check">✅</span> Risk Classification</div>
                <div className="rai-item"><span className="rai-check">✅</span> Human Escalation</div>
                <div className="rai-item"><span className="rai-check">✅</span> Audit Trail Logging</div>
                <div className="rai-item"><span className="rai-check">✅</span> RAG-Grounded Responses</div>
                <div className="rai-item"><span className="rai-check">✅</span> Output Safety Guardrails</div>
                <div className="rai-item"><span className="rai-check">✅</span> Medical Disclaimers</div>
                <div className="rai-item"><span className="rai-check">✅</span> Transparency Labels</div>
                <p className="rai-footer">Powered by Azure OpenAI with full guardrail pipeline on every request.</p>
              </div>
            )}
          </div>
        </div>

        {/* Chat Area */}
        <div className="chat-main">
          <div className="chat-messages" ref={chatContainer}>
            {/* ═══════════  WELCOME SCREEN  ═══════════ */}
            {showWelcome && (
              <div className="welcome-screen">
                {/* Animated header */}
                <div className="welcome-header">
                  <div className="welcome-logo-ring">
                    <div className="welcome-logo">
                      <img src="/logo.svg" width={40} height={40} alt="HealthAssist AI" />
                    </div>
                  </div>
                  <h1 className="welcome-title">
                    HealthAssist <span className="text-gradient-bright">AI</span>
                  </h1>
                  <p className="welcome-subtitle">Your intelligent healthcare companion at City General Hospital</p>
                </div>

                {/* Capability cards */}
                <div className="capability-grid">
                  {capabilities.map((cap, i) => (
                    <div
                      key={cap.title + i}
                      className="capability-card"
                      style={{ animationDelay: `${i * 0.08}s` }}
                      onClick={() => useSuggestion(cap.prompt)}
                    >
                      <div className="cap-icon" style={{ background: cap.gradient }}>
                        {cap.icon}
                      </div>
                      <div className="cap-info">
                        <h4>{cap.title}</h4>
                        <p>{cap.desc}</p>
                      </div>
                      <svg className="cap-arrow" width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <path d="M6 3l5 5-5 5" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    </div>
                  ))}
                </div>

                {/* Sample prompts */}
                <div className="welcome-prompts">
                  <p className="prompts-label">
                    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" style={{ verticalAlign: -2, marginRight: 4 }}>
                      <path d="M8 1v6l4 2" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
                      <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth={1.5} />
                    </svg>
                    Try asking
                  </p>
                  <div className="prompt-chips">
                    {samplePrompts.map((p, j) => (
                      <button
                        key={p}
                        className="welcome-prompt-chip"
                        style={{ animationDelay: `${0.5 + j * 0.06}s` }}
                        onClick={() => useSuggestion(p)}
                      >
                        <span className="chip-text">{p}</span>
                        <span className="chip-arrow">↗</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Disclaimer */}
                <div className="welcome-disclaimer">
                  <svg width="14" height="14" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                    <path d="M8 1l7 14H1L8 1z" stroke="currentColor" strokeWidth={1.5} strokeLinejoin="round" fill="none" />
                    <path d="M8 6v3M8 11.5v.5" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
                  </svg>
                  <span>
                    Not a replacement for professional medical advice. In an emergency, call <strong>911</strong> immediately.
                  </span>
                </div>
              </div>
            )}

            {/* ═══════════  MESSAGES  ═══════════ */}
            {messages.map((msg, msgIndex) => (
              <div key={msgIndex} className={`message ${msg.sender}`}>
                <div className={`message-bubble ${msg.escalated ? 'escalated' : ''}`}>
                  {/* AI-Generated Transparency Label */}
                  {msg.sender === 'assistant' && (
                    <div className="ai-generated-badge">
                      <span className="ai-badge-icon">🤖</span> AI-Generated
                    </div>
                  )}
                  <div
                    className="message-content"
                    dangerouslySetInnerHTML={{ __html: formatMarkdown(msg.content) }}
                  />

                  {/* ═══════════  INLINE FORM  ═══════════ */}
                  {msg.formType && msg.formFields && !msg.formSubmitted && (
                    <div className="chat-form">
                      <div className="form-header">
                        <span className="form-icon">
                          {formLabel(msg.formType).split(' ')[0]}
                        </span>
                        <span className="form-title">
                          {formLabel(msg.formType).substring(2).trim()}
                        </span>
                      </div>

                      <div className="form-body">
                        {msg.formFields.map((field) => (
                          <div key={field.name} className="form-group">
                            <label htmlFor={field.name}>
                              {field.label}
                              {field.required && <span className="required-star">*</span>}
                              {!field.required && <span className="optional-tag">optional</span>}
                            </label>

                            {['text', 'email', 'tel', 'number', 'date', 'time'].includes(field.type) && (
                              <input
                                type={field.type}
                                id={field.name}
                                placeholder={field.placeholder}
                                value={formData[field.name] || ''}
                                onChange={(e) => setFormField(field.name, e.target.value)}
                                required={field.required}
                                className="form-control"
                              />
                            )}

                            {field.type === 'select' && (
                              <select
                                id={field.name}
                                value={formData[field.name] || ''}
                                onChange={(e) => setFormField(field.name, e.target.value)}
                                required={field.required}
                                className="form-control"
                              >
                                <option value="" disabled>{field.placeholder}</option>
                                {field.options?.map((opt) => (
                                  <option key={opt} value={opt}>{opt}</option>
                                ))}
                              </select>
                            )}

                            {field.type === 'textarea' && (
                              <textarea
                                id={field.name}
                                placeholder={field.placeholder}
                                value={formData[field.name] || ''}
                                onChange={(e) => setFormField(field.name, e.target.value)}
                                required={field.required}
                                className="form-control"
                                rows={3}
                              />
                            )}
                          </div>
                        ))}
                      </div>

                      <div className="form-actions">
                        <button
                          className="btn-submit"
                          onClick={() => submitForm(msg, msgIndex)}
                          disabled={isLoading}
                        >
                          Submit
                        </button>
                        <button className="btn-cancel" onClick={() => cancelForm(msgIndex)}>
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}

                  {msg.formType && msg.formSubmitted && (
                    <div className="form-submitted-badge">Form submitted successfully</div>
                  )}

                  {/* ═══════════  QUICK REPLY BUTTONS  ═══════════ */}
                  {msg.quickReplies && msg.quickReplies.length > 0 && !msg.quickReplyClicked && (
                    <div className="quick-replies">
                      {msg.quickReplies.map((qr) => (
                        <button
                          key={qr.label}
                          className="quick-reply-btn"
                          onClick={() => clickQuickReply(msgIndex, msg, qr)}
                          disabled={isLoading}
                        >
                          {qr.label}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Risk Badge */}
                  {msg.riskTier && msg.sender === 'assistant' && (
                    <div className={`risk-badge ${getRiskClass(msg.riskTier)}`}>
                      {msg.riskTier} RISK
                    </div>
                  )}

                  {/* Citations */}
                  {msg.citations && msg.citations.length > 0 && (
                    <div className="citations">
                      <strong>Sources:</strong>
                      {msg.citations.map((c) => (
                        <span key={c} className="citation-chip">{c}</span>
                      ))}
                    </div>
                  )}

                  {/* Workflow ID */}
                  {msg.workflowId && (
                    <div className="workflow-id">
                      <Link to={`/audit?workflowId=${encodeURIComponent(msg.workflowId)}`}>
                        Audit: {msg.workflowId}
                      </Link>
                    </div>
                  )}

                  {/* Disclaimer */}
                  {msg.disclaimer && msg.sender === 'assistant' && (
                    <div className="disclaimer">{msg.disclaimer}</div>
                  )}
                </div>
                <div className="message-time">{formatDate(msg.timestamp, 'shortTime')}</div>
              </div>
            ))}

            {/* Loading indicator */}
            {isLoading && (
              <div className="message assistant">
                <div className="message-bubble loading">
                  <div className="typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Persistent Quick Action Chips */}
          {!showWelcome && (
            <div className="persistent-actions">
              {persistentActions.map((action) => (
                <button
                  key={action.label}
                  className="persistent-action-chip"
                  onClick={() => useSuggestion(action.prompt)}
                  disabled={isLoading}
                >
                  <span className="action-icon">{action.icon}</span>
                  <span>{action.label}</span>
                </button>
              ))}
            </div>
          )}

          {/* Input Area */}
          <div className="chat-input-area">
            <textarea
              value={userInput}
              onChange={(e) => setUserInput(e.target.value)}
              placeholder="Describe your symptoms, ask about insurance, report an issue..."
              onKeyPress={onKeyPress}
              disabled={isLoading}
              rows={2}
            />
            <button
              onClick={() => sendMessage()}
              disabled={isLoading || !userInput.trim()}
              className="send-btn"
            >
              {!isLoading && (
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <path d="M22 2L11 13" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
              {isLoading && (
                <span className="send-loading">
                  <span></span>
                  <span></span>
                  <span></span>
                </span>
              )}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
