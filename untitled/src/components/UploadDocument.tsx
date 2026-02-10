import { useState, useCallback } from 'react';
import {
  Box,
  Button,
  TextField,
  Typography,
  Paper,
  CircularProgress,
  Alert,
} from '@mui/material';
import { CloudUpload as CloudUploadIcon } from '@mui/icons-material';
import { documentsApi } from '../api/documentsApi';

interface UploadDocumentProps {
  onUploadSuccess: () => void;
}

export const UploadDocument: React.FC<UploadDocumentProps> = ({ onUploadSuccess }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [userId, setUserId] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      // Проверка формата файла
      if (file.type === 'application/json' || file.name.endsWith('.json')) {
        setSelectedFile(file);
        setError(null);
      } else {
        setError('Пожалуйста, выберите файл в формате JSON');
        setSelectedFile(null);
      }
    }
  };

  const handleUpload = useCallback(async () => {
    if (!selectedFile) {
      setError('Пожалуйста, выберите файл');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await documentsApi.uploadDocument(selectedFile, userId || undefined);
      setSuccess('Документ успешно загружен!');
      setSelectedFile(null);
      setUserId('');

      // Сброс input
      const fileInput = document.getElementById('file-upload') as HTMLInputElement;
      if (fileInput) {
        fileInput.value = '';
      }

      // Уведомляем родительский компонент
      setTimeout(() => {
        onUploadSuccess();
      }, 1000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка при загрузке документа');
    } finally {
      setLoading(false);
    }
  }, [selectedFile, userId, onUploadSuccess]);

  return (
    <Paper elevation={2} sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        Загрузка OpenAPI документа
      </Typography>

      <Box sx={{ mt: 2 }}>
        <TextField
          fullWidth
          label="User ID (опционально)"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          margin="normal"
          disabled={loading}
        />

        <Box sx={{ mt: 2, mb: 2 }}>
          <input
            accept="application/json"
            style={{ display: 'none' }}
            id="file-upload"
            type="file"
            onChange={handleFileChange}
            disabled={loading}
          />
          <label htmlFor="file-upload">
            <Button
              variant="outlined"
              component="span"
              fullWidth
              disabled={loading}
              startIcon={<CloudUploadIcon />}
            >
              Выбрать JSON файл
            </Button>
          </label>
        </Box>

        {selectedFile && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Выбран файл: {selectedFile.name}
          </Typography>
        )}

        <Button
          fullWidth
          variant="contained"
          onClick={handleUpload}
          disabled={!selectedFile || loading}
          startIcon={loading ? <CircularProgress size={20} /> : <CloudUploadIcon />}
        >
          {loading ? 'Загрузка...' : 'Загрузить документ'}
        </Button>

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}

        {success && (
          <Alert severity="success" sx={{ mt: 2 }}>
            {success}
          </Alert>
        )}
      </Box>
    </Paper>
  );
};

