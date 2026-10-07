/**
 * Modal — accessible dialog shell with backdrop.
 *
 * Features:
 *   - Closes when the user clicks the backdrop
 *   - Closes on ESC key press
 *   - Sets aria-modal="true" and aria-labelledby
 *   - Prevents body scroll while open
 *   - Renders title bar, content body, and action footer slot via children
 */

import { useEffect, type ReactNode } from 'react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  description?: string;
  widthClass?: string; // default: "modal__dialog--md"
}

export default function Modal({
  isOpen,
  onClose,
  title,
  children,
  description,
  widthClass = 'modal__dialog--md',
}: ModalProps) {
  // ── Lock body scroll & register ESC close ────────────────────────────────
  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const titleId = `modal-title-${title.replace(/\s+/g, '-').toLowerCase()}`;
  const descId = description ? `${titleId}-desc` : undefined;

  const handleBackdrop = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div
      className="modal__backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descId}
      onMouseDown={handleBackdrop}
    >
      <div className={`modal__dialog ${widthClass}`}>
        <header className="modal__header">
          <h3 id={titleId} className="modal__title">
            {title}
          </h3>
          <button
            type="button"
            className="modal__close"
            onClick={onClose}
            aria-label="Close dialog"
          >
            ×
          </button>
        </header>

        {description && (
          <p id={descId} className="modal__description">
            {description}
          </p>
        )}

        <div className="modal__body">{children}</div>
      </div>
    </div>
  );
}
