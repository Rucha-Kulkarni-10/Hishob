/**
 * EmptyState — placeholder shown when a list view has zero items.
 */

import type { ReactNode } from 'react';

interface EmptyStateProps {
  heading: string;
  description?: string;
  icon?: string;          // single emoji / text glyph
  action?: ReactNode;     // e.g. a button
}

export default function EmptyState({
  heading,
  description,
  icon = '📭',
  action,
}: EmptyStateProps) {
  return (
    <div className="empty-state" role="status" aria-live="polite">
      <div className="empty-state__icon" aria-hidden="true">
        {icon}
      </div>
      <h3 className="empty-state__heading">{heading}</h3>
      {description && <p className="empty-state__desc">{description}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
