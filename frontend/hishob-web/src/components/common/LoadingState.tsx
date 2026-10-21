/**
 * LoadingState — a spinner / skeleton placeholder for async data with optional subtle brand icon.
 */

import BrandLogo from './BrandLogo';

interface LoadingStateProps {
  label?: string;  // default: "Loading…"
  height?: 'sm' | 'md' | 'lg';
  branded?: boolean;
}

export default function LoadingState({
  label = 'Loading…',
  height = 'md',
  branded = height === 'lg',
}: LoadingStateProps) {
  return (
    <div
      className={`loading-state loading-state--${height}`}
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      {branded && (
        <div className="loading-state__brand" style={{ marginBottom: '0.75rem', display: 'flex', justifyContent: 'center' }}>
          <BrandLogo variant="icon" size={height === 'lg' ? 'md' : 'sm'} priority />
        </div>
      )}
      <div className="loading-state__spinner" aria-hidden="true" />
      <span className="loading-state__label">{label}</span>
    </div>
  );
}
