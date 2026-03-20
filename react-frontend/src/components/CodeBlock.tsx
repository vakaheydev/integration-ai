import React from 'react';
import { Box, Paper, IconButton, Typography, Tooltip, CircularProgress, Chip, Divider, Button } from '@mui/material';
import { ContentCopy as CopyIcon, PlayArrow as RunIcon, CheckCircle as SuccessIcon, Error as ErrorIcon, ExpandLess as CollapseIcon, ExpandMore as ExpandIcon } from '@mui/icons-material';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { tasksApi } from '../api/tasksApi';

interface CodeBlockProps {
  code: string;
  language?: string;
  taskId?: string;
  onExecResult?: (result: ExecResult | null) => void;
}

interface ExecResult {
  success: boolean;
  exitCode: number;
  stdout: string;
  stderr: string;
  timedOut: boolean;
}

export const CodeBlock: React.FC<CodeBlockProps> = ({ code, language = 'python', taskId, onExecResult }) => {
  const [copied, setCopied] = React.useState(false);
  const [running, setRunning] = React.useState(false);
  const [execResult, setExecResult] = React.useState<ExecResult | null>(null);
  const [execError, setExecError] = React.useState<string | null>(null);
  const [collapsed, setCollapsed] = React.useState(false);

  const isPython = language?.toLowerCase() === 'python';
  const canRun = isPython && !!taskId;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy code:', err);
    }
  };

  const handleRun = async () => {
    if (!taskId) return;
    setRunning(true);
    setExecResult(null);
    setExecError(null);
    try {
      const result = await tasksApi.executeCode(taskId, code);
      setExecResult(result);
      onExecResult?.(result);
    } catch (err: any) {
      setExecError(err.response?.data?.message || 'Execution error');
      onExecResult?.(null);
    } finally {
      setRunning(false);
    }
  };

  return (
    <Paper
      elevation={0}
      sx={{ position: 'relative', bgcolor: '#1e1e1e', borderRadius: 1, overflow: 'hidden', my: 1 }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2, py: 1, bgcolor: '#2d2d2d', borderBottom: '1px solid #3e3e3e' }}>
        <Typography variant="caption" sx={{ color: '#858585', fontFamily: 'monospace' }}>
          {language}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {canRun && (
            <Tooltip title={running ? 'Running...' : 'Run code'}>
              <span>
                <Button
                  size="small"
                  variant="contained"
                  color="success"
                  onClick={handleRun}
                  disabled={running}
                  startIcon={running ? <CircularProgress size={12} sx={{ color: 'white' }} /> : <RunIcon fontSize="small" />}
                  sx={{ height: 28, fontSize: '0.75rem', px: 1.5, minWidth: 'auto' }}
                >
                  {running ? 'Running...' : 'Run'}
                </Button>
              </span>
            </Tooltip>
          )}
          <Tooltip title={copied ? 'Copied!' : 'Copy code'}>
            <IconButton size="small" onClick={handleCopy} sx={{ color: copied ? 'success.main' : '#858585', '&:hover': { color: 'white' } }}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title={collapsed ? 'Expand' : 'Collapse'}>
            <IconButton size="small" onClick={() => setCollapsed(v => !v)} sx={{ color: '#858585', '&:hover': { color: 'white' } }}>
              {collapsed ? <ExpandIcon fontSize="small" /> : <CollapseIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Execution result — always above the code block */}
      {execError && (
        <Box sx={{ px: 2, py: 1.5, bgcolor: '#3a0000', borderBottom: '1px solid #3e3e3e' }}>
          <Typography variant="caption" sx={{ color: '#f44336' }}>{execError}</Typography>
        </Box>
      )}
      {execResult && (
        <Box sx={{ borderBottom: '1px solid #3e3e3e', bgcolor: '#252526' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1, borderBottom: '1px solid #3e3e3e' }}>
            {execResult.timedOut ? (
              <Chip label="Timed Out" size="small" color="warning" icon={<ErrorIcon />} sx={{ fontSize: '0.7rem' }} />
            ) : execResult.success ? (
              <Chip label="Success" size="small" color="success" icon={<SuccessIcon />} sx={{ fontSize: '0.7rem' }} />
            ) : (
              <Chip label={`Failed (exit ${execResult.exitCode})`} size="small" color="error" icon={<ErrorIcon />} sx={{ fontSize: '0.7rem' }} />
            )}
          </Box>
          {execResult.stdout && (
            <Box sx={{ px: 2, py: 1.5 }}>
              <Typography variant="caption" sx={{ color: '#858585', display: 'block', mb: 0.5 }}>stdout</Typography>
              <Box component="pre" sx={{ m: 0, p: 0, fontFamily: 'monospace', fontSize: '0.8rem', color: '#d4d4d4', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 300, overflowY: 'auto' }}>
                {execResult.stdout}
              </Box>
            </Box>
          )}
          {execResult.stderr && (
            <>
              {execResult.stdout && <Divider sx={{ borderColor: '#3e3e3e' }} />}
              <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="caption" sx={{ color: '#f48771', display: 'block', mb: 0.5 }}>stderr</Typography>
                <Box component="pre" sx={{ m: 0, p: 0, fontFamily: 'monospace', fontSize: '0.8rem', color: '#f48771', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 300, overflowY: 'auto' }}>
                  {execResult.stderr}
                </Box>
              </Box>
            </>
          )}
          {!execResult.stdout && !execResult.stderr && (
            <Box sx={{ px: 2, py: 1.5 }}>
              <Typography variant="caption" sx={{ color: '#858585', fontStyle: 'italic' }}>No output</Typography>
            </Box>
          )}
        </Box>
      )}

      {!collapsed && (
        <>
          {/* Code */}
          <SyntaxHighlighter
            language={language}
            style={vscDarkPlus}
            customStyle={{ margin: 0, padding: '16px', background: '#1e1e1e', fontSize: '14px' }}
            showLineNumbers
          >
            {code}
          </SyntaxHighlighter>

          {/* Collapse button at bottom */}
          <Box
            onClick={() => setCollapsed(true)}
            sx={{
              display: 'flex', justifyContent: 'center', alignItems: 'center',
              py: 0.5, bgcolor: '#2d2d2d', borderTop: '1px solid #3e3e3e',
              cursor: 'pointer',
              '&:hover': { bgcolor: '#383838' },
            }}
          >
            <CollapseIcon sx={{ fontSize: 16, color: '#858585' }} />
            <Typography variant="caption" sx={{ color: '#858585', ml: 0.5 }}>Collapse</Typography>
          </Box>
        </>
      )}
    </Paper>
  );
};
