import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  getAllAudits,
  getAuditByWorkflow,
  getWorkflowIds,
  getEscalatedAudits,
  getPiiAudits,
  getModerationFlaggedAudits,
} from '../services/auditService';
import { formatDate } from '../utils/format';
import './Audit.css';

function getStepClass(action) {
  if (action.includes('ERROR') || action.includes('BLOCK')) return 'step-error';
  if (action.includes('ESCALAT')) return 'step-warning';
  if (action.includes('COMPLET')) return 'step-success';
  return 'step-normal';
}

function formatMetadata(metadata) {
  if (!metadata) return '';
  try {
    return JSON.stringify(metadata, null, 2);
  } catch {
    return String(metadata);
  }
}

export default function Audit() {
  const [searchParams] = useSearchParams();
  const [auditLogs, setAuditLogs] = useState([]);
  const [workflowIds, setWorkflowIds] = useState([]);
  const [workflowId, setWorkflowId] = useState('');
  const [activeFilter, setActiveFilter] = useState('all');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const loadAll = () => {
    setIsLoading(true);
    setActiveFilter('all');
    setErrorMsg('');
    setWorkflowId('');
    getAllAudits()
      .then((data) => {
        setAuditLogs(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load audit data.');
        setIsLoading(false);
      });
  };

  const searchByWorkflow = (wfIdArg) => {
    const wfId = (wfIdArg ?? workflowId).trim();
    if (!wfId) return;
    setIsLoading(true);
    setErrorMsg('');
    setActiveFilter('search');
    setWorkflowId(wfId);
    getAuditByWorkflow(wfId)
      .then((data) => {
        setAuditLogs(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load audit trail.');
        setIsLoading(false);
      });
  };

  const loadEscalated = () => {
    setIsLoading(true);
    setActiveFilter('escalated');
    setErrorMsg('');
    getEscalatedAudits()
      .then((data) => {
        setAuditLogs(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load data.');
        setIsLoading(false);
      });
  };

  const loadPii = () => {
    setIsLoading(true);
    setActiveFilter('pii');
    setErrorMsg('');
    getPiiAudits()
      .then((data) => {
        setAuditLogs(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load data.');
        setIsLoading(false);
      });
  };

  const loadModeration = () => {
    setIsLoading(true);
    setActiveFilter('moderation');
    setErrorMsg('');
    getModerationFlaggedAudits()
      .then((data) => {
        setAuditLogs(data || []);
        setIsLoading(false);
      })
      .catch(() => {
        setErrorMsg('Failed to load data.');
        setIsLoading(false);
      });
  };

  const selectWorkflow = (wfId) => {
    searchByWorkflow(wfId);
  };

  // Load workflow IDs once
  useEffect(() => {
    getWorkflowIds()
      .then((ids) => setWorkflowIds(ids || []))
      .catch(() => {});
  }, []);

  // Handle initial workflowId from query params
  useEffect(() => {
    const wfFromQuery = searchParams.get('workflowId');
    if (wfFromQuery) {
      searchByWorkflow(wfFromQuery);
    } else {
      loadAll();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <div className="audit-page">
      {/* Page Hero */}
      <div className="page-hero">
        <svg
          className="page-hero-grid"
          width="100%"
          height="100%"
          xmlns="http://www.w3.org/2000/svg"
        >
          <defs>
            <pattern
              id="auditDots"
              x="0"
              y="0"
              width="20"
              height="20"
              patternUnits="userSpaceOnUse"
            >
              <circle cx="2" cy="2" r="1" fill="rgba(168,85,247,0.08)" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#auditDots)" />
        </svg>
        <div className="page-hero-orb"></div>
        <div className="page-hero-content">
          <div className="page-hero-left">
            <div className="page-hero-icon">
              <svg
                width="26"
                height="26"
                fill="none"
                viewBox="0 0 24 24"
                stroke="white"
                strokeWidth="2"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
            <div>
              <h1>Audit Trail</h1>
              <p>Track AI interactions, guardrail decisions, and workflow steps</p>
            </div>
          </div>
        </div>
      </div>

      {/* Search and Filters */}
      <div className="controls-bar">
        <div className="search-group">
          <select
            value={workflowId}
            onChange={(e) => {
              const v = e.target.value;
              setWorkflowId(v);
              if (v) searchByWorkflow(v);
              else loadAll();
            }}
            className="workflow-select"
          >
            <option value="">-- Select Workflow ID --</option>
            {workflowIds.map((wf) => (
              <option key={wf} value={wf}>
                {wf}
              </option>
            ))}
          </select>
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
              value={workflowId}
              onChange={(e) => setWorkflowId(e.target.value)}
              onKeyUp={(e) => {
                if (e.key === 'Enter') searchByWorkflow();
              }}
              placeholder="Or type Workflow ID..."
            />
          </div>
          <button className="btn-primary" onClick={() => searchByWorkflow()}>
            <svg
              width="14"
              height="14"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth="2"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            Search
          </button>
        </div>
        <div className="filter-tabs">
          <button
            className={activeFilter === 'all' ? 'active' : ''}
            onClick={loadAll}
          >
            All
          </button>
          <button
            className={activeFilter === 'escalated' ? 'active' : ''}
            onClick={loadEscalated}
          >
            <svg
              width="12"
              height="12"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z"
              />
            </svg>
            Escalated
          </button>
          <button
            className={activeFilter === 'pii' ? 'active' : ''}
            onClick={loadPii}
          >
            <svg
              width="12"
              height="12"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
              />
            </svg>
            PII Detected
          </button>
          <button
            className={activeFilter === 'moderation' ? 'active' : ''}
            onClick={loadModeration}
          >
            <svg
              width="12"
              height="12"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
              />
            </svg>
            Moderation
          </button>
        </div>
      </div>

      {/* Error */}
      {errorMsg && (
        <div className="alert error">
          <svg
            width="16"
            height="16"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
          {errorMsg}
        </div>
      )}

      {/* Loading */}
      {isLoading && (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading audit data...</p>
        </div>
      )}

      {/* Audit Timeline */}
      {!isLoading && auditLogs.length > 0 && (
        <div className="audit-timeline">
          {auditLogs.map((log, i) => {
            const stepClass = getStepClass(log.action);
            return (
              <div key={log.id ?? i} className="timeline-item">
                <div className={`timeline-marker ${stepClass}`}>{i + 1}</div>
                <div
                  className="timeline-content clickable"
                  onClick={() => selectWorkflow(log.workflowId)}
                >
                  <div className={`timeline-accent ${stepClass}`}></div>
                  <div className="timeline-header">
                    <span className={`step-badge ${stepClass}`}>{log.action}</span>
                    <span className="timestamp">
                      <svg
                        width="12"
                        height="12"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <circle cx="12" cy="12" r="10" />
                        <path d="M12 6v6l4 2" />
                      </svg>
                      {formatDate(log.createdAt, 'medium')}
                    </span>
                  </div>
                  <div className="timeline-body">
                    <div className="detail-row">
                      <svg
                        width="14"
                        height="14"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101"
                        />
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M10.172 13.828a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.102 1.101"
                        />
                      </svg>
                      <strong>Workflow:</strong>{' '}
                      <a
                        className="workflow-link"
                        onClick={(e) => {
                          e.stopPropagation();
                          selectWorkflow(log.workflowId);
                        }}
                      >
                        {log.workflowId}
                      </a>
                    </div>
                    {log.inputData && (
                      <div className="detail-row">
                        <svg
                          width="14"
                          height="14"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth="2"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
                          />
                        </svg>
                        <strong>Input:</strong> {log.inputData}
                      </div>
                    )}
                    {log.outputData && (
                      <div className="detail-row">
                        <svg
                          width="14"
                          height="14"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth="2"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
                          />
                        </svg>
                        <strong>Response:</strong>
                        <div className="response-text">{log.outputData}</div>
                      </div>
                    )}
                    {log.riskTier && (
                      <div className="detail-row">
                        <svg
                          width="14"
                          height="14"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth="2"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
                          />
                        </svg>
                        <strong>Risk:</strong>
                        <span className={`risk-tag risk-${log.riskTier.toLowerCase()}`}>
                          {log.riskTier}
                        </span>
                      </div>
                    )}
                    {(log.escalated || log.piiDetected || log.moderationFlagged) && (
                      <div className="detail-row flags">
                        {log.escalated && (
                          <span className="flag escalated">
                            <svg
                              width="10"
                              height="10"
                              fill="none"
                              viewBox="0 0 24 24"
                              stroke="currentColor"
                              strokeWidth="2"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M12 9v2m0 4h.01"
                              />
                            </svg>
                            Escalated
                          </span>
                        )}
                        {log.piiDetected && (
                          <span className="flag pii">
                            <svg
                              width="10"
                              height="10"
                              fill="none"
                              viewBox="0 0 24 24"
                              stroke="currentColor"
                              strokeWidth="2"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                              />
                            </svg>
                            PII Detected
                          </span>
                        )}
                        {log.moderationFlagged && (
                          <span className="flag moderation">
                            <svg
                              width="10"
                              height="10"
                              fill="none"
                              viewBox="0 0 24 24"
                              stroke="currentColor"
                              strokeWidth="2"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z"
                              />
                            </svg>
                            Moderation Flagged
                          </span>
                        )}
                      </div>
                    )}
                    {log.guardrailResults && (
                      <div className="detail-row">
                        <details>
                          <summary>
                            <svg
                              width="12"
                              height="12"
                              fill="none"
                              viewBox="0 0 24 24"
                              stroke="currentColor"
                              strokeWidth="2"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
                              />
                            </svg>
                            Guardrail Details
                          </summary>
                          <pre>{formatMetadata(log.guardrailResults)}</pre>
                        </details>
                      </div>
                    )}
                    {log.toolCalls && (
                      <div className="detail-row">
                        <details>
                          <summary>
                            <svg
                              width="12"
                              height="12"
                              fill="none"
                              viewBox="0 0 24 24"
                              stroke="currentColor"
                              strokeWidth="2"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                              />
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                              />
                            </svg>
                            Tool Calls
                          </summary>
                          <pre>{formatMetadata(log.toolCalls)}</pre>
                        </details>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {!isLoading && auditLogs.length === 0 && (
        <div className="empty-state">
          <svg
            width="48"
            height="48"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth="1.5"
            style={{ color: 'var(--gray-300)' }}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
          <p>No audit records found. Start a chat conversation to generate audit data.</p>
        </div>
      )}
    </div>
  );
}
