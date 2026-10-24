import React, { forwardRef, useId } from 'react';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helperText?: string;
  /** Shows a live `used / max` counter once the caller sets `maxLength`. */
  showCount?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, helperText, showCount = false, className = '', id, required, maxLength, value, ...rest },
  ref
) {
  const generatedId = useId();
  const textareaId = id || `textarea-${generatedId}`;
  const errorId = `${textareaId}-error`;
  const helperId = `${textareaId}-helper`;
  const describedBy = [error ? errorId : null, helperText && !error ? helperId : null]
    .filter(Boolean)
    .join(' ');

  const used = typeof value === 'string' ? value.length : 0;
  const nearLimit = typeof maxLength === 'number' && used >= maxLength * 0.8;

  return (
    <div className="flex flex-col w-full">
      {label && (
        <label htmlFor={textareaId} className={`label ${required ? 'label-required' : ''}`}>
          {label}
        </label>
      )}

      <textarea
        ref={ref}
        id={textareaId}
        required={required}
        maxLength={maxLength}
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={['input input-textarea', error ? 'input-error' : '', className]
          .filter(Boolean)
          .join(' ')}
        {...rest}
      />

      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          {error && (
            <p id={errorId} className="error-text" role="alert">
              <span className="material-symbols-outlined text-[14px] shrink-0" aria-hidden="true">
                error
              </span>
              {error}
            </p>
          )}
          {helperText && !error && (
            <p id={helperId} className="helper-text">
              {helperText}
            </p>
          )}
        </div>

        {showCount && typeof maxLength === 'number' && nearLimit && (
          <p
            className={`mt-1.5 font-caption text-caption tabular shrink-0 ${
              used >= maxLength ? 'text-danger-700' : 'text-c-text-subtle'
            }`}
          >
            {used}/{maxLength}
          </p>
        )}
      </div>
    </div>
  );
});
