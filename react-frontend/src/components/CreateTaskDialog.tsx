import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  CircularProgress,
  Autocomplete,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { tasksApi } from '../api/tasksApi';
import type { SwaggerDocument, TaskType, AIModel } from '../models/types';

interface CreateTaskDialogProps {
  open: boolean;
  onClose: () => void;
  documents: SwaggerDocument[];
}

export const CreateTaskDialog: React.FC<CreateTaskDialogProps> = ({ open, onClose, documents }) => {
  const navigate = useNavigate();
  const [selectedDocument, setSelectedDocument] = useState<SwaggerDocument | null>(null);
  const [taskType, setTaskType] = useState<TaskType>('ANALYZE');
  const [description, setDescription] = useState('');
  const [modelName, setModelName] = useState<string>('');
  const [availableModels, setAvailableModels] = useState<AIModel[]>([]);
  const [defaultModel, setDefaultModel] = useState<string>('');
  const [modelsLoading, setModelsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch available models when dialog opens
  useEffect(() => {
    if (open) {
      setModelsLoading(true);
      tasksApi.getModels()
        .then(data => {
          setAvailableModels(data.availableModels ?? []);
          const def = data.defaultModel ?? '';
          setDefaultModel(def);
          setModelName(prev => prev || def); // set default only if not already chosen
        })
        .catch(() => {
          setAvailableModels([]);
        })
        .finally(() => setModelsLoading(false));
    }
  }, [open]);

  const handleClose = () => {
    if (loading) return;
    setSelectedDocument(null);
    setTaskType('ANALYZE');
    setDescription('');
    setModelName('');
    setError(null);
    onClose();
  };

  const handleCreate = async () => {
    if (!description.trim()) {
      setError('Please enter a description');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const request = {
        type: taskType,
        description: description.trim(),
        ...(modelName ? { modelName } : {}),
      };
      const task = selectedDocument
        ? await tasksApi.createTask(selectedDocument.id, request)
        : await tasksApi.createTaskWithoutDocument(request);
      handleClose();
      navigate(`/tasks/${task.id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error creating task');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create Task</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        <Autocomplete
          options={documents}
          getOptionLabel={(doc) => doc.name || doc.id}
          value={selectedDocument}
          onChange={(_, val) => setSelectedDocument(val)}
          renderInput={(params) => (
            <TextField {...params} label="Document (optional)" placeholder="Select a document..." />
          )}
          clearOnEscape
        />

        <FormControl fullWidth>
          <InputLabel>Task type</InputLabel>
          <Select
            value={taskType}
            label="Task type"
            onChange={(e) => setTaskType(e.target.value as TaskType)}
          >
            <MenuItem value="ANALYZE">Analysis (ANALYZE)</MenuItem>
            <MenuItem value="CODE">Code generation (CODE)</MenuItem>
            <MenuItem value="TEST">Test generation (TEST)</MenuItem>
            <MenuItem value="ANALYZE_CODE">Analysis + Code (ANALYZE_CODE)</MenuItem>
            <MenuItem value="ANALYZE_TEST">Analysis + Tests (ANALYZE_TEST)</MenuItem>
          </Select>
        </FormControl>

        <FormControl fullWidth>
          <InputLabel>Model</InputLabel>
          <Select
            value={modelName}
            label="Model"
            onChange={(e) => setModelName(e.target.value)}
            disabled={modelsLoading}
            endAdornment={modelsLoading ? <CircularProgress size={20} sx={{ mr: 2 }} /> : undefined}
          >
            {availableModels.map((m) => (
              <MenuItem key={m.id} value={m.id}>
                {m.name}{m.id === defaultModel ? ' (default)' : ''}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          label="Description"
          multiline
          rows={5}
          fullWidth
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Describe what you want the AI to do..."
          required
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} disabled={loading}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleCreate}
          disabled={!description.trim() || loading}
          startIcon={loading ? <CircularProgress size={16} /> : undefined}
        >
          {loading ? 'Creating...' : 'Create task'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

