/**
 * LoadingState — a spinner / skeleton placeholder for async data.
 */

interface LoadingStateProps {
  label?: string;  // default: "Loading…"
  height?: 'sm' | 'md' | 'lg';
}

export default function LoadingState({
  label = 'Loading…',
  height = 'md',
}: LoadingStateProps) {
  return (
    <div
      className={`loading-state loading-state--${height}`}
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <div className="loading-state__spinner" aria-hidden="true" />
      <span className="loading-state__label">{label}</span>
    </div>
  );
}
