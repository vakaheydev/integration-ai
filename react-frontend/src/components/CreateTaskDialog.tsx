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
  RadioGroup,
  FormControlLabel,
  Radio,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { tasksApi } from '../api/tasksApi';
import type { SwaggerDocument, TaskType, ScenarioType, AIModel } from '../models/types';

interface CreateTaskDialogProps {
  open: boolean;
  onClose: () => void;
  documents: SwaggerDocument[];
}

export const CreateTaskDialog: React.FC<CreateTaskDialogProps> = ({ open, onClose, documents }) => {
  const navigate = useNavigate();
  const [selectedDocument, setSelectedDocument] = useState<SwaggerDocument | null>(null);
  const [taskKind, setTaskKind] = useState<'task' | 'scenario'>('task');
  const [taskType, setTaskType] = useState<TaskType>('ANALYZE');
  const [scenarioType, setScenarioType] = useState<ScenarioType | ''>('');
  const [description, setDescription] = useState('');
  const [modelName, setModelName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [models, setModels] = useState<AIModel[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);

  useEffect(() => {
    if (open) {
      setModelsLoading(true);
      tasksApi.getModels()
        .then((data) => setModels(data.availableModels || []))
        .catch(() => setModels([]))
        .finally(() => setModelsLoading(false));
    }
  }, [open]);

  const handleClose = () => {
    if (loading) return;
    setSelectedDocument(null);
    setTaskKind('task');
    setTaskType('ANALYZE');
    setScenarioType('');
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
      let request: any = { description: description.trim() };
      if (taskKind === 'task') {
        request.type = taskType;
      } else if (taskKind === 'scenario') {
        request.type = 'ANALYZE'; // или другой дефолт, если требуется
        request.scenarioType = scenarioType;
      }
      if (modelName) request.modelName = modelName;
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

        <FormControl component="fieldset" sx={{ mt: 2 }}>
          <RadioGroup
            row
            value={taskKind}
            onChange={e => setTaskKind(e.target.value as 'task' | 'scenario')}
          >
            <FormControlLabel value="task" control={<Radio />} label="Regular task" />
            <FormControlLabel value="scenario" control={<Radio />} label="Scenario task" />
          </RadioGroup>
        </FormControl>

        {taskKind === 'task' && (
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
            </Select>
          </FormControl>
        )}
        {taskKind === 'scenario' && (
          <FormControl fullWidth>
            <InputLabel>Scenario type</InputLabel>
            <Select
              value={scenarioType}
              label="Scenario type"
              onChange={(e) => setScenarioType(e.target.value as ScenarioType | '')}
            >
              <MenuItem value="">— None —</MenuItem>
              <MenuItem value="ANALYZE_CODE">Analysis + Code (ANALYZE_CODE)</MenuItem>
              <MenuItem value="ANALYZE_TEST">Analysis + Tests (ANALYZE_TEST)</MenuItem>
              <MenuItem value="ANALYZE_CODE_TEST">Analysis + Code + Tests (ANALYZE_CODE_TEST)</MenuItem>
            </Select>
          </FormControl>
        )}

        <FormControl fullWidth sx={{ mt: 2 }}>
          <InputLabel>Model (optional)</InputLabel>
          <Select
            value={modelName}
            label="Model (optional)"
            onChange={e => setModelName(e.target.value)}
            disabled={modelsLoading}
          >
            <MenuItem value="">Default</MenuItem>
            {models.map((m) => (
              <MenuItem key={m.id} value={m.id}>{m.name}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          label="Description"
          multiline
          rows={5}
          fullWidth
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="Describe what you want the AI to do..."
          required
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} disabled={loading}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleCreate}
          disabled={!description.trim() || loading || (taskKind === 'scenario' && !scenarioType)}
          startIcon={loading ? <CircularProgress size={16} /> : undefined}
        >
          {loading ? 'Creating...' : 'Create task'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
