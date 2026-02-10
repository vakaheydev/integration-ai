import { useState, useEffect } from 'react';
import { Container, Box, Typography, Divider, Button } from '@mui/material';
import { Refresh as RefreshIcon } from '@mui/icons-material';
import { UploadDocument } from '../components/UploadDocument';
import { DocumentList } from '../components/DocumentList';
import { ChatInterface } from './ChatInterface';
import { documentsApi } from '../api/documentsApi';
import type { SwaggerDocument } from '../models/types';

export const MainPage: React.FC = () => {
  const [documents, setDocuments] = useState<SwaggerDocument[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<SwaggerDocument | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDocuments = async () => {
    setLoading(true);
    setError(null);
    try {
      const docs = await documentsApi.getDocuments();
      setDocuments(docs);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка при загрузке документов');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDocuments();
  }, []);

  const handleUploadSuccess = () => {
    loadDocuments();
  };

  const handleSelectDocument = (document: SwaggerDocument) => {
    setSelectedDocument(document);
  };

  const handleBackToList = () => {
    setSelectedDocument(null);
  };

  // Если выбран документ, показываем интерфейс чата
  if (selectedDocument) {
    return (
      <Container maxWidth="lg" sx={{ py: 4, height: '100vh', display: 'flex', flexDirection: 'column' }}>
        <ChatInterface document={selectedDocument} onBack={handleBackToList} />
      </Container>
    );
  }

  // Иначе показываем список документов и форму загрузки
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4, textAlign: 'center' }}>
        <Typography variant="h3" component="h1" gutterBottom>
          OpenAPI Analyzer
        </Typography>
        <Typography variant="subtitle1" color="text.secondary">
          Загрузите Swagger документ и задавайте вопросы с помощью AI
        </Typography>
      </Box>

      <Divider sx={{ mb: 4 }} />

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        <Box>
          <UploadDocument onUploadSuccess={handleUploadSuccess} />
        </Box>

        <Box>
          <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="h6">Документы</Typography>
            <Button
              startIcon={<RefreshIcon />}
              onClick={loadDocuments}
              disabled={loading}
              size="small"
            >
              Обновить
            </Button>
          </Box>
          <DocumentList
            documents={documents}
            loading={loading}
            error={error}
            onSelectDocument={handleSelectDocument}
          />
        </Box>
      </Box>
    </Container>
  );
};

