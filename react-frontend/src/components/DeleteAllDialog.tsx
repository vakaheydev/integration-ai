import React, { useState, useEffect } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress,
} from '@mui/material';
import { Warning as WarningIcon } from '@mui/icons-material';

interface DeleteAllDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  itemName: string; // например "tasks" или "documents"
}

const COUNTDOWN = 5;

export const DeleteAllDialog: React.FC<DeleteAllDialogProps> = ({
  open, onClose, onConfirm, itemName,
}) => {
  const [seconds, setSeconds] = useState(COUNTDOWN);
  const [loading, setLoading] = useState(false);

  // Запускаем таймер при открытии
  useEffect(() => {
    if (!open) return;
    setSeconds(COUNTDOWN);
    const interval = setInterval(() => {
      setSeconds(prev => {
        if (prev <= 1) { clearInterval(interval); return 0; }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [open]);

  const handleConfirm = async () => {
    setLoading(true);
    try {
      await onConfirm();
      onClose();
    } finally {
      setLoading(false);
    }
  };

  const disabled = seconds > 0 || loading;

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'error.main' }}>
        <WarningIcon color="error" />
        Delete all {itemName}?
      </DialogTitle>

      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          Are you sure you want to delete <strong>all {itemName}</strong>? This action is <strong>irreversible</strong>.
        </Typography>

        {/* Прогресс-кольцо с таймером */}
        {seconds > 0 && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 1 }}>
            <Box sx={{ position: 'relative', display: 'inline-flex' }}>
              <CircularProgress
                variant="determinate"
                value={(1 - seconds / COUNTDOWN) * 100}
                size={36}
                color="error"
              />
              <Box sx={{
                top: 0, left: 0, bottom: 0, right: 0,
                position: 'absolute',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Typography variant="caption" fontWeight="bold" color="error">{seconds}</Typography>
              </Box>
            </Box>
            <Typography variant="body2" color="text.secondary">
              The button will become active in {seconds} sec...
            </Typography>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={loading}>Cancel</Button>
        <Button
          variant="contained"
          color="error"
          onClick={handleConfirm}
          disabled={disabled}
          startIcon={loading ? <CircularProgress size={16} sx={{ color: 'white' }} /> : undefined}
        >
          {loading ? 'Deleting...' : `Delete all${seconds > 0 ? ` (${seconds})` : ''}`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

