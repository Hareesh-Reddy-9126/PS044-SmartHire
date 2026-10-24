interface SpinnerProps {
  className?: string;
  /** Announced to assistive technology when the spinner is the only signal. */
  label?: string;
}

/**
 * Indeterminate progress indicator. Decorative by default so it does not
 * duplicate a button's `aria-busy`; pass `label` when it stands alone.
 */
export function Spinner({ className = 'h-5 w-5', label }: SpinnerProps) {
  return (
    <svg
      className={`animate-spin ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      role={label ? 'status' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  );
}
