import { useState, useRef, useEffect } from 'react';
import {
  Box,
  Paper,
  TextField,
  IconButton,
  Typography,
  Alert,
  CircularProgress,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Tooltip,
} from '@mui/material';
import {
  Send as SendIcon,
  ArrowBack as ArrowBackIcon,
  Description as DescriptionIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Add as AddIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { documentsApi } from '../api/documentsApi';
import { tasksApi } from '../api/tasksApi';
import type { SwaggerDocument, ChatMessage, TaskType } from '../models/types';
import { ChatMessageItem } from '../components/ChatMessageItem';
import { SwaggerUIViewer } from '../components/SwaggerUIViewer';

interface ChatInterfaceProps {
  document: SwaggerDocument;
  onBack: () => void;
}

export const ChatInterface: React.FC<ChatInterfaceProps> = ({ document, onBack }) => {
  const navigate = useNavigate();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [role, setRole] = useState<string>('analytic');
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [tempRole, setTempRole] = useState<string>('analytic');
  const [summaryExpanded, setSummaryExpanded] = useState(true);
  const [showSwaggerUI, setShowSwaggerUI] = useState(false);

  // Task creation dialog
  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const [taskType, setTaskType] = useState<TaskType>('ANALYZE');
  const [taskDescription, setTaskDescription] = useState('');
  const [taskCreating, setTaskCreating] = useState(false);
  const [taskError, setTaskError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Автопрокрутка к последнему сообщению
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleRoleDialogOpen = () => {
    setTempRole(role);
    setRoleDialogOpen(true);
  };

  const handleRoleDialogClose = () => {
    setRoleDialogOpen(false);
  };

  const handleRoleConfirm = () => {
    setRole(tempRole);
    setRoleDialogOpen(false);
  };

  const handleCreateTask = async () => {
    if (!taskDescription.trim()) return;
    setTaskCreating(true);
    setTaskError(null);
    try {
      const task = await tasksApi.createTask(document.id, {
        type: taskType,
        description: taskDescription.trim(),
      });
      setTaskDialogOpen(false);
      setTaskDescription('');
      setTaskType('ANALYZE');
      navigate(`/tasks/${task.id}`);
    } catch (err: any) {
      setTaskError(err.response?.data?.message || 'Error creating task');
    } finally {
      setTaskCreating(false);
    }
  };

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage: ChatMessage = {
      role: 'user',
      content: input.trim(),
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);
    setError(null);

    try {
      const response = await documentsApi.sendChatMessage(document.id, {
        question: userMessage.content,
        role: role,
      });

      console.log('API Response:', response);

      // Безопасное извлечение текста ответа из разных возможных полей
      const responseText = response.answer ||
                          (response as any).response ||
                          (response as any).text ||
                          (response as any).message ||
                          JSON.stringify(response);

      const assistantMessage: ChatMessage = {
        role: 'assistant',
        content: responseText,
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error sending message');

      // Удаляем сообщение пользователя при ошибке, чтобы можно было попробовать снова
      setMessages((prev) => prev.slice(0, -1));
      setInput(userMessage.content);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Заголовок */}
      <Paper elevation={2} sx={{ p: 2, mb: 2 }}>
        {/* Основная информация */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton onClick={onBack} size="small">
            <ArrowBackIcon />
          </IconButton>
          <DescriptionIcon color="primary" />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6">
              {document.name || document.summary || `Document ${document.id}`}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ID: {document.id}
            </Typography>
          </Box>
          {document.content && (
            <Button
              onClick={() => setShowSwaggerUI(!showSwaggerUI)}
              variant="contained"
              sx={{
                bgcolor: 'primary.main',
                color: 'white',
                px: 2,
                py: 1,
                fontSize: '0.875rem',
                fontWeight: 'bold',
                '&:hover': {
                  bgcolor: 'primary.dark',
                }
              }}
            >
              {showSwaggerUI ? 'Switch to Chat' : 'Switch to Swagger UI'}
            </Button>
          )}
          <Tooltip title="Create task">
            <IconButton
              color="primary"
              onClick={() => { setTaskDialogOpen(true); setTaskError(null); }}
              size="small"
              sx={{ border: '1px solid', borderColor: 'primary.main' }}
            >
              <AddIcon />
            </IconButton>
          </Tooltip>
          <Chip label="Active" color="success" size="small" />
        </Box>

        {/* Краткое содержание документа (сворачиваемое) */}
        {document.documentSummary && (
          <Box
            sx={{
              mt: 2,
              p: 1.5,
              bgcolor: 'grey.50',
              borderRadius: 1,
              borderLeft: 4,
              borderColor: 'primary.main',
              cursor: 'pointer',
              '&:hover': {
                bgcolor: 'grey.100',
              },
            }}
            onClick={() => setSummaryExpanded(!summaryExpanded)}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle2" color="primary" fontWeight="bold">
                Document Summary
              </Typography>
              <IconButton size="small" sx={{ p: 0 }}>
                {summaryExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Box>
            {summaryExpanded && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{
                  mt: 1,
                  whiteSpace: 'pre-line',
                  maxHeight: '200px',
                  overflow: 'auto',
                }}
              >
                {document.documentSummary}
              </Typography>
            )}
          </Box>
        )}
      </Paper>

      {/* Условный рендеринг: Swagger UI или Чат */}
      {showSwaggerUI && document.content ? (
        // Swagger UI View
        <Paper
          elevation={2}
          sx={{
            flexGrow: 1,
            mb: 2,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <SwaggerUIViewer
            content={document.content}
            documentName={document.name || document.summary}
          />
        </Paper>
      ) : (
        <>
          {/* Область сообщений */}
          <Paper
            elevation={2}
            sx={{
              flexGrow: 1,
              p: 2,
              mb: 2,
              overflow: 'auto',
              display: 'flex',
              flexDirection: 'column',
              minHeight: 0,
            }}
          >
            {messages.length === 0 ? (
              <Box
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  height: '100%',
                  color: 'text.secondary',
                }}
              >
                <Typography variant="h6" gutterBottom>
                  Ask a question about the document
                </Typography>
                <Typography variant="body2" textAlign="center">
                  You can ask about API structure, endpoints, parameters, and other details
                  of the OpenAPI document
                </Typography>
              </Box>
            ) : (
              <>
                {messages.map((message, index) => (
                  <ChatMessageItem key={index} message={message} />
                ))}
                {loading && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    <CircularProgress size={24} />
                    <Typography variant="body2" color="text.secondary">
                      Generating response...
                    </Typography>
                  </Box>
                )}
                <div ref={messagesEndRef} />
              </>
            )}
          </Paper>

          {/* Поле ввода */}
          <Paper elevation={2} sx={{ p: 2 }}>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

        <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-end' }}>
          <Tooltip title="Select role">
            <Button
              onClick={handleRoleDialogOpen}
              disabled={loading}
              sx={{
                minWidth: 'auto',
                bgcolor: 'primary.main',
                color: 'white',
                px: 1.5,
                py: 1.2,
                height: '40px',
                '&:hover': {
                  bgcolor: 'primary.dark',
                },
                '&:disabled': {
                  bgcolor: 'action.disabledBackground',
                  color: 'action.disabled',
                }
              }}
              variant="contained"
            >
              <Typography variant="caption" fontWeight="bold">
                ROLE
              </Typography>
            </Button>
          </Tooltip>
          <TextField
            fullWidth
            multiline
            maxRows={4}
            placeholder="Enter your question..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={loading}
          />
          <IconButton
            color="primary"
            onClick={handleSend}
            disabled={!input.trim() || loading}
          >
            <SendIcon />
          </IconButton>
        </Box>
      </Paper>
        </>
      )}

      {/* Task creation dialog */}
      <Dialog
        open={taskDialogOpen}
        onClose={() => setTaskDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create Task</DialogTitle>
        <DialogContent sx={{ pt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {taskError && <Alert severity="error">{taskError}</Alert>}
          <FormControl fullWidth sx={{ mt: 1 }}>
            <InputLabel>Task type</InputLabel>
            <Select
              value={taskType}
              label="Task type"
              onChange={(e) => setTaskType(e.target.value as TaskType)}
            >
              <MenuItem value="ANALYZE">Analysis (ANALYZE)</MenuItem>
              <MenuItem value="CODE">Code generation (CODE)</MenuItem>
              <MenuItem value="TEST">Test generation (TEST)</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="Description"
            multiline
            rows={5}
            fullWidth
            value={taskDescription}
            onChange={(e) => setTaskDescription(e.target.value)}
            placeholder="Describe what you want the AI to do..."
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setTaskDialogOpen(false)} disabled={taskCreating}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateTask}
            disabled={!taskDescription.trim() || taskCreating}
            startIcon={taskCreating ? <CircularProgress size={16} /> : undefined}
          >
            {taskCreating ? 'Creating...' : 'Create task'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Role selection dialog */}
      <Dialog
        open={roleDialogOpen}
        onClose={handleRoleDialogClose}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            minHeight: '300px',
          }
        }}
      >
          <DialogTitle sx={{ fontSize: '1.25rem', py: 2.5 }}>Select Role</DialogTitle>
          <DialogContent sx={{ pt: 6, pb: 4, minHeight: '180px' }}>
            <FormControl fullWidth sx={{ mb: 3, mt: 1 }}>
              <InputLabel id="role-select-label" sx={{ fontSize: '1rem' }}>Role</InputLabel>
              <Select
                labelId="role-select-label"
                value={tempRole}
                label="Role"
                onChange={(e) => setTempRole(e.target.value)}
                sx={{
                  minHeight: '56px',
                  '& .MuiSelect-select': {
                    py: 2,
                    fontSize: '1rem',
                  }
                }}
              >
                <MenuItem value="analytic" sx={{ py: 2, fontSize: '1rem' }}>
                  Analyst (analytic)
                </MenuItem>
                <MenuItem value="programmer" sx={{ py: 2, fontSize: '1rem' }}>
                  Programmer (programmer)
                </MenuItem>
              </Select>
            </FormControl>
            <Typography variant="body2" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
              Current role: <strong>{role === 'analytic' ? 'Analyst' : 'Programmer'}</strong>
            </Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 3, pt: 2 }}>
            <Button onClick={handleRoleDialogClose} sx={{ py: 1 }}>Cancel</Button>
            <Button onClick={handleRoleConfirm} variant="contained" sx={{ py: 1 }}>
              Apply
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};




