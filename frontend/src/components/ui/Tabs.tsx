export interface TabItem {
  label: string;
  value: string;
  count?: number;
  disabled?: boolean;
}

export interface TabsProps {
  items: TabItem[];
  value: string;
  onChange: (value: string) => void;
  className?: string;
  ariaLabel?: string;
}

export function Tabs({ items, value, onChange, className = '', ariaLabel }: TabsProps) {
  const handleKeyDown = (event: React.KeyboardEvent, currentIndex: number) => {
    const nextIndex =
      event.key === 'ArrowRight'
        ? Math.min(currentIndex + 1, items.length - 1)
        : event.key === 'ArrowLeft'
        ? Math.max(currentIndex - 1, 0)
        : currentIndex;

    if (nextIndex !== currentIndex) {
      onChange(items[nextIndex].value);
    }
  };

  return (
    <div
      className={`flex flex-wrap gap-1 border-b border-c-border ${className}`}
      role="tablist"
      aria-label={ariaLabel}
    >
      {items.map((item, index) => (
        <button
          key={item.value}
          role="tab"
          aria-selected={value === item.value}
          tabIndex={value === item.value ? 0 : -1}
          disabled={item.disabled}
          onClick={() => {
            if (!item.disabled) onChange(item.value);
          }}
          onKeyDown={event => {
            if (!item.disabled) handleKeyDown(event, index);
          }}
          className={`relative flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors duration-fast disabled:cursor-not-allowed disabled:opacity-45 ${
            value === item.value
              ? 'border-c-accent-500 text-c-primary-800'
              : 'border-transparent text-c-text-muted hover:bg-c-surface-sunken hover:text-c-text'
          }`}
        >
          {item.label}
          {typeof item.count === 'number' && (
            <span
              className={`rounded-full px-1.5 py-0.5 text-[10px] tabular ${
                value === item.value
                  ? 'bg-c-accent-100 text-c-accent-800'
                  : 'bg-c-surface-sunken text-c-text-subtle'
              }`}
            >
              {item.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
