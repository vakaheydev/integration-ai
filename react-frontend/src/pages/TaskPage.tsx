import { useState, useEffect, useRef } from 'react';
import {
  Box, Paper, Typography, IconButton, Chip, CircularProgress,
  Alert, Divider, Tooltip, Button, LinearProgress,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon, Refresh as RefreshIcon, Assignment as AssignmentIcon,
  CheckCircle as CheckCircleIcon, Error as ErrorIcon, Schedule as ScheduleIcon,
  PlayArrow as PlayArrowIcon, HourglassEmpty as HourglassIcon,
  Replay as ReloadIcon, Delete as DeleteIcon, ContentCopy as CopyIcon,
  ArrowUpward as ArrowUpIcon, ExpandMore as ExpandMoreIcon, ExpandLess as ExpandLessIcon,
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { tasksApi } from '../api/tasksApi';
import type { Task, TaskStage } from '../models/types';

const POLL_INTERVAL = 1000;

const statusConfig: Record<string, { label: string; color: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'; icon: React.ReactElement }> = {
  CREATED:   { label: 'Created',   color: 'default',  icon: <ScheduleIcon fontSize="small" /> },
  RUNNING:   { label: 'Running',   color: 'primary',  icon: <PlayArrowIcon fontSize="small" /> },
  WAITING:   { label: 'Waiting',   color: 'warning',  icon: <HourglassIcon fontSize="small" /> },
  COMPLETED: { label: 'Completed', color: 'success',  icon: <CheckCircleIcon fontSize="small" /> },
  FAILED:    { label: 'Failed',    color: 'error',    icon: <ErrorIcon fontSize="small" /> },
};

const typeLabels: Record<string, string> = {
  ANALYZE: 'Analysis', CODE: 'Code generation', TEST: 'Test generation',
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
  CREATED:   { bg: '#1565c0', hover: '#0d47a1' },
  RUNNING:   { bg: '#1565c0', hover: '#0d47a1' },
};

function stageColor(stage: TaskStage) {
  return stageColorMap[stage.status] ?? stageColorMap.CREATED;
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

  const isTerminal = (status?: string) => status === 'COMPLETED' || status === 'FAILED';

  const fetchTask = async (silent = false) => {
    if (!taskId) return;
    if (!silent) setLoading(true);
    try {
      const t = await tasksApi.getTask(taskId);
      setTask(t);
      setError(null);
      if (isTerminal(t.status)) { clearInterval(intervalRef.current!); intervalRef.current = null; }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading task');
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    fetchTask();
    intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [taskId]);

  const handleReload = async () => {
    if (!task) return;
    try {
      const updated = await tasksApi.reloadTask(task.id);
      setTask(updated);
      if (!isTerminal(updated.status) && !intervalRef.current)
        intervalRef.current = setInterval(() => fetchTask(true), POLL_INTERVAL);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error reloading task');
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

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50', p: 3 }}>
      <Box sx={{ maxWidth: 860, mx: 'auto' }}>

        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
          <IconButton onClick={() => navigate(-1)}><ArrowBackIcon /></IconButton>
          <AssignmentIcon color="primary" />
          <Typography variant="h5" fontWeight="bold" sx={{ flexGrow: 1 }}>Task</Typography>
          {task && !isTerminal(task.status) && <CircularProgress size={20} sx={{ mr: 1 }} />}
          <Tooltip title="Refresh">
            <IconButton onClick={() => fetchTask()} size="small"><RefreshIcon /></IconButton>
          </Tooltip>
          {task && (
            <>
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

        {task && (
          <>
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
                </Box>
              )}
            </Paper>

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
                    <Box sx={mdStyles}>
                      <ReactMarkdown>{task.result}</ReactMarkdown>
                    </Box>
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
                    {!isTerminal(task.status) || task.status === 'FAILED' ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                        {task.currentStage && (<>
                          <Tooltip title="Click for details">
                            <Box onClick={() => setSelectedStage(task.currentStage!)} sx={{
                              ...stageCircleBase, minHeight: 56,
                              bgcolor: task.status === 'FAILED' ? '#c62828' : '#1565c0',
                              color: 'white', py: 1, boxShadow: 3,
                              cursor: 'pointer',
                              transition: 'transform 0.15s, box-shadow 0.15s',
                              '&:hover': { transform: 'scale(1.04)', boxShadow: 4, bgcolor: task.status === 'FAILED' ? '#7f0000' : '#0d47a1' },
                            }}>
                              {task.status !== 'FAILED' && <CircularProgress size={12} sx={{ color: 'white', mb: 0.5 }} />}
                              <Typography variant="caption" fontWeight="bold" sx={{ fontSize: '0.75rem', lineHeight: 1.3 }}>
                                {task.currentStage.name}
                              </Typography>
                              {task.status === 'FAILED' && (
                                <Typography variant="caption" sx={{ fontSize: '0.65rem', opacity: 0.85, mt: 0.25 }}>
                                  failed here
                                </Typography>
                              )}
                            </Box>
                          </Tooltip>
                          {task.stageHistory.length > 0 && <ArrowUpIcon sx={{ color: task.status === 'FAILED' ? 'error.light' : 'primary.light', fontSize: 24, my: 0.5 }} />}
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
                <Box sx={{
                  mt: 0.5,
                  '& p': { mt: 0, mb: 0.5 },
                  '& code': { bgcolor: 'grey.100', px: 0.5, borderRadius: 0.5, fontFamily: 'monospace', fontSize: '0.82rem' },
                  '& pre': { bgcolor: 'grey.100', p: 1, borderRadius: 1, overflow: 'auto', fontFamily: 'monospace', fontSize: '0.82rem' },
                  '& pre code': { bgcolor: 'transparent', p: 0 },
                  '& ul, & ol': { pl: 2.5, mb: 0.5 },
                  fontSize: '0.875rem',
                }}>
                  <ReactMarkdown>{selectedStage.description}</ReactMarkdown>
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
    </Box>
  );
};

