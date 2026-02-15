import { useEffect, useState } from 'react';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import { Box, Alert, CircularProgress, Typography } from '@mui/material';

interface SwaggerUIViewerProps {
  content: string;
  documentName?: string;
}

export const SwaggerUIViewer: React.FC<SwaggerUIViewerProps> = ({ content, documentName }) => {
  const [spec, setSpec] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      setLoading(true);
      setError(null);

      // Try to parse as JSON first
      let parsedSpec;
      try {
        parsedSpec = JSON.parse(content);
      } catch {
        // If JSON parsing fails, assume it's YAML and use it as-is
        // SwaggerUI can handle YAML strings
        parsedSpec = content;
      }

      setSpec(parsedSpec);
    } catch (err: any) {
      setError(`Error loading OpenAPI document: ${err.message}`);
    } finally {
      setLoading(false);
    }
  }, [content]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{error}</Alert>
      </Box>
    );
  }

  if (!spec) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">No OpenAPI specification available</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', overflow: 'auto' }}>
      {documentName && (
        <Box sx={{ p: 2, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="h6">{documentName}</Typography>
          <Typography variant="caption" color="text.secondary">
            OpenAPI Specification
          </Typography>
        </Box>
      )}
      <SwaggerUI spec={spec} />
    </Box>
  );
};

