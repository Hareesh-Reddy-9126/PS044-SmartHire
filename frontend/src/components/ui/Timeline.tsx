export interface TimelineItem {
  title: string;
  subtitle?: string;
  time?: string;
  status?: 'pending' | 'active' | 'completed';
  description?: string;
}

export interface TimelineProps {
  items: TimelineItem[];
}

/**
 * A vertical record of a workflow. The track is structurally meaningful: it
 * shows the actual order and the position of the current state, so colour is
 * never the only signal.
 */
export function Timeline({ items }: TimelineProps) {
  return (
    <ol className="timeline">
      {items.map(item => {
        const status = item.status ?? 'pending';
        return (
          <li key={item.title} className={`timeline-item timeline-item-${status}`}>
            <span className="timeline-marker" aria-hidden="true">
              <span className="material-symbols-outlined text-[16px]">
                {status === 'completed' ? 'check' : status === 'active' ? 'radio_button_checked' : 'circle'}
              </span>
            </span>
            <div className="min-w-0 grow pt-1">
              <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                <h3 className="timeline-title">{item.title}</h3>
                {item.time && <span className="timeline-meta">{item.time}</span>}
              </div>
              {item.subtitle && <p className="timeline-meta mt-0.5">{item.subtitle}</p>}
              {item.description && (
                <p className="mt-1 font-body-sm text-body-sm text-c-text-muted">{item.description}</p>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
