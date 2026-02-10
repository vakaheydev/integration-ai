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
} from '@mui/icons-material';
import { documentsApi } from '../api/documentsApi';
import type { SwaggerDocument, ChatMessage } from '../models/types';
import { ChatMessageItem } from '../components/ChatMessageItem';

interface ChatInterfaceProps {
  document: SwaggerDocument;
  onBack: () => void;
}

export const ChatInterface: React.FC<ChatInterfaceProps> = ({ document, onBack }) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [role, setRole] = useState<string>('analytic');
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [tempRole, setTempRole] = useState<string>('analytic');
  const [summaryExpanded, setSummaryExpanded] = useState(true);
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
      setError(err.response?.data?.message || 'Ошибка при отправке сообщения');

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
              {document.name || document.summary || `Документ ${document.id}`}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ID: {document.id}
            </Typography>
          </Box>
          <Chip label="Активен" color="success" size="small" />
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
                Краткое содержание документа
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
              Задайте вопрос по документу
            </Typography>
            <Typography variant="body2" textAlign="center">
              Вы можете спросить о структуре API, эндпоинтах, параметрах и других деталях
              OpenAPI документа
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
                  Генерация ответа...
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
          <Tooltip title="Выбрать роль">
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
            placeholder="Введите ваш вопрос..."
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

        {/* Диалог выбора роли */}
        <Dialog
          open={roleDialogOpen}
          onClose={handleRoleDialogClose}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>Выбор роли</DialogTitle>
          <DialogContent sx={{ pt: 3, pb: 2 }}>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel id="role-select-label">Роль</InputLabel>
              <Select
                labelId="role-select-label"
                value={tempRole}
                label="Роль"
                onChange={(e) => setTempRole(e.target.value)}
              >
                <MenuItem value="analytic">Аналитик (analytic)</MenuItem>
                <MenuItem value="programmer">Программист (programmer)</MenuItem>
              </Select>
            </FormControl>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              Текущая роль: <strong>{role === 'analytic' ? 'Аналитик' : 'Программист'}</strong>
            </Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={handleRoleDialogClose}>Отмена</Button>
            <Button onClick={handleRoleConfirm} variant="contained">
              Применить
            </Button>
          </DialogActions>
        </Dialog>
      </Paper>
    </Box>
  );
};




