import { useState } from 'react';

export interface CopyableIdProps {
  value: string;
}

export function CopyableId({ value }: CopyableIdProps) {
  const [copied, setCopied] = useState(false);
  const truncated = value.length > 8 ? `${value.slice(0, 8)}…` : value;

  return (
    <div className="copyable-id" title="Copy to clipboard">
      <span className="font-code-sm text-code-sm tabular">{truncated}</span>
      <button
        type="button"
        onClick={() => {
          navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        }}
        className="material-symbols-outlined text-[14px] text-c-text-subtle hover:text-c-primary-600 hover:scale-110 transition-transform"
        aria-label={copied ? 'Copied to clipboard' : 'Copy to clipboard'}
      >
        {copied ? 'done' : 'content_copy'}
      </button>
    </div>
  );
}