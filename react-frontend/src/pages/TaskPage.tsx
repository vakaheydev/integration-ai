import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box, Paper, Typography, IconButton, Chip, CircularProgress,
  Alert, Divider, Tooltip, Button, LinearProgress,
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Select, MenuItem, FormControl, InputLabel,
  Stepper, Step, StepLabel,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon, Refresh as RefreshIcon, Assignment as AssignmentIcon,
  CheckCircle as CheckCircleIcon, Error as ErrorIcon, Schedule as ScheduleIcon,
  PlayArrow as PlayArrowIcon, HourglassEmpty as HourglassIcon,
  Replay as ReloadIcon, Delete as DeleteIcon, ContentCopy as CopyIcon,
  ArrowUpward as ArrowUpIcon, ExpandMore as ExpandMoreIcon, ExpandLess as ExpandLessIcon,
  Send as SendIcon, Close as CloseIcon, CallSplit as FromBaseIcon,
  QuestionAnswer as QuestionAnswerIcon,
  ThumbUp as ThumbUpIcon, ThumbDown as ThumbDownIcon,
  AccountTree as AccountTreeIcon,
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { tasksApi } from '../api/tasksApi';
import { documentsApi } from '../api/documentsApi';
import type { Task, TaskStage, SwaggerDocument, AIModel } from '../models/types';
import { parseMessageWithCode } from '../utils/messageParser';
import { CodeBlock } from '../components/CodeBlock';

interface TaskChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

const POLL_INTERVAL = 1000;

const statusConfig: Record<string, { label: string; color: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'; icon: React.ReactElement }> = {
  CREATED:   { label: 'Created',   color: 'default',  icon: <ScheduleIcon fontSize="small" /> },
  RUNNING:   { label: 'Running',   color: 'primary',  icon: <PlayArrowIcon fontSize="small" /> },
  WAITING:   { label: 'Waiting',   color: 'warning',  icon: <HourglassIcon fontSize="small" /> },
  WAITING_USER_INPUT: { label: 'Waiting for your input', color: 'warning', icon: <QuestionAnswerIcon fontSize="small" /> },
  WAITING_USER_APPROVE: { label: 'Waiting for your approval', color: 'warning', icon: <ThumbUpIcon fontSize="small" /> },
  WAITING_SUBTASK: { label: 'Subtask running', color: 'info', icon: <HourglassIcon fontSize="small" /> },
  COMPLETED: { label: 'Completed', color: 'success',  icon: <CheckCircleIcon fontSize="small" /> },
  FAILED:    { label: 'Failed',    color: 'error',    icon: <ErrorIcon fontSize="small" /> },
};

const typeLabels: Record<string, string> = {
  ANALYZE: 'Analysis', CODE: 'Code generation', TEST: 'Test generation',
  ANALYZE_CODE: 'Analysis + Code', ANALYZE_TEST: 'Analysis + Tests',
};

