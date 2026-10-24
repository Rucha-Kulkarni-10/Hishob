/**
 * EmptyState — placeholder shown when a list view has zero items.
 */

import type { ReactNode } from 'react';
import BrandLogo from './BrandLogo';

interface EmptyStateProps {
  heading: string;
  description?: string;
  icon?: ReactNode;     // single emoji / text glyph or custom node
  action?: ReactNode;     // e.g. a button
  branded?: boolean;
}

export default function EmptyState({
  heading,
  description,
  icon = '📭',
  action,
  branded = false,
}: EmptyStateProps) {
  return (
    <div className="empty-state" role="status" aria-live="polite">
      <div className="empty-state__icon" aria-hidden="true">
        {branded ? (
          <BrandLogo variant="icon" size="md" style={{ opacity: 0.85 }} />
        ) : (
          icon
        )}
      </div>
      <h3 className="empty-state__heading">{heading}</h3>
      {description && <p className="empty-state__desc">{description}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
