/**
 * NotificationBell — header bell button with unread badge and desktop dropdown.
 *
 * Responsibilities:
 * - Uses existing SVG sprite system (/public/icons.svg#bell-icon).
 * - Displays unread count badge (hidden when 0).
 * - Desktop: toggles NotificationDropdown popover.
 * - Mobile: navigates directly to /notifications.
 * - Cleans up listeners on unmount.
 */

import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useNotifications } from '@/hooks/useNotifications';
import NotificationDropdown from './NotificationDropdown';

const MOBILE_BREAKPOINT_PX = 640;

export default function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const { unreadCount, refetch } = useNotifications();
  const navigate = useNavigate();
  const location = useLocation();

  // Close dropdown automatically when route changes
  const [prevPathname, setPrevPathname] = useState(location.pathname);
  if (location.pathname !== prevPathname) {
    if (isOpen) setIsOpen(false);
    setPrevPathname(location.pathname);
  }

  // Handle outside click and Escape key on desktop
  useEffect(() => {
    if (!isOpen) return;

    const handleOutsideClick = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleOutsideClick);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const handleBellClick = () => {
    // On mobile, prefer direct navigation to the dedicated notifications page
    if (window.innerWidth <= MOBILE_BREAKPOINT_PX) {
      navigate('/notifications');
      return;
    }

    setIsOpen((prev) => !prev);
  };

  const badgeText = unreadCount > 99 ? '99+' : unreadCount > 0 ? String(unreadCount) : null;

  return (
    <div className="notification-bell" ref={containerRef}>
      <button
        type="button"
        className={`notification-bell__btn ${isOpen ? 'is-active' : ''}`}
        onClick={handleBellClick}
        aria-label={
          unreadCount > 0
            ? `Notifications, ${unreadCount} unread`
            : 'Notifications'
        }
        aria-haspopup="true"
        aria-expanded={isOpen}
      >
        <svg
          className="notification-bell__icon"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <use href="/icons.svg#bell-icon" />
        </svg>

        {badgeText && (
          <span className="notification-bell__badge" aria-hidden="true">
            {badgeText}
          </span>
        )}
      </button>

      {isOpen && (
        <NotificationDropdown
          onClose={() => setIsOpen(false)}
          onNotificationRead={refetch}
        />
      )}
    </div>
  );
}
