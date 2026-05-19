import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, CircularProgress, Alert } from '@mui/material';
import { documentsApi } from '../api/documentsApi';
import { ChatInterface } from './ChatInterface';
import type { SwaggerDocument } from '../models/types';

export const DocumentPage: React.FC = () => {
  const { documentId } = useParams<{ documentId: string }>();
  const navigate = useNavigate();
  const [document, setDocument] = useState<SwaggerDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!documentId) return;
    setLoading(true);
    documentsApi.getDocument(documentId)
      .then(doc => setDocument(doc))
      .catch(err => setError(err.response?.data?.message || 'Error loading document'))
      .finally(() => setLoading(false));
  }, [documentId]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !document) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{error ?? 'Document not found'}</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column', p: 2 }}>
      <ChatInterface document={document} onBack={() => navigate('/')} />
    </Box>
  );
};

