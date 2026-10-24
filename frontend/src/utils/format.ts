/**
 * Display formatting helpers.
 *
 * These are presentation-only. Values that the backend owns (statuses, enum
 * names) are never rewritten here, so what the UI shows always matches the wire
 * contract.
 */

/**
 * Parses an API date value.
 *
 * `YYYY-MM-DD` is interpreted as a local calendar date rather than UTC midnight,
 * so a date-only field does not slip to the previous day in a negative-offset
 * timezone.
 */
function toDate(value: string): Date | null {
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = dateOnly
    ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Renders an ISO timestamp in the viewer's locale, or an em dash when absent. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  if (!date) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

/** Date without a time component, for interview schedules. */
export function formatDay(value: string | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  if (!date) return '—';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

/** 24-hour clock value as stored (`HH:mm`), passed through when already plain. */
export function formatTime(value: string | null | undefined): string {
  if (!value) return '—';
  // The service stores a LocalTime; it arrives as `HH:mm` or `HH:mm:ss`.
  const match = /^(\d{2}):(\d{2})/.exec(value);
  if (match) return `${match[1]}:${match[2]}`;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    timeStyle: 'short',
  }).format(date);
}

/** Byte counts as KB/MB with one decimal place. */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Title-cases an enum value for use as prose, e.g. `UNDER_REVIEW` -> `Under review`. */
export function humanise(value: string): string {
  const lower = value.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** `1` -> `1 item`, `3` -> `3 items`. */
export function pluralise(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
