/**
 * MemberSpendingBar — renders member spending breakdowns with
 * visual CSS comparison bars between Total Paid and Total Consumed/Share.
 */

import type { MemberSpendingSummary } from '@/types/analytics';

interface MemberSpendingBarProps {
  memberSpending: MemberSpendingSummary[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
}

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

export default function MemberSpendingBar({
  memberSpending,
  memberNames,
  currentUserId,
}: MemberSpendingBarProps) {
  if (memberSpending.length === 0) {
    return <p className="report-empty-sub">No member spending records found.</p>;
  }

  // Calculate highest amount among paid and share to scale the comparison bars
  const maxAmount = Math.max(
    ...memberSpending.map((m) => Math.max(Number(m.totalPaid), Number(m.totalShare))),
    1
  );

  return (
    <div className="report-member-list">
      {memberSpending.map((member) => {
        const isSelf = currentUserId === member.userId;
        const displayName = memberNames[member.userId] || 'Unknown member';
        const paidPercent = Math.min(100, (Number(member.totalPaid) / maxAmount) * 100);
        const sharePercent = Math.min(100, (Number(member.totalShare) / maxAmount) * 100);

        const net = Number(member.netPosition);
        let badgeClass = 'badge--secondary';
        let netLabel = 'Settled';
        if (net > 0) {
          badgeClass = 'badge--success';
          netLabel = `Gets back ${formatINR(net)}`;
        } else if (net < 0) {
          badgeClass = 'badge--danger';
          netLabel = `Owes ${formatINR(Math.abs(net))}`;
        }

        return (
          <div key={member.userId} className="report-member-card">
            <div className="report-member-card__header">
              <div className="report-member-card__identity">
                <span className="report-member-card__name">
                  {displayName}
                  {isSelf && <span className="badge badge--primary report-member-card__you">You</span>}
                </span>
              </div>
              <span className={`badge ${badgeClass}`}>{netLabel}</span>
            </div>

            <div className="report-member-card__metrics">
              <div className="report-member-card__metric">
                <span className="report-member-card__metric-label">Paid out:</span>
                <strong className="report-member-card__metric-val">
                  {formatINR(Number(member.totalPaid))}
                </strong>
              </div>
              <div className="report-member-card__metric">
                <span className="report-member-card__metric-label">Share / Consumed:</span>
                <strong className="report-member-card__metric-val">
                  {formatINR(Number(member.totalShare))}
                </strong>
              </div>
            </div>

            {/* Visual comparison bars */}
            <div className="report-bar-group" aria-hidden="true">
              <div className="report-bar-track">
                <div
                  className="report-bar-fill report-bar-fill--paid"
                  style={{ width: `${Math.max(paidPercent, 2)}%` }}
                  title={`Paid: ${formatINR(Number(member.totalPaid))}`}
                />
              </div>
              <div className="report-bar-track">
                <div
                  className="report-bar-fill report-bar-fill--share"
                  style={{ width: `${Math.max(sharePercent, 2)}%` }}
                  title={`Share: ${formatINR(Number(member.totalShare))}`}
                />
              </div>
            </div>

            <div className="report-bar-legend">
              <span className="report-legend-item">
                <span className="report-legend-dot report-legend-dot--paid" /> Paid out
              </span>
              <span className="report-legend-item">
                <span className="report-legend-dot report-legend-dot--share" /> Share consumed
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
