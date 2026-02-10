import {
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Paper,
  Typography,
  CircularProgress,
  Alert,
  Box,
} from '@mui/material';
import { Description as DescriptionIcon } from '@mui/icons-material';
import type { SwaggerDocument } from '../models/types';

interface DocumentListProps {
  documents: SwaggerDocument[];
  loading: boolean;
  error: string | null;
  onSelectDocument: (document: SwaggerDocument) => void;
}

export const DocumentList: React.FC<DocumentListProps> = ({
  documents,
  loading,
  error,
  onSelectDocument,
}) => {
  if (loading) {
    return (
      <Paper elevation={2} sx={{ p: 3, textAlign: 'center' }}>
        <CircularProgress />
        <Typography sx={{ mt: 2 }}>Загрузка документов...</Typography>
      </Paper>
    );
  }

  if (error) {
    return (
      <Paper elevation={2} sx={{ p: 3 }}>
        <Alert severity="error">{error}</Alert>
      </Paper>
    );
  }

  if (documents.length === 0) {
    return (
      <Paper elevation={2} sx={{ p: 3 }}>
        <Alert severity="info">
          Нет загруженных документов. Загрузите свой первый OpenAPI документ.
        </Alert>
      </Paper>
    );
  }

  return (
    <Paper elevation={2}>
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6">Мои документы</Typography>
      </Box>
      <List>
        {documents.map((doc) => (
          <ListItem key={doc.id} disablePadding>
            <ListItemButton onClick={() => onSelectDocument(doc)}>
              <Box sx={{ mr: 2, display: 'flex', alignItems: 'center' }}>
                <DescriptionIcon color="primary" />
              </Box>
              <ListItemText
                primary={doc.summary || `Документ ${doc.id}`}
                secondary={`ID: ${doc.id}${doc.uploadedAt ? ` • ${new Date(doc.uploadedAt).toLocaleDateString('ru-RU')}` : ''}`}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Paper>
  );
};

