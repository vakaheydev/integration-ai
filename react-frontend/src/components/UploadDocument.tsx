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
  const [documentName, setDocumentName] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      // Проверка формата файла - поддерживаем JSON и YAML
      const isJson = file.type === 'application/json' || file.name.endsWith('.json');
      const isYaml = file.name.endsWith('.yml') || file.name.endsWith('.yaml');

      if (isJson || isYaml) {
        setSelectedFile(file);
        setError(null);
      } else {
        setError('Please select a JSON or YAML file (.json, .yml, .yaml)');
        setSelectedFile(null);
      }
    }
  };

  const handleUpload = useCallback(async () => {
    if (!selectedFile) {
      setError('Please select a file');
      return;
    }

    if (!documentName.trim()) {
      setError('Please specify document name');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await documentsApi.uploadDocument(selectedFile, documentName.trim());
      setSuccess('Document successfully uploaded!');
      setSelectedFile(null);
      setDocumentName('');

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
      setError(err.response?.data?.message || 'Error uploading document');
    } finally {
      setLoading(false);
    }
  }, [selectedFile, documentName, onUploadSuccess]);

  return (
    <Paper elevation={2} sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        Upload OpenAPI Document
      </Typography>

      <TextField
        fullWidth
        label="Document Name"
        value={documentName}
        onChange={(e) => setDocumentName(e.target.value)}
        margin="normal"
        disabled={loading}
        required
        placeholder="e.g., User Management API"
      />

      <Box sx={{ mt: 2, mb: 2 }}>
        <input
          accept="application/json,.json,.yml,.yaml"
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
            Select JSON or YAML file
          </Button>
        </label>
      </Box>

      {selectedFile && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Selected file: {selectedFile.name}
        </Typography>
      )}

      <Button
        fullWidth
        variant="contained"
        onClick={handleUpload}
        disabled={!selectedFile || !documentName.trim() || loading}
        startIcon={loading ? <CircularProgress size={20} /> : <CloudUploadIcon />}
      >
        {loading ? 'Uploading...' : 'Upload Document'}
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
    </Paper>
  );
};