function formatDuration(iso: string | null | undefined): string {
  if (!iso) return '—';
  const match = iso.match(/PT(?:(\d+)M)?(?:([\d.]+)S)?/);
  if (!match) return iso;
  const mins = match[1] ? `${match[1]}m ` : '';
  const secs = match[2] ? `${parseFloat(match[2]).toFixed(2)}s` : '';
  return mins + secs || iso;
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

const mdStyles = {
  '& p': { mt: 0, mb: 1 },
  '& h1, & h2, & h3, & h4': { mt: 1.5, mb: 0.5 },
  '& ul, & ol': { pl: 2.5, mb: 1 },
  '& li': { mb: 0.25 },
  '& code': { bgcolor: 'grey.100', px: 0.5, py: 0.25, borderRadius: 0.5, fontFamily: 'monospace', fontSize: '0.85rem' },
  '& pre': { bgcolor: 'grey.100', p: 1.5, borderRadius: 1, overflow: 'auto', fontFamily: 'monospace', fontSize: '0.85rem', mb: 1 },
  '& pre code': { bgcolor: 'transparent', p: 0 },
  '& blockquote': { borderLeft: '3px solid', borderColor: 'primary.light', pl: 1.5, ml: 0, color: 'text.secondary' },
  fontSize: '0.9rem',
};

const stageCircleBase = {
  width: 220, borderRadius: '50px',
  display: 'flex', flexDirection: 'column' as const, alignItems: 'center', justifyContent: 'center',
  px: 2, textAlign: 'center' as const,
};

const stageColorMap: Record<string, { bg: string; hover: string }> = {
  COMPLETED: { bg: '#2e7d32', hover: '#1b5e20' },
  FAILED:    { bg: '#c62828', hover: '#7f0000' },
  WAITING:   { bg: '#f57f17', hover: '#e65100' },
  WAITING_USER_INPUT: { bg: '#f57f17', hover: '#e65100' },
  WAITING_USER_APPROVE: { bg: '#f57f17', hover: '#e65100' },
  WAITING_SUBTASK: { bg: '#1565c0', hover: '#0d47a1' },
  CREATED:   { bg: '#1565c0', hover: '#0d47a1' },
  RUNNING:   { bg: '#1565c0', hover: '#0d47a1' },
};

function stageColor(stage: TaskStage) {
  return stageColorMap[stage.status] ?? stageColorMap.CREATED;
}

/**
 * Предобрабатывает текст: заменяет "Название" на [Название](doc:id) если документ найден.
 * Поддерживает все виды кавычек: "…", "…", «…», „…"
 */
// REMOVED: linkifyDocuments
// REMOVED: makeDocLinkComponents

interface SelectionPopup {
  doc: SwaggerDocument;
  x: number;
  y: number;
}

/** Следит за выделением текста: если выделение не снято 0.5с — ищет документ */
function useDocumentSelection(
  documents: SwaggerDocument[],
  onFound: (popup: SelectionPopup) => void,
  onClear: () => void,
) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onFoundRef = useRef(onFound);
  const onClearRef = useRef(onClear);
  // Обновляем ref при каждом рендере без пересоздания эффекта
  useEffect(() => { onFoundRef.current = onFound; }, [onFound]);
  useEffect(() => { onClearRef.current = onClear; }, [onClear]);

  useEffect(() => {
    const handleSelectionChange = () => {
      if (timerRef.current) clearTimeout(timerRef.current);

      const sel = window.getSelection();
      const text = sel?.toString().trim() ?? '';

      if (!text || text.length < 2) {
        onClearRef.current();
        return;
      }

      timerRef.current = setTimeout(() => {
        const currentSel = window.getSelection();
        const currentText = currentSel?.toString().trim() ?? '';
        if (!currentText || currentText !== text) return;

        const doc = documents.find(
          d =>
            (d.name ?? '').toLowerCase() === currentText.toLowerCase() ||
            d.id.toLowerCase() === currentText.toLowerCase()
        );

        if (doc && currentSel && currentSel.rangeCount > 0) {
          const range = currentSel.getRangeAt(0);
          const rect = range.getBoundingClientRect();
          onFoundRef.current({ doc, x: rect.left + rect.width / 2, y: rect.bottom + 8 });
        } else {
          onClearRef.current();
        }
      }, 500);
    };

    document.addEventListener('selectionchange', handleSelectionChange);
    return () => {
      document.removeEventListener('selectionchange', handleSelectionChange);
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documents]); // только documents — не пересоздаём при смене колбэков
}

export const TaskPage: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [selectedStage, setSelectedStage] = useState<TaskStage | null>(null);
  const [infoExpanded, setInfoExpanded] = useState(true);
  const [resultExpanded, setResultExpanded] = useState(true);
  const [historyExpanded, setHistoryExpanded] = useState(true);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Retry dialog
  const [retryDialogOpen, setRetryDialogOpen] = useState(false);
  const [retryMessage, setRetryMessage] = useState('');
  const [retrying, setRetrying] = useState(false);

  // Create from base dialog
  const [fromBaseDialogOpen, setFromBaseDialogOpen] = useState(false);
  const [fromBaseMessage, setFromBaseMessage] = useState('');
  const [creatingFromBase, setCreatingFromBase] = useState(false);

  // AI question (WAITING_USER_INPUT) dialog
  const [userInputDialogOpen, setUserInputDialogOpen] = useState(false);
  const [userInputAnswer, setUserInputAnswer] = useState('');
  const [submittingUserInput, setSubmittingUserInput] = useState(false);
  const lastShownInputKeyRef = useRef<string | null>(null);

  // Approve dialog (WAITING_USER_APPROVE)
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [disapproveReason, setDisapproveReason] = useState('');
  const [showDisapproveField, setShowDisapproveField] = useState(false);
  const [submittingApproval, setSubmittingApproval] = useState(false);
  const lastShownApproveKeyRef = useRef<string | null>(null);

  // Subtasks for scenario tasks
  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [subtasksLoading, setSubtasksLoading] = useState(false);

  // Last code execution result (stdout/stderr) — shared from CodeBlock
  const [lastExecResult, setLastExecResult] = useState<{ stdout: string; stderr: string } | null>(null);

  // Chat state
  const [chatMessages, setChatMessages] = useState<TaskChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [chatError, setChatError] = useState<string | null>(null);
  const [chatRole, setChatRole] = useState<string>('analytic');
  const [chatRoleDialogOpen, setChatRoleDialogOpen] = useState(false);
  const [tempChatRole, setTempChatRole] = useState<string>('analytic');
  const [chatExpanded, setChatExpanded] = useState(true);
  const chatContainerRef = useRef<HTMLDivElement | null>(null);

  // Model selection for chat
  const [chatModel, setChatModel] = useState<string>('');
  const [availableModels, setAvailableModels] = useState<AIModel[]>([]);
  const [defaultModel, setDefaultModel] = useState<string>('');
  const [chatModelDialogOpen, setChatModelDialogOpen] = useState(false);
  const [tempChatModel, setTempChatModel] = useState<string>('');

  // Documents for linked text
  const [documents, setDocuments] = useState<SwaggerDocument[]>([]);

  // Selection popup
  const [selectionPopup, setSelectionPopup] = useState<SelectionPopup | null>(null);
  const [selectionPopupExpanded, setSelectionPopupExpanded] = useState(false);
  const popupRef = useRef<HTMLDivElement | null>(null);

  const handlePopupExpand = () => setSelectionPopupExpanded(v => !v);

  const closePopup = useCallback(() => {
    setSelectionPopup(null);
    setSelectionPopupExpanded(false);
  }, []);

  const closePopupRef = useRef(closePopup);
  useEffect(() => { closePopupRef.current = closePopup; }, [closePopup]);

  // Закрываем по mousedown вне попапа — вешаем один раз
  useEffect(() => {
    const handleMouseDown = (e: MouseEvent) => {
      if (!popupRef.current) return;
      if (!popupRef.current.contains(e.target as Node)) {
        closePopupRef.current();
      }
    };
    document.addEventListener('mousedown', handleMouseDown);
    return () => document.removeEventListener('mousedown', handleMouseDown);
  }, []);

  useDocumentSelection(
    documents,
    (popup) => { setSelectionPopup(popup); setSelectionPopupExpanded(false); },
    () => { /* не закрываем по selectionchange — только mousedown вне или крестик */ },
  );

  const isTerminal = (status?: string) => status === 'COMPLETED' || status === 'FAILED';

  const isScenarioTask = (t: Task) => t.type === 'ANALYZE_CODE' || t.type === 'ANALYZE_TEST' || t.status === 'WAITING_SUBTASK';

  const fetchTask = useCallback(async (silent = false) => {
    if (!taskId) return;
    if (!silent) setLoading(true);
    try {
      const t = await tasksApi.getTask(taskId);
      setTask(t);
      setError(null);
      if (isTerminal(t.status)) {
        if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
      }
      // Load subtasks for scenario tasks
      if (isScenarioTask(t)) {
        if (!silent) setSubtasksLoading(true);
        tasksApi.getSubtasks(taskId).then(setSubtasks).catch(() => setSubtasks([])).finally(() => setSubtasksLoading(false));
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading task');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    let cancelled = false;

    const init = async () => {
      if (cancelled) return;
      await fetchTask();
      if (cancelled) return;
      documentsApi.getDocuments().then(docs => { if (!cancelled) setDocuments(docs); }).catch(() => {});
      tasksApi.getModels().then(data => {
        if (!cancelled) {
          const models = data.availableModels ?? [];
          const def = data.defaultModel ?? '';
          setAvailableModels(models);
          setDefaultModel(def);
          setChatModel(prev => prev || def);
          setTempChatModel(prev => prev || def);
        }
      }).catch(() => {});
      intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    };

    init();

    return () => {
      cancelled = true;
      if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
    };
  }, [taskId]);

  const handleReload = () => {
    setRetryMessage('');
    setRetryDialogOpen(true);
  };

  const handleRetryConfirm = async () => {
    if (!task) return;
    setRetrying(true);
    try {
      // Если есть stdout/stderr — добавляем в сообщение
      let message = retryMessage.trim();
      if (lastExecResult) {
        const parts: string[] = [];
        if (message) parts.push(message);
        if (lastExecResult.stdout)
          parts.push(`[Code execution stdout]:\n${lastExecResult.stdout}`);
        if (lastExecResult.stderr)
          parts.push(`[Code execution stderr]:\n${lastExecResult.stderr}`);
        message = parts.join('\n\n');
      }
      const updated = await tasksApi.reloadTask(task.id, message || undefined);
      setTask(updated);
      setRetryDialogOpen(false);
      setRetryMessage('');
      if (!isTerminal(updated.status) && !intervalRef.current)
        intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error retrying task');
    } finally {
      setRetrying(false);
    }
  };

  const handleCreateFromBase = async () => {
    if (!task) return;
    setCreatingFromBase(true);
    try {
      const newTask = await tasksApi.createFromBase(task.id, fromBaseMessage.trim() || undefined);
      setFromBaseDialogOpen(false);
      setFromBaseMessage('');
      navigate(`/tasks/${newTask.id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error creating task from base');
    } finally {
      setCreatingFromBase(false);
    }
  };

  // Auto-open user input dialog when task status is WAITING_USER_INPUT
  useEffect(() => {
    if (!task || task.status !== 'WAITING_USER_INPUT') return;
    const key = `${task.id}:${task.currentStage?.aiQuestion ?? ''}`;
    if (lastShownInputKeyRef.current === key) return;
    lastShownInputKeyRef.current = key;
    setUserInputAnswer('');
    setUserInputDialogOpen(true);
  }, [task?.status, task?.currentStage?.aiQuestion, task?.id]);

  const handleSubmitUserInput = async () => {
    if (!task || !userInputAnswer.trim()) return;
    setSubmittingUserInput(true);
    try {
      const updated = await tasksApi.resolveInput(task.id, userInputAnswer.trim());
      setTask(updated);
      setUserInputDialogOpen(false);
      setUserInputAnswer('');
      if (!isTerminal(updated.status) && !intervalRef.current)
        intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error submitting answer');
    } finally {
      setSubmittingUserInput(false);
    }
  };

  // Auto-open approve dialog when task status is WAITING_USER_APPROVE
  useEffect(() => {
    if (!task || task.status !== 'WAITING_USER_APPROVE') return;
    const key = `${task.id}:WAITING_USER_APPROVE`;
    if (lastShownApproveKeyRef.current === key) return;
    lastShownApproveKeyRef.current = key;
    setDisapproveReason('');
    setShowDisapproveField(false);
    setApproveDialogOpen(true);
  }, [task?.status, task?.id]);

  const handleApprove = async () => {
    if (!task) return;
    setSubmittingApproval(true);
    try {
      const updated = await tasksApi.approveTask(task.id, true);
      setTask(updated);
      setApproveDialogOpen(false);
      if (!isTerminal(updated.status) && !intervalRef.current)
        intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error approving task');
    } finally {
      setSubmittingApproval(false);
    }
  };

  const handleDisapprove = async () => {
    if (!task) return;
    setSubmittingApproval(true);
    try {
      const updated = await tasksApi.approveTask(task.id, false, disapproveReason.trim() || undefined);
      setTask(updated);
      setApproveDialogOpen(false);
      setDisapproveReason('');
      setShowDisapproveField(false);
      if (!isTerminal(updated.status) && !intervalRef.current)
        intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error disapproving task');
    } finally {
      setSubmittingApproval(false);
    }
  };

  const handleDelete = async () => {
    if (!task) return;
    setDeleting(true);
    setDeleteConfirm(false);
    try {
      await tasksApi.deleteTask(task.id);
      navigate(-1);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error deleting task');
      setDeleting(false);
    }
  };

  const cfg = task
    ? (statusConfig[task.status] ?? { label: task.status, color: 'default' as const, icon: <ScheduleIcon fontSize="small" /> })
    : null;

  const handleChatSend = async () => {
    if (!chatInput.trim() || !taskId || chatLoading) return;
    const userMsg: TaskChatMessage = { role: 'user', content: chatInput.trim() };
    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setChatLoading(true);
    setChatError(null);
    try {
      const res = await tasksApi.chatWithTask(taskId, userMsg.content, chatRole, chatModel || undefined);
      const text =
        res?.answer ??
        res?.response ??
        res?.text ??
        res?.message ??
        (typeof res === 'string' ? res : JSON.stringify(res));
      setChatMessages(prev => [...prev, { role: 'assistant', content: text }]);
    } catch (err: any) {
      setChatError(err.response?.data?.message || 'Error sending message');
    } finally {
      setChatLoading(false);
      setTimeout(() => {
        if (chatContainerRef.current) {
          chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
        }
      }, 100);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50', p: 3 }}>
      <Box sx={{ maxWidth: 860, mx: 'auto' }}>

        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
          <IconButton onClick={() => navigate(-1)}><ArrowBackIcon /></IconButton>
          <AssignmentIcon color="primary" />
          <Typography variant="h5" fontWeight="bold" sx={{ flexGrow: 1 }}>
            {task?.parentTaskId ? 'Subtask' : 'Task'}
          </Typography>
          {task?.parentTaskId && (
            <Button
              variant="outlined"
              size="small"
              onClick={() => navigate(`/tasks/${task.parentTaskId}`)}
              startIcon={<AccountTreeIcon />}
            >
              Parent task
            </Button>
          )}
          {task && !isTerminal(task.status) && <CircularProgress size={20} sx={{ mr: 1 }} />}
          <Tooltip title="Refresh">
            <IconButton onClick={() => fetchTask()} size="small"><RefreshIcon /></IconButton>
          </Tooltip>
          {task && (
            <>
              <Tooltip title="Create task based on this one"><span>
                <Button variant="outlined" color="primary" size="small" onClick={() => { setFromBaseMessage(''); setFromBaseDialogOpen(true); }} startIcon={<FromBaseIcon />} disabled={deleting}>
                  From base
                </Button>
              </span></Tooltip>
              <Tooltip title="Retry task"><span>
                <Button variant="outlined" size="small" onClick={handleReload} startIcon={<ReloadIcon />} disabled={deleting}>Retry</Button>
              </span></Tooltip>
              <Tooltip title="Delete task"><span>
                <Button variant="outlined" color="error" size="small" onClick={() => setDeleteConfirm(true)} startIcon={<DeleteIcon />} disabled={deleting}>
                  {deleting ? 'Deleting...' : 'Delete'}
                </Button>
              </span></Tooltip>
            </>
          )}
        </Box>

        {loading && !task && <LinearProgress sx={{ mb: 2 }} />}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        {/* WAITING_USER_INPUT banner */}
        {task?.status === 'WAITING_USER_INPUT' && task.currentStage?.aiQuestion && (
          <Alert
            severity="warning"
            sx={{ mb: 2 }}
            icon={<QuestionAnswerIcon />}
            action={
              <Button
                color="warning"
                size="small"
                variant="contained"
                onClick={() => {
                  setUserInputAnswer('');
                  setUserInputDialogOpen(true);
                }}
                startIcon={<SendIcon />}
              >
                Answer
              </Button>
            }
          >
            <Typography variant="subtitle2" fontWeight="bold" sx={{ mb: 0.5 }}>
              AI is waiting for your input
            </Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
              {task.currentStage.aiQuestion!.length > 200
                ? task.currentStage.aiQuestion!.slice(0, 200) + '...'
                : task.currentStage.aiQuestion}
            </Typography>
           </Alert>
        )}

        {/* WAITING_USER_APPROVE banner */}
        {task?.status === 'WAITING_USER_APPROVE' && (
          <Alert
            severity="warning"
            sx={{ mb: 2 }}
            icon={<ThumbUpIcon />}
            action={
              <Button
                color="warning"
                size="small"
                variant="contained"
                onClick={() => {
                  setDisapproveReason('');
                  setApproveDialogOpen(true);
                }}
              >
                Review
              </Button>
            }
          >
            <Typography variant="subtitle2" fontWeight="bold" sx={{ mb: 0.5 }}>
              AI is waiting for your approval
            </Typography>
            {task.currentStage?.approveDescription && (
              <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
                {task.currentStage.approveDescription.length > 200
                  ? task.currentStage.approveDescription.slice(0, 200) + '...'
                  : task.currentStage.approveDescription}
              </Typography>
            )}
          </Alert>
        )}

        {task && (
          <>
            {/* ── Chat ── */}
            <Paper elevation={2} sx={{ mb: 3 }}>
              <Box
                sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 3, py: 2, cursor: 'pointer', userSelect: 'none' }}
                onClick={() => setChatExpanded(v => !v)}
              >
                <Typography variant="subtitle1" fontWeight="bold">Chat with AI about this task</Typography>
                <IconButton size="small">{chatExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}</IconButton>
              </Box>
              {chatExpanded && (
                <>
                  <Divider />
                  <Box ref={chatContainerRef} sx={{ px: 3, py: 2, maxHeight: 480, minHeight: 120, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                    {chatMessages.length === 0 && (
                      <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
                        Ask anything about this task...
                      </Typography>
                    )}
                    {chatMessages.map((msg, idx) => (
                      <Box key={idx} sx={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                        <Box sx={{
                          maxWidth: '80%', px: 2, py: 1,
                          borderRadius: msg.role === 'user' ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
                          bgcolor: msg.role === 'user' ? 'primary.main' : 'grey.100',
                          color: msg.role === 'user' ? 'white' : 'text.primary',
                        }}>
                          {msg.role === 'assistant' ? (
                            <Box sx={{ '& p': { mt: 0, mb: 0.5 }, fontSize: '0.875rem' }}>
                              {parseMessageWithCode(msg.content).map((part, partIdx) =>
                                part.type === 'code' ? (
                                  <CodeBlock key={partIdx} code={part.content} language={part.language} taskId={taskId} />
                                ) : (
                                  <Box key={partIdx} sx={{ ...mdStyles, '& p': { mt: 0, mb: 0.5 }, fontSize: '0.875rem' }}>
                                    <ReactMarkdown>{part.content}</ReactMarkdown>
                                  </Box>
                                )
                              )}
                            </Box>
                          ) : (
                            <Typography variant="body2">{msg.content}</Typography>
                          )}
                        </Box>
                      </Box>
                    ))}
                    {chatLoading && (
                      <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                        <Box sx={{ px: 2, py: 1, borderRadius: '16px 16px 16px 4px', bgcolor: 'grey.100', display: 'flex', alignItems: 'center', gap: 1 }}>
                          <CircularProgress size={16} />
                          <Typography variant="caption" color="text.secondary">Generating response...</Typography>
                        </Box>
                      </Box>
                    )}
                  </Box>
                  <Divider />
                  <Box sx={{ px: 3, py: 2 }}>
                    {chatError && (
                      <Alert severity="error" sx={{ mb: 1.5 }} onClose={() => setChatError(null)}>{chatError}</Alert>
                    )}
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-end' }}>
                      <Tooltip title="Select role">
                        <Button
                          variant="contained"
                          onClick={() => { setTempChatRole(chatRole); setChatRoleDialogOpen(true); }}
                          disabled={chatLoading}
                          sx={{ minWidth: 'auto', px: 1.5, height: '40px', flexShrink: 0 }}
                        >
                          <Typography variant="caption" fontWeight="bold">ROLE</Typography>
                        </Button>
                      </Tooltip>
                      <Tooltip title="Select model">
                        <Button
                          variant="contained"
                          color="secondary"
                          onClick={() => { setTempChatModel(chatModel); setChatModelDialogOpen(true); }}
                          disabled={chatLoading}
                          sx={{ minWidth: 'auto', px: 1.5, height: '40px', flexShrink: 0 }}
                        >
                          <Typography variant="caption" fontWeight="bold">MODEL</Typography>
                        </Button>
                      </Tooltip>
                      <TextField
                        fullWidth
                        multiline
                        maxRows={4}
                        size="small"
                        placeholder="Type your message..."
                        value={chatInput}
                        onChange={e => setChatInput(e.target.value)}
                        onKeyDown={e => {
                          if (e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            handleChatSend();
                          }
                        }}
                        disabled={chatLoading}
                      />
                      <IconButton color="primary" onClick={handleChatSend} disabled={!chatInput.trim() || chatLoading}>
                        <SendIcon />
                      </IconButton>
                    </Box>
                  </Box>
                </>
              )}
            </Paper>

            {/* ── Task info ── */}
            <Paper elevation={2} sx={{ mb: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 3, py: 2, cursor: 'pointer', userSelect: 'none' }}
                onClick={() => setInfoExpanded(v => !v)}>
                <Typography variant="subtitle1" fontWeight="bold">Task info</Typography>
                <IconButton size="small">{infoExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}</IconButton>
              </Box>
              {infoExpanded && (
                <Box sx={{ px: 3, pb: 3 }}>
                  <Divider sx={{ mb: 2 }} />
                  <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start', mb: 2 }}>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="caption" color="text.secondary">Type</Typography>
                      <Typography variant="subtitle1" fontWeight="bold">{typeLabels[task.type] ?? task.type}</Typography>
                    </Box>
                    {task.model && (
                      <Box>
                        <Typography variant="caption" color="text.secondary">Model</Typography>
                        <Typography variant="subtitle1">{task.model}</Typography>
                      </Box>
                    )}
                    <Box>
                      <Typography variant="caption" color="text.secondary">Status</Typography>
                      <Box sx={{ mt: 0.5 }}>
                        {cfg && <Chip icon={cfg.icon} label={cfg.label} color={cfg.color} size="small" />}
                      </Box>
                    </Box>
                  </Box>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="caption" color="text.secondary">Description</Typography>
                  <Typography variant="body1" sx={{ mt: 0.5, whiteSpace: 'pre-line' }}>{task.description}</Typography>
                  {task.currentStage && (<>
                    <Divider sx={{ my: 2 }} />
                    <Typography variant="caption" color="text.secondary">Current stage</Typography>
                    <Typography variant="body2" sx={{ mt: 0.5 }}>{task.currentStage.name}</Typography>
                  </>)}
                  {task.statusDescription && (<>
                    <Divider sx={{ my: 2 }} />
                    <Typography variant="caption" color="text.secondary">Status description</Typography>
                    <Typography variant="body2" sx={{ mt: 0.5 }}>{task.statusDescription}</Typography>
                  </>)}
                  {task.completedDatetime && (<>
                    <Divider sx={{ my: 2 }} />
                    <Typography variant="caption" color="text.secondary">Completed at</Typography>
                    <Typography variant="body2" sx={{ mt: 0.5 }}>{formatDateTime(task.completedDatetime)}</Typography>
                  </>)}
                  {task.documentId && (<>
                    <Divider sx={{ my: 2 }} />
                    <Typography variant="caption" color="text.secondary">Linked document</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                      <Typography variant="body2">
                        {documents.find(d => d.id === task.documentId)?.name ?? task.documentId}
                      </Typography>
                      <Button
                        size="small"
                        variant="outlined"
                        id="task-info-doc-anchor"
                        onMouseDown={e => e.stopPropagation()}
                        onClick={(e) => {
                          const doc = documents.find(d => d.id === task.documentId);
                          if (doc) {
                            const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
                            setSelectionPopup({ doc, x: rect.left + rect.width / 2, y: rect.bottom + 8 });
                            setSelectionPopupExpanded(false);
                          } else {
                            navigate(`/documents/${task.documentId}`);
                          }
                        }}
                      >
                        VIEW
                      </Button>
                    </Box>
                  </>)}
                </Box>
              )}
            </Paper>

            {/* ── Subtasks (scenario) ── */}
            {isScenarioTask(task) && (
              <Paper elevation={2} sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 3, py: 2 }}>
                  <AccountTreeIcon color="primary" fontSize="small" />
                  <Typography variant="subtitle1" fontWeight="bold">Scenario progress</Typography>
                  {subtasksLoading && <CircularProgress size={16} sx={{ ml: 1 }} />}
                </Box>
                <Divider />
                <Box sx={{ px: 3, py: 2 }}>
                  {subtasks.length === 0 && !subtasksLoading && (
                    <Typography variant="body2" color="text.secondary">No subtasks yet</Typography>
                  )}
                  {subtasks.length > 0 && (
                    <>
                      <Stepper
                        activeStep={subtasks.findIndex(s => s.status !== 'COMPLETED' && s.status !== 'FAILED')}
                        alternativeLabel
                        sx={{ mb: 2 }}
                      >
                        {subtasks
                          .sort((a, b) => (a.scenarioStep ?? 0) - (b.scenarioStep ?? 0))
                          .map((sub) => {
                            const subCfg = statusConfig[sub.status] ?? { label: sub.status, color: 'default' as const, icon: <ScheduleIcon fontSize="small" /> };
                            const isError = sub.status === 'FAILED';
                            return (
                              <Step key={sub.id} completed={sub.status === 'COMPLETED'}>
                                <StepLabel
                                  error={isError}
                                  sx={{ cursor: 'pointer' }}
                                  onClick={() => navigate(`/tasks/${sub.id}`)}
                                >
                                  <Typography variant="caption" fontWeight="bold">
                                    {typeLabels[sub.type] ?? sub.type}
                                  </Typography>
                                  <Chip
                                    icon={subCfg.icon}
                                    label={subCfg.label}
                                    color={subCfg.color}
                                    size="small"
                                    sx={{ mt: 0.5, fontSize: '0.65rem', height: 20 }}
                                  />
                                </StepLabel>
                              </Step>
                            );
                          })}
                      </Stepper>
                    </>
                  )}
                </Box>
              </Paper>
            )}

            {/* ── Result ── */}
            {task.result && (
              <Paper elevation={2} sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 3, py: 2, cursor: 'pointer', userSelect: 'none' }}
                  onClick={() => setResultExpanded(v => !v)}>
                  <Typography variant="subtitle1" fontWeight="bold" color="primary">Result</Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }} onClick={e => e.stopPropagation()}>
                    <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'}>
                      <IconButton size="small" color={copied ? 'success' : 'default'} onClick={() => {
                        navigator.clipboard.writeText(task.result ?? '').then(() => {
                          setCopied(true);
                          setTimeout(() => setCopied(false), 2000);
                        });
                      }}>
                        <CopyIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <IconButton size="small" onClick={() => setResultExpanded(v => !v)}>
                      {resultExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </IconButton>
                  </Box>
                </Box>
                {resultExpanded && (
                  <Box sx={{ px: 3, pb: 3 }}>
                    <Divider sx={{ mb: 2 }} />
                    {parseMessageWithCode(task.result).map((part, idx) =>
                      part.type === 'code' ? (
                        <CodeBlock
                          key={idx}
                          code={part.content}
                          language={part.language}
                          taskId={taskId}
                          onExecResult={r => setLastExecResult(r ? { stdout: r.stdout, stderr: r.stderr } : null)}
                        />
                      ) : (
                        <Box key={idx} sx={mdStyles}>
                          <ReactMarkdown>{part.content}</ReactMarkdown>
                        </Box>
                      )
                    )}
                  </Box>
                )}
              </Paper>
            )}

            {/* ── Stage history ── */}
            {task.stageHistory && task.stageHistory.length > 0 && (
              <Paper elevation={2} sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 3, py: 2, cursor: 'pointer', userSelect: 'none' }}
                  onClick={() => setHistoryExpanded(v => !v)}>
                  <Typography variant="subtitle1" fontWeight="bold">Stage history</Typography>
                  <IconButton size="small">{historyExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}</IconButton>
                </Box>
                {historyExpanded && (
                  <Box sx={{ px: 3, pb: 3 }}>
                    <Divider sx={{ mb: 3 }} />
                    {task.status === 'RUNNING' || task.status === 'WAITING' || task.status === 'CREATED' ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                        {task.currentStage && (<>
                          <Tooltip title="Click for details">
                            <Box onClick={() => setSelectedStage(task.currentStage!)} sx={{
                              ...stageCircleBase, minHeight: 56,
                              bgcolor: '#1565c0',
                              color: 'white', py: 1, boxShadow: 3,
                              cursor: 'pointer',
                              transition: 'transform 0.15s, box-shadow 0.15s',
                              '&:hover': { transform: 'scale(1.04)', boxShadow: 4, bgcolor: '#0d47a1' },
                            }}>
                              <CircularProgress size={12} sx={{ color: 'white', mb: 0.5 }} />
                              <Typography variant="caption" fontWeight="bold" sx={{ fontSize: '0.75rem', lineHeight: 1.3 }}>
                                {task.currentStage.name}
                              </Typography>
                            </Box>
                          </Tooltip>
                          {task.stageHistory.length > 0 && <ArrowUpIcon sx={{ color: 'primary.light', fontSize: 24, my: 0.5 }} />}
                        </>)}
                        {[...task.stageHistory].reverse().map((stage: TaskStage, idx: number) => (
                          <Box key={stage.id ?? idx} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%' }}>
                            <Tooltip title="Click for details">
                              <Box onClick={() => setSelectedStage(stage)} sx={{
                                ...stageCircleBase, minHeight: 56, bgcolor: stageColor(stage).bg, color: 'white', py: 1, cursor: 'pointer',
                                transition: 'transform 0.15s, box-shadow 0.15s',
                                '&:hover': { transform: 'scale(1.04)', boxShadow: 3, bgcolor: stageColor(stage).hover },
                              }}>
                                <Typography variant="caption" fontWeight="bold" sx={{ lineHeight: 1.3, fontSize: '0.75rem' }}>{stage.name}</Typography>
                                <Typography variant="caption" sx={{ mt: 0.5, opacity: 0.8, fontSize: '0.7rem' }}>{formatDuration(stage.duration)}</Typography>
                              </Box>
                            </Tooltip>
                            {idx < task.stageHistory.length - 1 && <ArrowUpIcon sx={{ color: 'grey.400', fontSize: 24, my: 0.5 }} />}
                          </Box>
                        ))}
                      </Box>
                    ) : task.status === 'FAILED' ? (
                      /* FAILED: show currentStage in red + stageHistory */
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                        {task.currentStage && (<>
                          <Tooltip title="Click for details">
                            <Box onClick={() => setSelectedStage(task.currentStage!)} sx={{
                              ...stageCircleBase, minHeight: 56,
                              bgcolor: '#c62828', color: 'white', py: 1, boxShadow: 3,
                              cursor: 'pointer',
                              transition: 'transform 0.15s, box-shadow 0.15s',
                              '&:hover': { transform: 'scale(1.04)', boxShadow: 4, bgcolor: '#7f0000' },
                            }}>
                              <Typography variant="caption" fontWeight="bold" sx={{ fontSize: '0.75rem', lineHeight: 1.3 }}>
                                {task.currentStage.name}
                              </Typography>
                              <Typography variant="caption" sx={{ fontSize: '0.65rem', opacity: 0.85, mt: 0.25 }}>
                                failed here
                              </Typography>
                            </Box>
                          </Tooltip>
                          {task.stageHistory.length > 0 && <ArrowUpIcon sx={{ color: 'error.light', fontSize: 24, my: 0.5 }} />}
                        </>)}
                        {[...task.stageHistory].reverse().map((stage: TaskStage, idx: number) => (
                          <Box key={stage.id ?? idx} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%' }}>
                            <Tooltip title="Click for details">
                              <Box onClick={() => setSelectedStage(stage)} sx={{
                                ...stageCircleBase, minHeight: 56, bgcolor: stageColor(stage).bg, color: 'white', py: 1, cursor: 'pointer',
                                transition: 'transform 0.15s, box-shadow 0.15s',
                                '&:hover': { transform: 'scale(1.04)', boxShadow: 3, bgcolor: stageColor(stage).hover },
                              }}>
                                <Typography variant="caption" fontWeight="bold" sx={{ lineHeight: 1.3, fontSize: '0.75rem' }}>{stage.name}</Typography>
                                <Typography variant="caption" sx={{ mt: 0.5, opacity: 0.8, fontSize: '0.7rem' }}>{formatDuration(stage.duration)}</Typography>
                              </Box>
                            </Tooltip>
                            {idx < task.stageHistory.length - 1 && <ArrowUpIcon sx={{ color: 'grey.400', fontSize: 24, my: 0.5 }} />}
                          </Box>
                        ))}
                      </Box>
                    ) : (
                      /* COMPLETED / WAITING_USER_INPUT / WAITING_USER_APPROVE: full history */
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                        {[...task.stageHistory].reverse().map((stage: TaskStage, idx: number) => (
                          <Box key={stage.id ?? idx} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%' }}>
                            <Tooltip title="Click for details">
                              <Box onClick={() => setSelectedStage(stage)} sx={{
                                ...stageCircleBase, minHeight: 72, bgcolor: stageColor(stage).bg, color: 'white', py: 1.5, cursor: 'pointer', boxShadow: 2,
                                transition: 'transform 0.15s, box-shadow 0.15s',
                                '&:hover': { transform: 'scale(1.04)', boxShadow: 4, bgcolor: stageColor(stage).hover },
                              }}>
                                <Typography variant="caption" fontWeight="bold" sx={{ lineHeight: 1.3, fontSize: '0.75rem' }}>{stage.name}</Typography>
                                <Typography variant="caption" sx={{ mt: 0.5, opacity: 0.85, fontSize: '0.7rem' }}>{formatDuration(stage.duration)}</Typography>
                              </Box>
                            </Tooltip>
                            {idx < task.stageHistory.length - 1 && <ArrowUpIcon sx={{ color: 'primary.light', fontSize: 28, my: 0.5 }} />}
                          </Box>
                        ))}
                      </Box>
                    )}
                  </Box>
                )}
              </Paper>
            )}

            {/* Polling indicator */}
            {!isTerminal(task.status) && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 2 }}>
                <CircularProgress size={14} />
                <Typography variant="caption" color="text.secondary">
                  Auto-updating every {POLL_INTERVAL / 1000}s...
                </Typography>
              </Box>
            )}
          </>
        )}
      </Box>

      {/* Stage detail dialog */}
      <Dialog open={!!selectedStage} onClose={() => setSelectedStage(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Stage details</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, pt: 2 }}>
          {selectedStage && (<>
            <Box>
              <Typography variant="caption" color="text.secondary">Name</Typography>
              <Typography variant="body1" fontWeight="bold">{selectedStage.name}</Typography>
            </Box>
            {selectedStage.description && (
              <Box>
                <Typography variant="caption" color="text.secondary">Description</Typography>
                <Box sx={{ mt: 0.5 }}>
                  {parseMessageWithCode(selectedStage.description).map((part, idx) =>
                    part.type === 'code' ? (
                      <CodeBlock key={idx} code={part.content} language={part.language} taskId={taskId} />
                    ) : (
                      <Box key={idx} sx={{
                        '& p': { mt: 0, mb: 0.5 },
                        '& code': { bgcolor: 'grey.100', px: 0.5, borderRadius: 0.5, fontFamily: 'monospace', fontSize: '0.82rem' },
                        '& pre': { bgcolor: 'grey.100', p: 1, borderRadius: 1, overflow: 'auto', fontFamily: 'monospace', fontSize: '0.82rem' },
                        '& pre code': { bgcolor: 'transparent', p: 0 },
                        '& ul, & ol': { pl: 2.5, mb: 0.5 },
                        fontSize: '0.875rem',
                      }}>
                        <ReactMarkdown>{part.content}</ReactMarkdown>
                      </Box>
                    )
                  )}
                </Box>
              </Box>
            )}
            {selectedStage.result && (
              <Box>
                <Typography variant="caption" color="text.secondary">Result</Typography>
                <Box sx={{ mt: 0.5 }}>
                  {parseMessageWithCode(selectedStage.result).map((part, idx) =>
                    part.type === 'code' ? (
                      <CodeBlock key={idx} code={part.content} language={part.language} taskId={taskId} />
                    ) : (
                      <Box key={idx} sx={{
                        '& p': { mt: 0, mb: 0.5 },
                        '& code': { bgcolor: 'grey.100', px: 0.5, borderRadius: 0.5, fontFamily: 'monospace', fontSize: '0.82rem' },
                        '& pre': { bgcolor: 'grey.100', p: 1, borderRadius: 1, overflow: 'auto', fontFamily: 'monospace', fontSize: '0.82rem' },
                        '& pre code': { bgcolor: 'transparent', p: 0 },
                        '& ul, & ol': { pl: 2.5, mb: 0.5 },
                        fontSize: '0.875rem',
                      }}>
                        <ReactMarkdown>{part.content}</ReactMarkdown>
                      </Box>
                    )
                  )}
                </Box>
              </Box>
            )}
            <Divider />
            <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
              <Box>
                <Typography variant="caption" color="text.secondary">Start</Typography>
                <Typography variant="body2">{formatDateTime(selectedStage.instantStart)}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">End</Typography>
                <Typography variant="body2">{formatDateTime(selectedStage.instantEnd)}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">Duration</Typography>
                <Typography variant="body2" fontWeight="bold">{formatDuration(selectedStage.duration)}</Typography>
              </Box>
            </Box>
          </>)}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedStage(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog open={deleteConfirm} onClose={() => setDeleteConfirm(false)}>
        <DialogTitle>Delete task?</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete task <strong>"{task?.description}"</strong>? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(false)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>

      {/* Chat role selection dialog */}
      <Dialog
        open={chatRoleDialogOpen}
        onClose={() => setChatRoleDialogOpen(false)}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { minHeight: '300px' } }}
      >
        <DialogTitle sx={{ fontSize: '1.25rem', py: 2.5 }}>Select Role</DialogTitle>
        <DialogContent sx={{ pt: 3, pb: 4, minHeight: '180px' }}>
          <FormControl fullWidth sx={{ mb: 3, mt: 1 }}>
            <InputLabel id="chat-role-select-label" sx={{ fontSize: '1rem' }}>Role</InputLabel>
            <Select
              labelId="chat-role-select-label"
              value={tempChatRole}
              label="Role"
              onChange={e => setTempChatRole(e.target.value)}
              sx={{ minHeight: '56px', '& .MuiSelect-select': { py: 2, fontSize: '1rem' } }}
            >
              <MenuItem value="analytic" sx={{ py: 2, fontSize: '1rem' }}>Analyst (analytic)</MenuItem>
              <MenuItem value="programmer" sx={{ py: 2, fontSize: '1rem' }}>Programmer (programmer)</MenuItem>
            </Select>
          </FormControl>
          <Typography variant="body2" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Current role: <strong>{chatRole === 'analytic' ? 'Analyst' : 'Programmer'}</strong>
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3, pt: 2 }}>
          <Button onClick={() => setChatRoleDialogOpen(false)} sx={{ py: 1 }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => { setChatRole(tempChatRole); setChatRoleDialogOpen(false); }}
            sx={{ py: 1 }}
          >
            Apply
          </Button>
        </DialogActions>
      </Dialog>

      {/* Chat model selection dialog */}
      <Dialog
        open={chatModelDialogOpen}
        onClose={() => setChatModelDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ fontSize: '1.25rem', py: 2.5 }}>Select Model</DialogTitle>
        <DialogContent sx={{ pt: 3, pb: 4, minHeight: '180px' }}>
          <FormControl fullWidth sx={{ mb: 3, mt: 1 }}>
            <InputLabel id="chat-model-select-label" sx={{ fontSize: '1rem' }}>Model</InputLabel>
            <Select
              labelId="chat-model-select-label"
              value={tempChatModel}
              label="Model"
              onChange={e => setTempChatModel(e.target.value)}
              sx={{ minHeight: '56px', '& .MuiSelect-select': { py: 2, fontSize: '1rem' } }}
            >
              {availableModels.map((m) => (
                <MenuItem key={m.id} value={m.id} sx={{ py: 2, fontSize: '1rem' }}>
                  {m.name}{m.id === defaultModel ? ' (default)' : ''}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Typography variant="body2" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Current model: <strong>{availableModels.find(m => m.id === chatModel)?.name ?? chatModel ?? 'Default'}</strong>
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3, pt: 2 }}>
          <Button onClick={() => setChatModelDialogOpen(false)} sx={{ py: 1 }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => { setChatModel(tempChatModel); setChatModelDialogOpen(false); }}
            sx={{ py: 1 }}
          >
            Apply
          </Button>
        </DialogActions>
      </Dialog>

      {/* Document selection popup */}
      {selectionPopup && (
        <Paper
          ref={popupRef}
          elevation={6}
          sx={{
            position: 'fixed',
            top: selectionPopup.y,
            left: selectionPopup.x,
            transform: 'translateX(-50%)',
            zIndex: 9999,
            borderRadius: 2,
            boxShadow: 6,
            minWidth: 260,
            maxWidth: 380,
            overflow: 'hidden',
          }}
        >
          {/* Header row */}
          <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
              <Typography variant="caption" color="text.secondary" display="block">Document found</Typography>
              <Typography variant="body2" fontWeight="bold" noWrap>
                {selectionPopup.doc.name ?? selectionPopup.doc.id}
              </Typography>
            </Box>
            <Button
              size="small"
              variant="contained"
              onClick={() => {
                const docId = selectionPopup.doc.id;
                closePopup();
                window.getSelection()?.removeAllRanges();
                navigate(`/documents/${docId}`);
              }}
            >
              Open
            </Button>
            <Tooltip title={selectionPopupExpanded ? 'Collapse' : 'Show details'}>
              <IconButton size="small" onClick={handlePopupExpand}>
                {selectionPopupExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Close">
              <IconButton size="small" onClick={closePopup}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>

          {/* Expanded details */}
          {selectionPopupExpanded && (
            <>
              <Divider />
              <Box sx={{ px: 2, py: 1.5, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                <Box>
                  <Typography variant="caption" color="text.secondary">Name</Typography>
                  <Typography variant="body2" fontWeight="bold">{selectionPopup.doc.name ?? '—'}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">ID</Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' }}>
                    {selectionPopup.doc.id}
                  </Typography>
                </Box>
                {selectionPopup.doc.documentSummary && (
                  <Box>
                    <Typography variant="caption" color="text.secondary">Summary</Typography>
                    <Typography variant="body2" sx={{ mt: 0.25, maxHeight: 120, overflowY: 'auto', whiteSpace: 'pre-line', fontSize: '0.8rem' }}>
                      {selectionPopup.doc.documentSummary}
                    </Typography>
                  </Box>
                )}
                {selectionPopup.doc.methodSummary && (
                  <Box>
                    <Typography variant="caption" color="text.secondary">Method summary</Typography>
                    <Typography variant="body2" sx={{ mt: 0.25, maxHeight: 120, overflowY: 'auto', whiteSpace: 'pre-line', fontSize: '0.8rem' }}>
                      {selectionPopup.doc.methodSummary}
                    </Typography>
                  </Box>
                )}
              </Box>
            </>
          )}
        </Paper>
      )}

      {/* Retry dialog */}
      <Dialog open={retryDialogOpen} onClose={() => !retrying && setRetryDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Retry task</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Please explain what you didn't like about the previous result so the AI can improve its response.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            maxRows={8}
            value={retryMessage}
            onChange={e => setRetryMessage(e.target.value)}
            placeholder="Describe your feedback..."
            disabled={retrying}
            onKeyDown={e => {
              if (e.key === 'Enter' && e.ctrlKey) {
                e.preventDefault();
                handleRetryConfirm();
              }
            }}
          />
          {lastExecResult && (lastExecResult.stdout || lastExecResult.stderr) && (
            <Box sx={{ bgcolor: 'grey.50', borderRadius: 1, p: 2, border: '1px solid', borderColor: 'grey.300' }}>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                ⚙ Code execution output will be attached automatically:
              </Typography>
              {lastExecResult.stdout && (
                <Box sx={{ mb: 1 }}>
                  <Typography variant="caption" fontWeight="bold" color="success.dark">stdout:</Typography>
                  <Box component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.8rem', bgcolor: '#e8f5e9', p: 1, borderRadius: 1, maxHeight: 120, overflowY: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word', mt: 0.5, m: 0 }}>
                    {lastExecResult.stdout}
                  </Box>
                </Box>
              )}
              {lastExecResult.stderr && (
                <Box>
                  <Typography variant="caption" fontWeight="bold" color="error.dark">stderr:</Typography>
                  <Box component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.8rem', bgcolor: '#ffebee', p: 1, borderRadius: 1, maxHeight: 120, overflowY: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word', mt: 0.5, m: 0 }}>
                    {lastExecResult.stderr}
                  </Box>
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setRetryDialogOpen(false)} disabled={retrying}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleRetryConfirm}
            disabled={retrying}
            startIcon={retrying ? <CircularProgress size={16} /> : <ReloadIcon />}
          >
            {retrying ? 'Retrying...' : 'Retry'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Create from base dialog */}
      <Dialog open={fromBaseDialogOpen} onClose={() => !creatingFromBase && setFromBaseDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create task based on this one</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <Typography variant="body2" color="text.secondary">
            What would you like to add or change for the new task? The AI will use the current task as a base and apply your instructions.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            maxRows={8}
            value={fromBaseMessage}
            onChange={e => setFromBaseMessage(e.target.value)}
            placeholder="Describe what to add or update..."
            disabled={creatingFromBase}
            onKeyDown={e => {
              if (e.key === 'Enter' && e.ctrlKey) {
                e.preventDefault();
                handleCreateFromBase();
              }
            }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setFromBaseDialogOpen(false)} disabled={creatingFromBase}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateFromBase}
            disabled={creatingFromBase}
            startIcon={creatingFromBase ? <CircularProgress size={16} /> : <FromBaseIcon />}
          >
            {creatingFromBase ? 'Creating...' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* AI question — waiting for user input dialog */}
      <Dialog
        open={userInputDialogOpen}
        onClose={() => !submittingUserInput && setUserInputDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <QuestionAnswerIcon color="warning" />
          AI is asking you a question
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <Paper variant="outlined" sx={{ p: 2, bgcolor: 'warning.50', borderColor: 'warning.light' }}>
            <Typography variant="body1" sx={{ whiteSpace: 'pre-line' }}>
              {task?.currentStage?.aiQuestion ?? ''}
            </Typography>
          </Paper>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            maxRows={8}
            value={userInputAnswer}
            onChange={e => setUserInputAnswer(e.target.value)}
            placeholder="Type your answer..."
            disabled={submittingUserInput}
            onKeyDown={e => {
              if (e.key === 'Enter' && e.ctrlKey) {
                e.preventDefault();
                handleSubmitUserInput();
              }
            }}
          />
          <Typography variant="caption" color="text.secondary">
            Press Ctrl+Enter to send
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setUserInputDialogOpen(false)} disabled={submittingUserInput}>
            Dismiss
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmitUserInput}
            disabled={!userInputAnswer.trim() || submittingUserInput}
            startIcon={submittingUserInput ? <CircularProgress size={16} /> : <SendIcon />}
          >
            {submittingUserInput ? 'Sending...' : 'Send answer'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Approve/Disapprove dialog */}
      <Dialog
        open={approveDialogOpen}
        onClose={() => {
          if (submittingApproval) return;
          setApproveDialogOpen(false);
          setShowDisapproveField(false);
          setDisapproveReason('');
        }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <ThumbUpIcon color="warning" />
          Approval required
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <Paper variant="outlined" sx={{ p: 2, bgcolor: 'warning.50', borderColor: 'warning.light' }}>
            <Typography variant="body1" sx={{ whiteSpace: 'pre-line' }}>
              {task?.currentStage?.approveDescription || 'AI is requesting your approval to proceed.'}
            </Typography>
          </Paper>
          {showDisapproveField && (
            <TextField
              autoFocus
              fullWidth
              multiline
              minRows={2}
              maxRows={6}
              value={disapproveReason}
              onChange={e => setDisapproveReason(e.target.value)}
              placeholder="Describe why you are disapproving (optional)..."
              disabled={submittingApproval}
              label="Reason for disapproval (optional)"
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button
            onClick={() => {
              setApproveDialogOpen(false);
              setShowDisapproveField(false);
              setDisapproveReason('');
            }}
            disabled={submittingApproval}
          >
            Dismiss
          </Button>

          {!showDisapproveField ? (
            <>
              {/* Initial state: two buttons */}
              <Button
                variant="outlined"
                color="error"
                onClick={() => setShowDisapproveField(true)}
                disabled={submittingApproval}
                startIcon={<ThumbDownIcon />}
              >
                Disapprove
              </Button>
              <Button
                variant="contained"
                color="success"
                onClick={handleApprove}
                disabled={submittingApproval}
                startIcon={submittingApproval ? <CircularProgress size={16} /> : <ThumbUpIcon />}
              >
                {submittingApproval ? 'Sending...' : 'Approve'}
              </Button>
            </>
          ) : (
            <>
              {/* Disapprove field shown: Back + Confirm disapprove */}
              <Button
                variant="outlined"
                onClick={() => { setShowDisapproveField(false); setDisapproveReason(''); }}
                disabled={submittingApproval}
              >
                Back
              </Button>
              <Button
                variant="contained"
                color="error"
                onClick={handleDisapprove}
                disabled={submittingApproval}
                startIcon={submittingApproval ? <CircularProgress size={16} /> : <ThumbDownIcon />}
              >
                {submittingApproval ? 'Sending...' : 'Confirm disapproval'}
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

