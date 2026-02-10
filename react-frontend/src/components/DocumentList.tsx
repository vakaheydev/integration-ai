import { useState } from 'react';
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
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
} from '@mui/material';
import { Description as DescriptionIcon, Delete as DeleteIcon } from '@mui/icons-material';
import type { SwaggerDocument } from '../models/types';
import { documentsApi } from '../api/documentsApi';

interface DocumentListProps {
  documents: SwaggerDocument[];
  loading: boolean;
  error: string | null;
  onSelectDocument: (document: SwaggerDocument) => void;
  onDocumentDeleted?: () => void;
}

export const DocumentList: React.FC<DocumentListProps> = ({
  documents,
  loading,
  error,
  onSelectDocument,
  onDocumentDeleted,
}) => {
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [documentToDelete, setDocumentToDelete] = useState<SwaggerDocument | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleDeleteClick = (doc: SwaggerDocument, event: React.MouseEvent) => {
    event.stopPropagation(); // Предотвращаем открытие документа при клике на кнопку удаления
    setDocumentToDelete(doc);
    setDeleteDialogOpen(true);
    setDeleteError(null);
  };

  const handleDeleteCancel = () => {
    setDeleteDialogOpen(false);
    setDocumentToDelete(null);
    setDeleteError(null);
  };

  const handleDeleteConfirm = async () => {
    if (!documentToDelete) return;

    setDeleting(true);
    setDeleteError(null);

    try {
      await documentsApi.deleteDocument(documentToDelete.id);
      setDeleteDialogOpen(false);
      setDocumentToDelete(null);

      // Уведомляем родительский компонент об успешном удалении
      if (onDocumentDeleted) {
        onDocumentDeleted();
      }
    } catch (err: any) {
      setDeleteError(err.response?.data?.message || 'Ошибка при удалении документа');
    } finally {
      setDeleting(false);
    }
  };

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
          <ListItem
            key={doc.id}
            disablePadding
            secondaryAction={
              <IconButton
                edge="end"
                aria-label="delete"
                onClick={(e) => handleDeleteClick(doc, e)}
                color="error"
              >
                <DeleteIcon />
              </IconButton>
            }
          >
            <ListItemButton onClick={() => onSelectDocument(doc)}>
              <Box sx={{ mr: 2, display: 'flex', alignItems: 'center' }}>
                <DescriptionIcon color="primary" />
              </Box>
              <ListItemText
                primary={doc.name || doc.summary || `Документ ${doc.id}`}
                secondary={`ID: ${doc.id}${doc.uploadedAt ? ` • ${new Date(doc.uploadedAt).toLocaleDateString('ru-RU')}` : ''}`}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      {/* Диалог подтверждения удаления */}
      <Dialog
        open={deleteDialogOpen}
        onClose={handleDeleteCancel}
        aria-labelledby="delete-dialog-title"
        aria-describedby="delete-dialog-description"
      >
        <DialogTitle id="delete-dialog-title">
          Удалить документ?
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-dialog-description">
            Вы действительно хотите удалить документ{' '}
            <strong>
              {documentToDelete?.name || documentToDelete?.summary || `с ID ${documentToDelete?.id}`}
            </strong>?
            <br />
            Это действие нельзя отменить.
          </DialogContentText>
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleting}>
            Отмена
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={20} /> : undefined}
          >
            {deleting ? 'Удаление...' : 'Удалить'}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};

