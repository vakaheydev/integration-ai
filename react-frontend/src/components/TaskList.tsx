import { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Paper,
  List,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  Chip,
  CircularProgress,
  Alert,
  IconButton,
  Tooltip,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
} from '@mui/material';
import {
  Assignment as AssignmentIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Schedule as ScheduleIcon,
  PlayArrow as PlayArrowIcon,
  HourglassEmpty as HourglassIcon,
  ChevronRight as ChevronRightIcon,
  Replay as ReloadIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  DeleteSweep as DeleteAllIcon,
  QuestionAnswer as QuestionAnswerIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { tasksApi } from '../api/tasksApi';
import type { Task, SwaggerDocument } from '../models/types';
import { CreateTaskDialog } from './CreateTaskDialog';
import { DeleteAllDialog } from './DeleteAllDialog';

const statusConfig: Record<string, { label: string; color: 'default' | 'primary' | 'warning' | 'error' | 'success'; icon: React.ReactElement }> = {
  CREATED:   { label: 'Created',   color: 'default',  icon: <ScheduleIcon fontSize="small" /> },
  RUNNING:   { label: 'Running',   color: 'primary',  icon: <PlayArrowIcon fontSize="small" /> },
  WAITING:   { label: 'Waiting',   color: 'warning',  icon: <HourglassIcon fontSize="small" /> },
  WAITING_USER_INPUT: { label: 'Needs input', color: 'warning', icon: <QuestionAnswerIcon fontSize="small" /> },
  WAITING_USER_APPROVE: { label: 'Needs approval', color: 'warning', icon: <QuestionAnswerIcon fontSize="small" /> },
  WAITING_SUBTASK: { label: 'Subtask running', color: 'primary', icon: <HourglassIcon fontSize="small" /> },
  COMPLETED: { label: 'Completed', color: 'success',  icon: <CheckCircleIcon fontSize="small" /> },
  FAILED:    { label: 'Failed',    color: 'error',    icon: <ErrorIcon fontSize="small" /> },
};

const typeLabels: Record<string, string> = {
  ANALYZE: 'Analysis',
  CODE:    'Code',
  TEST:    'Tests',
  ANALYZE_CODE: 'Analysis + Code',
  ANALYZE_TEST: 'Analysis + Tests',
};

export const TaskList: React.FC<{ documents: SwaggerDocument[] }> = ({ documents }) => {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Task | null>(null);
  const [deleteAllOpen, setDeleteAllOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const navigate = useNavigate();

  const loadTasks = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await tasksApi.getAllTasks();
      setTasks(data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading tasks');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadTasks(); }, []);

  const handleReload = async (e: React.MouseEvent, task: Task) => {
    e.stopPropagation();
    setActionLoading(task.id);
    try {
      await tasksApi.reloadTask(task.id);
      await loadTasks();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error reloading task');
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;
    setActionLoading(deleteConfirm.id);
    setDeleteConfirm(null);
    try {
      await tasksApi.deleteTask(deleteConfirm.id);
      await loadTasks();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error deleting task');
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <Box>
      <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">Tasks</Typography>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          <Tooltip title="Create task">
            <IconButton onClick={() => setCreateOpen(true)} size="small" color="primary">
              <AddIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Delete all tasks">
            <span>
              <IconButton onClick={() => setDeleteAllOpen(true)} size="small" color="error" disabled={tasks.length === 0}>
                <DeleteAllIcon />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Refresh">
            <IconButton onClick={loadTasks} disabled={loading} size="small">
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress size={28} />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ m: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {!loading && !error && tasks.length === 0 && (
          <Box sx={{ p: 4, textAlign: 'center', color: 'text.secondary' }}>
            <AssignmentIcon sx={{ fontSize: 40, mb: 1, opacity: 0.4 }} />
            <Typography variant="body2">No tasks yet</Typography>
          </Box>
        )}

        {!loading && tasks.length > 0 && (
          <List disablePadding>
            {tasks.filter(t => !t.parentTaskId).map((task, idx) => {
              const cfg = statusConfig[task.status] ?? { label: task.status, color: 'default' as const, icon: <ScheduleIcon fontSize="small" /> };
              const busy = actionLoading === task.id;
              return (
                <Box key={task.id}>
                  {idx > 0 && <Divider />}
                  <ListItemButton onClick={() => navigate(`/tasks/${task.id}`)} sx={{ py: 1.5 }}>
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <AssignmentIcon color="primary" fontSize="small" />
                    </ListItemIcon>
                    <ListItemText
                      disableTypography
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                          <Typography variant="body2" fontWeight="bold" noWrap sx={{ maxWidth: 160 }}>
                            {task.description}
                          </Typography>
                          <Chip
                            label={typeLabels[task.type] ?? task.type}
                            size="small"
                            variant="outlined"
                            sx={{ fontSize: '0.65rem', height: 20 }}
                          />
                        </Box>
                      }
                      secondary={
                        <Chip
                          icon={cfg.icon}
                          label={cfg.label}
                          color={cfg.color}
                          size="small"
                          sx={{ mt: 0.5, fontSize: '0.7rem', height: 22 }}
                        />
                      }
                    />
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, ml: 1 }} onClick={e => e.stopPropagation()}>
                      <Tooltip title="Reload">
                        <span>
                          <IconButton size="small" disabled={busy} onClick={(e) => handleReload(e, task)}>
                            {busy ? <CircularProgress size={16} /> : <ReloadIcon fontSize="small" />}
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <span>
                          <IconButton size="small" disabled={busy} onClick={(e) => { e.stopPropagation(); setDeleteConfirm(task); }}>
                            <DeleteIcon fontSize="small" color="error" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </Box>
                    <ChevronRightIcon fontSize="small" sx={{ color: 'text.disabled', ml: 0.5 }} />
                  </ListItemButton>
                </Box>
              );
            })}
          </List>
        )}
      </Paper>

      {/* Delete confirmation dialog */}
      <Dialog open={!!deleteConfirm} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Delete task?</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete the task <strong>"{deleteConfirm?.description}"</strong>? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDeleteConfirm}>Delete</Button>
        </DialogActions>
      </Dialog>

      <CreateTaskDialog
        open={createOpen}
        onClose={() => { setCreateOpen(false); loadTasks(); }}
        documents={documents}
      />

      <DeleteAllDialog
        open={deleteAllOpen}
        onClose={() => setDeleteAllOpen(false)}
        onConfirm={async () => { await tasksApi.deleteAllTasks(); await loadTasks(); }}
        itemName="tasks"
      />
    </Box>
  );
};

