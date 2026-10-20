import React, { forwardRef } from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  showPasswordToggle?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ 
    label, 
    error, 
    helperText, 
    leftIcon, 
    rightIcon, 
    showPasswordToggle = false,
    className = '',
    id,
    ...props 
  }, ref) => {
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-');
    const errorId = error ? `${inputId}-error` : undefined;
    const helperId = helperText ? `${inputId}-helper` : undefined;
    const [showPassword, setShowPassword] = React.useState(false);
    const type = showPasswordToggle && showPassword ? 'text' : props.type || 'text';

    return (
      <div className="flex flex-col">
        {label && (
          <label htmlFor={inputId} className="label">
            {label}
            {props.required && <span aria-hidden="true" className="text-danger-600">*</span>}
          </label>
        )}
        <div className="relative flex items-center">
          {leftIcon && (
            <span className="absolute left-3 text-c-text-subtle pointer-events-none">
              {leftIcon}
            </span>
          )}
          <input
            ref={ref}
            id={inputId}
            className={`input ${error ? 'input-error' : ''} ${leftIcon ? 'pl-10' : ''} ${rightIcon || showPasswordToggle ? 'pr-10' : ''} ${className}`}
            aria-invalid={error ? 'true' : 'false'}
            aria-describedby={[errorId, helperId].filter(Boolean).join(' ') || undefined}
            type={type}
            {...props}
          />
          {showPasswordToggle && (
            <button
              type="button"
              className="absolute right-2 p-1.5 text-c-text-muted hover:text-on-surface transition-colors focus:outline-none focus:ring-1 focus:ring-c-primary-700 rounded"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              aria-pressed={showPassword}
            >
              <span className="material-symbols-outlined text-[20px] select-none">
                {showPassword ? 'visibility_off' : 'visibility'}
              </span>
            </button>
          )}
          {rightIcon && !showPasswordToggle && (
            <span className="absolute right-3 text-c-text-subtle pointer-events-none">
              {rightIcon}
            </span>
          )}
        </div>
        {error && (
          <p id={errorId} className="error-text" role="alert">
            <span className="material-symbols-outlined text-[12px] inline align-middle mr-1">error</span>
            {error}
          </p>
        )}
        {helperText && !error && (
          <p id={helperId} className="helper-text">{helperText}</p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';