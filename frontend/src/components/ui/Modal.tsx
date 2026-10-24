import React from 'react';
import { Button } from './Button';

export interface ModalProps {
  children: React.ReactNode;
  title?: string;
  onClose: () => void;
  className?: string;
}

/**
 * Modal dialog with backdrop, header, body, and footer sections.
 */
export function Modal({ children, title, onClose, className = '' }: ModalProps) {
  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div className={`modal ${className}`}>
        {title && (
          <div className="modal-header">
            <h2 className="font-headline-sm text-headline-sm text-on-surface" id="modal-title">
              {title}
            </h2>
            <button
              type="button"
              onClick={onClose}
              className="material-symbols-outlined text-[20px] text-c-text-muted hover:text-c-text"
              aria-label="Close dialog"
            >
              close
            </button>
          </div>
        )}
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

/**
 * Confirmation dialog with title, message, confirm/cancel actions.
 */
export function ConfirmDialog({
  title = 'Confirm action',
  message,
  onConfirm,
  onCancel,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  confirmLoading = false,
  className = '',
}: {
  title?: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmLoading?: boolean;
  className?: string;
}) {
  return (
    <Modal title={title} onClose={onCancel} className={className}>
      <div className="space-y-4">
        <p className="font-body-md text-body-md text-on-surface">{message}</p>
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onCancel} size="sm" disabled={confirmLoading}>
            {cancelLabel}
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            size="sm"
            isLoading={confirmLoading}
            disabled={confirmLoading}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}