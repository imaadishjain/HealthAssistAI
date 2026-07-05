import { createContext, useContext, useState, useEffect, useRef } from 'react';

const STORAGE_KEY = 'healthassist_chat_history';

const ChatContext = createContext(undefined);

function loadFromStorage() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return [];
    const parsed = JSON.parse(stored);
    return parsed.map((m) => ({
      ...m,
      timestamp: new Date(m.timestamp),
      // Strip forms from restored messages — prevent stale form re-submission
      formSubmitted: m.formType ? true : m.formSubmitted,
      quickReplyClicked: true, // Don't re-show old quick replies
    }));
  } catch {
    return [];
  }
}

export function ChatProvider({ children }) {
  const [messages, setMessagesState] = useState(() => loadFromStorage());
  const [formData, setFormDataState] = useState({});
  const [useStreaming, setUseStreaming] = useState(false);
  const [useAgent, setUseAgent] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  // Latest ref so saveToStorage always reads current messages
  const messagesRef = useRef(messages);
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const saveToStorage = () => {
    try {
      const serializable = messagesRef.current.map((m) => ({
        ...m,
        timestamp: m.timestamp instanceof Date ? m.timestamp.toISOString() : m.timestamp,
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(serializable));
    } catch {
      /* storage unavailable — ignore */
    }
  };

  const setMessages = (updater) => {
    setMessagesState((prev) => (typeof updater === 'function' ? updater(prev) : updater));
  };

  const setFormData = (updater) => {
    setFormDataState((prev) =>
      typeof updater === 'function' ? updater(prev) : updater
    );
  };

  const addMessage = (msg) => {
    setMessagesState((prev) => [...prev, msg]);
  };

  const updateMessage = (index, updater) => {
    setMessagesState((prev) => prev.map((m, i) => (i === index ? updater(m) : m)));
  };

  const clearHistory = () => {
    setMessagesState([]);
    localStorage.removeItem(STORAGE_KEY);
  };

  return (
    <ChatContext.Provider
      value={{
        messages,
        setMessages,
        addMessage,
        updateMessage,
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
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const ctx = useContext(ChatContext);
  if (!ctx) throw new Error('useChat must be used within ChatProvider');
  return ctx;
}
