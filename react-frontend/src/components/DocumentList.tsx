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
  Tooltip,
} from '@mui/material';
import { Description as DescriptionIcon, Delete as DeleteIcon, DeleteSweep as DeleteAllIcon } from '@mui/icons-material';
import type { SwaggerDocument } from '../models/types';
import { documentsApi } from '../api/documentsApi';
import { DeleteAllDialog } from './DeleteAllDialog';

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
  const [deleteAllOpen, setDeleteAllOpen] = useState(false);

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
      setDeleteError(err.response?.data?.message || 'Error deleting document');
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <Paper elevation={2} sx={{ p: 3, textAlign: 'center' }}>
        <CircularProgress />
        <Typography sx={{ mt: 2 }}>Loading documents...</Typography>
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
          No uploaded documents. Upload your first OpenAPI document.
        </Alert>
      </Paper>
    );
  }

  return (
    <Paper elevation={2}>
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h6">My Documents</Typography>
        <Tooltip title="Delete all documents">
          <span>
            <IconButton
              size="small"
              color="error"
              onClick={() => setDeleteAllOpen(true)}
              disabled={documents.length === 0}
            >
              <DeleteAllIcon />
            </IconButton>
          </span>
        </Tooltip>
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
                primary={doc.name || doc.summary || `Document ${doc.id}`}
                secondary={`ID: ${doc.id}${doc.uploadedAt ? ` • ${new Date(doc.uploadedAt).toLocaleDateString('en-US')}` : ''}`}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      {/* Диалог подтверждения удаления одного документа */}
      <Dialog open={deleteDialogOpen} onClose={handleDeleteCancel}>
        <DialogTitle>Delete document?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete the document{' '}
            <strong>
              {documentToDelete?.name || documentToDelete?.summary || `with ID ${documentToDelete?.id}`}
            </strong>?
            <br />
            This action cannot be undone.
          </DialogContentText>
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleting}>
            Cancel
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={20} /> : undefined}
          >
            {deleting ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Диалог удаления всех документов */}
      <DeleteAllDialog
        open={deleteAllOpen}
        onClose={() => setDeleteAllOpen(false)}
        onConfirm={async () => {
          await documentsApi.deleteAllDocuments();
          if (onDocumentDeleted) onDocumentDeleted();
        }}
        itemName="documents"
      />
    </Paper>
  );
};
