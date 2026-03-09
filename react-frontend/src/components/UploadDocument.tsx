import { useState, useCallback } from 'react';
import {
  Box,
  Button,
  TextField,
  Typography,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Tooltip,
} from '@mui/material';
import { CloudUpload as CloudUploadIcon, Add as AddIcon } from '@mui/icons-material';
import { documentsApi } from '../api/documentsApi';

interface UploadDocumentProps {
  onUploadSuccess: () => void;
}

export const UploadDocument: React.FC<UploadDocumentProps> = ({ onUploadSuccess }) => {
  const [open, setOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [documentName, setDocumentName] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleOpen = () => {
    setOpen(true);
    setError(null);
  };

  const handleClose = () => {
    if (loading) return;
    setOpen(false);
    setSelectedFile(null);
    setDocumentName('');
    setError(null);
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      const isJson = file.type === 'application/json' || file.name.endsWith('.json');
      const isYaml = file.name.endsWith('.yml') || file.name.endsWith('.yaml');
      if (isJson || isYaml) {
        setSelectedFile(file);
        setError(null);
        if (!documentName.trim()) {
          setDocumentName(file.name.replace(/\.(json|yml|yaml)$/i, ''));
        }
      } else {
        setError('Please select a JSON or YAML file (.json, .yml, .yaml)');
        setSelectedFile(null);
      }
    }
  };

  const handleUpload = useCallback(async () => {
    if (!selectedFile) { setError('Please select a file'); return; }
    if (!documentName.trim()) { setError('Please specify document name'); return; }

    setLoading(true);
    setError(null);

    try {
      await documentsApi.uploadDocument(selectedFile, documentName.trim());
      setOpen(false);
      setSelectedFile(null);
      setDocumentName('');
      onUploadSuccess();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error uploading document');
    } finally {
      setLoading(false);
    }
  }, [selectedFile, documentName, onUploadSuccess]);

  return (
    <>
      <Tooltip title="Upload new document">
        <IconButton
          color="primary"
          onClick={handleOpen}
          sx={{
            border: '1px dashed',
            borderColor: 'primary.main',
            borderRadius: 2,
            width: '100%',
            py: 1,
            gap: 1,
            '&:hover': { bgcolor: 'primary.50' },
          }}
        >
          <AddIcon />
          <Typography variant="body2" color="primary">Upload document</Typography>
        </IconButton>
      </Tooltip>

      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle>Upload OpenAPI Document</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

          <TextField
            fullWidth
            label="Document Name"
            value={documentName}
            onChange={(e) => setDocumentName(e.target.value)}
            disabled={loading}
            required
            placeholder="e.g., User Management API"
          />

          <Box>
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
                {selectedFile ? selectedFile.name : 'Select JSON or YAML file'}
              </Button>
            </label>
          </Box>
        </DialogContent>

        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleClose} disabled={loading}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleUpload}
            disabled={!selectedFile || !documentName.trim() || loading}
            startIcon={loading ? <CircularProgress size={16} /> : <CloudUploadIcon />}
          >
            {loading ? 'Uploading...' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
