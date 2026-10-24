import React, { forwardRef, useId, useState } from 'react';

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  /** Renders a show/hide toggle; only meaningful for `type="password"`. */
  showPasswordToggle?: boolean;
  /** Purely presentational size; does not affect the native control. */
  inputSize?: 'sm' | 'md';
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    label,
    error,
    helperText,
    leftIcon,
    rightIcon,
    showPasswordToggle = false,
    inputSize = 'md',
    className = '',
    id,
    type = 'text',
    required,
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const inputId = id || `input-${generatedId}`;
  const errorId = `${inputId}-error`;
  const helperId = `${inputId}-helper`;

  const [revealed, setRevealed] = useState(false);
  const resolvedType = showPasswordToggle && revealed ? 'text' : type;
  const describedBy = [error ? errorId : null, helperText && !error ? helperId : null]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="flex flex-col w-full">
      {label && (
        <label
          htmlFor={inputId}
          className={`label ${required ? 'label-required' : ''}`}
        >
          {label}
        </label>
      )}

      <div className="relative flex items-center">
        {leftIcon && (
          <span className="absolute left-3 text-c-text-subtle pointer-events-none flex" aria-hidden="true">
            {leftIcon}
          </span>
        )}

        <input
          ref={ref}
          id={inputId}
          type={resolvedType}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
          className={[
            'input',
            inputSize === 'sm' ? 'h-9 text-body-sm' : '',
            error ? 'input-error' : '',
            leftIcon ? 'pl-10' : '',
            rightIcon || showPasswordToggle ? 'pr-10' : '',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
          {...rest}
        />

        {showPasswordToggle && (
          <button
            type="button"
            onClick={() => setRevealed(value => !value)}
            aria-label={revealed ? 'Hide password' : 'Show password'}
            aria-pressed={revealed}
            className="absolute right-1.5 flex h-7 w-7 items-center justify-center rounded text-c-text-muted transition-colors duration-fast hover:bg-c-surface-sunken hover:text-on-surface focus-ring"
          >
            <span className="material-symbols-outlined text-[20px]">
              {revealed ? 'visibility_off' : 'visibility'}
            </span>
          </button>
        )}

        {rightIcon && !showPasswordToggle && (
          <span className="absolute right-3 text-c-text-subtle pointer-events-none flex" aria-hidden="true">
            {rightIcon}
          </span>
        )}
      </div>

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
  );
});