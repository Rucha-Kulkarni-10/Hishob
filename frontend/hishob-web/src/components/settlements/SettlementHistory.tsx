/**
 * SettlementHistory — displays past settlement records in clean visual cards.
 *
 * Shows a chronological list of completed settlements with:
 * - Proper visual separation between cards
 * - Clear sender and receiver ("You → Chintan Patel", "Chintan Patel → You")
 * - Visually prominent amount
 * - Separated date & status badge
 * - Optional notes block
 * - No raw unstyled text, no exposed UUIDs
 */

import type { SettlementHistoryItem } from '@/types/settlements';

interface SettlementHistoryProps {
  settlements: SettlementHistoryItem[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
  isLoading?: boolean;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatDate(isoDate: string): string {
  if (!isoDate) return '—';
  const date = new Date(isoDate);
  if (isNaN(date.getTime())) return '—';
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  // Recent dates
  if (diffDays === 0) {
    return 'Today';
  }
  if (diffDays === 1) {
    return 'Yesterday';
  }
  if (diffDays < 7) {
    return `${diffDays} days ago`;
  }

  // Older dates: "Jan 15, 2024"
  return date.toLocaleDateString('en-IN', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export default function SettlementHistory({
  settlements,
  memberNames,
  currentUserId,
  isLoading = false,
}: SettlementHistoryProps) {
  if (isLoading) {
    return (
      <div className="settlement-history">
        <p className="settlement-history__loading">Loading settlement history...</p>
      </div>
    );
  }

  if (settlements.length === 0) {
    return (
      <div className="settlement-history">
        <div className="settlement-history__empty">
          <span className="settlement-history__empty-icon">📜</span>
          <p className="settlement-history__empty-text">
            No settlements recorded yet.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="settlement-history">
      <div className="settlement-history__list">
        {settlements.map((settlement) => {
          const fromIsMe = currentUserId ? settlement.fromUserId === currentUserId : false;
          const toIsMe = currentUserId ? settlement.toUserId === currentUserId : false;

          const isUUID = (str?: string) =>
            str ? /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str) : false;

          const getDisplayName = (userId: string, backendName?: string) => {
            if (memberNames[userId]) return memberNames[userId];
            if (backendName && !isUUID(backendName)) return backendName;
            return 'Unknown member';
          };

          const fromLabel = getDisplayName(settlement.fromUserId, settlement.fromUserName);
          const toLabel = getDisplayName(settlement.toUserId, settlement.toUserName);

          const isSelf = settlement.fromUserId === settlement.toUserId;
          const status = settlement.status || 'COMPLETED';

          return (
            <div
              key={settlement.id}
              className={`settlement-history-card${
                fromIsMe || toIsMe ? ' settlement-history-card--involves-me' : ''
              }`}
            >
              <div className="settlement-history-card__header">
                <div className="settlement-history-card__parties">
                  <span className="settlement-history-card__party settlement-history-card__party--from">
                    {fromLabel}
                  </span>
                  <span className="settlement-history-card__arrow" aria-hidden="true">
                    →
                  </span>
                  <span className="settlement-history-card__party settlement-history-card__party--to">
                    {toLabel}
                  </span>
                  {isSelf && (
                    <span className="badge badge--danger" style={{ marginLeft: '0.5rem', fontSize: '0.7rem' }}>
                      Self
                    </span>
                  )}
                </div>
              </div>

              <div className="settlement-history-card__amount">
                ₹{formatAmount(settlement.amount)}
              </div>

              <div className="settlement-history-card__footer">
                <span className="settlement-history-card__date">
                  {formatDate(settlement.createdAt || settlement.settledAt || '')}
                </span>
                <span className="settlement-history-card__status badge badge--success">
                  ✓ {status}
                </span>
              </div>

              {settlement.notes && (
                <div className="settlement-history-card__notes">
                  <span className="settlement-history-card__notes-label">Note:</span>{' '}
                  {settlement.notes}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
