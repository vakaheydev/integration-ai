import React from 'react';
import { Box, Paper, IconButton, Typography, Tooltip } from '@mui/material';
import { ContentCopy as CopyIcon } from '@mui/icons-material';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface CodeBlockProps {
  code: string;
  language?: string;
}

export const CodeBlock: React.FC<CodeBlockProps> = ({ code, language = 'python' }) => {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy code:', err);
    }
  };

  return (
    <Paper
      elevation={0}
      sx={{
        position: 'relative',
        bgcolor: '#1e1e1e',
        borderRadius: 1,
        overflow: 'hidden',
        my: 1,
      }}
    >
      {/* Заголовок с языком и кнопкой копирования */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          px: 2,
          py: 1,
          bgcolor: '#2d2d2d',
          borderBottom: '1px solid #3e3e3e',
        }}
      >
        <Typography variant="caption" sx={{ color: '#858585', fontFamily: 'monospace' }}>
          {language}
        </Typography>
        <Tooltip title={copied ? 'Скопировано!' : 'Копировать код'}>
          <IconButton
            size="small"
            onClick={handleCopy}
            sx={{
              color: copied ? 'success.main' : '#858585',
              '&:hover': {
                color: 'white',
              },
            }}
          >
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Блок кода */}
      <SyntaxHighlighter
        language={language}
        style={vscDarkPlus}
        customStyle={{
          margin: 0,
          padding: '16px',
          background: '#1e1e1e',
          fontSize: '14px',
        }}
        showLineNumbers
      >
        {code}
      </SyntaxHighlighter>
    </Paper>
  );
};

