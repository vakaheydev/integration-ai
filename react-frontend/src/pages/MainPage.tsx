import { useState, useEffect } from 'react';
import { Container, Box, Typography, Divider, Button, AppBar, Toolbar, IconButton, Menu, MenuItem } from '@mui/material';
import { Refresh as RefreshIcon, AccountCircle, Logout } from '@mui/icons-material';
import { UploadDocument } from '../components/UploadDocument';
import { DocumentList } from '../components/DocumentList';
import { ChatInterface } from './ChatInterface';
import { documentsApi } from '../api/documentsApi';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import type { SwaggerDocument } from '../models/types';

export const MainPage: React.FC = () => {
  const [documents, setDocuments] = useState<SwaggerDocument[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<SwaggerDocument | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const { username, logout } = useAuth();
  const navigate = useNavigate();

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    handleMenuClose();
    logout();
    navigate('/login');
  };

  const loadDocuments = async () => {
    setLoading(true);
    setError(null);
    try {
      const docs = await documentsApi.getDocuments();
      setDocuments(docs);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading documents');
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
    <>
      <AppBar position="static" elevation={1}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            OpenAPI Analyzer
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2">{username}</Typography>
            <IconButton
              size="large"
              onClick={handleMenuOpen}
              color="inherit"
            >
              <AccountCircle />
            </IconButton>
            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={handleMenuClose}
              anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'right',
              }}
              transformOrigin={{
                vertical: 'top',
                horizontal: 'right',
              }}
            >
              <MenuItem disabled>
                <Typography variant="body2" color="text.secondary">
                  {username}
                </Typography>
              </MenuItem>
              <Divider />
              <MenuItem onClick={handleLogout}>
                <Logout fontSize="small" sx={{ mr: 1 }} />
                Logout
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box sx={{ mb: 4, textAlign: 'center' }}>
          <Typography variant="h3" component="h1" gutterBottom>
            OpenAPI Analyzer
          </Typography>
          <Typography variant="subtitle1" color="text.secondary">
            Upload Swagger document and ask questions using AI
          </Typography>
        </Box>

      <Divider sx={{ mb: 4 }} />

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        <Box>
          <UploadDocument onUploadSuccess={handleUploadSuccess} />
        </Box>

        <Box>
          <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="h6">Documents</Typography>
            <Button
              startIcon={<RefreshIcon />}
              onClick={loadDocuments}
              disabled={loading}
              size="small"
            >
              Refresh
            </Button>
          </Box>
          <DocumentList
            documents={documents}
            loading={loading}
            error={error}
            onSelectDocument={handleSelectDocument}
            onDocumentDeleted={loadDocuments}
          />
        </Box>
      </Box>
      </Container>
    </>
  );
};

