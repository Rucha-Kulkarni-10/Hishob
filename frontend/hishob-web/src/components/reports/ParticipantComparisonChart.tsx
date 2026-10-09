/**
 * ParticipantComparisonChart — accessible comparative visual breakdown for group members or personal friends.
 *
 * Supports two explicit typed modes without mixing domain boundaries:
 * 1. GROUP mode: compares totalPaid vs totalShare per member with net position badges.
 * 2. PERSONAL mode: compares userShare vs friendShare per friend with expense counts.
 *
 * Safety & UX:
 * - Safe name resolution (never displays raw UUIDs)
 * - Proportional comparative horizontal progress bars
 * - Empty & 0-value state handling
 * - Accessible keyboard focus and ARIA metadata
 */

import { useMemo, useState } from 'react';
import type { MemberSpendingSummary, FriendSpendingSummary } from '@/types/analytics';

export interface GroupComparisonProps {
  mode: 'GROUP';
  items?: MemberSpendingSummary[];
  memberNames?: Record<string, string>;
  currentUserId?: string | null;
  title?: string;
  emptyMessage?: string;
}

export interface PersonalComparisonProps {
  mode: 'PERSONAL';
  items?: FriendSpendingSummary[];
  friendNames?: Record<string, string>;
  currentUserId?: string | null;
  title?: string;
  emptyMessage?: string;
}

export type ParticipantComparisonChartProps = GroupComparisonProps | PersonalComparisonProps;

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function formatINR(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(val);
}

function resolveSafeName(
  rawId: string,
  rawName: string | undefined | null,
  currentUserId: string | null | undefined
): string {
  if (currentUserId && rawId.toLowerCase() === currentUserId.toLowerCase()) {
    return 'You';
  }
  if (rawName && rawName.trim() && !UUID_REGEX.test(rawName.trim())) {
    return rawName.trim();
  }
  return 'Unknown member';
}

interface NormalizedParticipantRow {
  id: string;
  name: string;
  isSelf: boolean;
  metric1Label: string;
  metric1Value: number;
  metric2Label: string;
  metric2Value: number;
  bar1Percent: number;
  bar2Percent: number;
  badgeLabel: string;
  badgeClass: string;
  subText?: string;
}

export default function ParticipantComparisonChart(props: ParticipantComparisonChartProps) {
  const { title = props.mode === 'GROUP' ? 'Member Spending Comparison' : 'Spending by Friend', emptyMessage = 'No participant data available.' } = props;
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  const rows: NormalizedParticipantRow[] = useMemo(() => {
    if (props.mode === 'GROUP') {
      const list = props.items || [];
      if (list.length === 0) return [];

      const maxVal = Math.max(
        ...list.map((m) => Math.max(Number(m.totalPaid) || 0, Number(m.totalShare) || 0)),
        1
      );

      return list.map((m) => {
        const isSelf = Boolean(props.currentUserId && m.userId.toLowerCase() === props.currentUserId.toLowerCase());
        const resolved = props.memberNames?.[m.userId];
        const name = isSelf ? 'You' : resolveSafeName(m.userId, resolved, props.currentUserId);

        const paid = Number(m.totalPaid) || 0;
        const share = Number(m.totalShare) || 0;
        const net = Number(m.netPosition) || 0;

        let badgeClass = 'badge--neutral';
        let badgeLabel = 'Settled';
        if (net > 0.009) {
          badgeClass = 'badge--success';
          badgeLabel = `Gets back ${formatINR(net)}`;
        } else if (net < -0.009) {
          badgeClass = 'badge--danger';
          badgeLabel = `Owes ${formatINR(Math.abs(net))}`;
        }

        return {
          id: m.userId,
          name,
          isSelf,
          metric1Label: 'Paid out',
          metric1Value: paid,
          metric2Label: 'Share / Consumed',
          metric2Value: share,
          bar1Percent: Math.max(2, (paid / maxVal) * 100),
          bar2Percent: Math.max(2, (share / maxVal) * 100),
          badgeLabel,
          badgeClass,
        };
      });
    } else {
      // PERSONAL mode
      const list = props.items || [];
      if (list.length === 0) return [];

      const maxVal = Math.max(
        ...list.map((f) => Math.max(Number(f.userShare) || 0, Number(f.friendShare) || 0, Number(f.totalSpending) || 0)),
        1
      );

      return list.map((f) => {
        const resolvedName = props.friendNames?.[f.friendUserId];
        const name = resolveSafeName(f.friendUserId, resolvedName, props.currentUserId);
        const userShare = Number(f.userShare) || 0;
        const friendShare = Number(f.friendShare) || 0;
        const total = Number(f.totalSpending) || 0;

        return {
          id: f.friendUserId,
          name,
          isSelf: false,
          metric1Label: 'Your share',
          metric1Value: userShare,
          metric2Label: `${name}’s share`,
          metric2Value: friendShare,
          bar1Percent: Math.max(2, (userShare / maxVal) * 100),
          bar2Percent: Math.max(2, (friendShare / maxVal) * 100),
          badgeLabel: `Total: ${formatINR(total)}`,
          badgeClass: 'badge--primary',
          subText: `${f.expenseCount} shared expense${f.expenseCount !== 1 ? 's' : ''}`,
        };
      });
    }
  }, [props]);

  if (rows.length === 0) {
    return (
      <div className="participant-comparison participant-comparison--empty" role="region" aria-label={title}>
        <div className="participant-comparison__header">
          <h4 className="participant-comparison__title">{title}</h4>
        </div>
        <p className="participant-comparison__empty-text">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div
      className="participant-comparison"
      role="region"
      aria-label={`${title}. Displaying comparison for ${rows.length} participants.`}
    >
      <div className="participant-comparison__header">
        <div>
          <h4 className="participant-comparison__title">{title}</h4>
          <p className="participant-comparison__sub">
            {props.mode === 'GROUP'
              ? 'Comparison of total paid vs share consumed by each member.'
              : 'Direct split comparison across your shared friend expenses.'}
          </p>
        </div>
      </div>

      <div className="participant-comparison__legend-header" aria-hidden="true">
        <span className="participant-comparison__legend-pill participant-comparison__legend-pill--primary">
          <span className="participant-comparison__legend-dot participant-comparison__legend-dot--primary" />
          {props.mode === 'GROUP' ? 'Paid out' : 'Your share'}
        </span>
        <span className="participant-comparison__legend-pill participant-comparison__legend-pill--secondary">
          <span className="participant-comparison__legend-dot participant-comparison__legend-dot--secondary" />
          {props.mode === 'GROUP' ? 'Share consumed' : 'Friend’s share'}
        </span>
      </div>

      <div className="participant-comparison__list">
        {rows.map((row) => {
          const isHovered = hoveredId === row.id;

          return (
            <div
              key={row.id}
              className={`participant-comparison__card ${
                isHovered ? 'participant-comparison__card--hovered' : ''
              }`}
              onMouseEnter={() => setHoveredId(row.id)}
              onMouseLeave={() => setHoveredId(null)}
              tabIndex={0}
              role="article"
              aria-label={`${row.name}: ${row.metric1Label} ${formatINR(row.metric1Value)}, ${row.metric2Label} ${formatINR(row.metric2Value)}. ${row.badgeLabel}.`}
            >
              <div className="participant-comparison__card-top">
                <div className="participant-comparison__identity">
                  <span className="participant-comparison__name" title={row.name}>
                    {row.name}
                  </span>
                  {row.isSelf && <span className="badge badge--primary participant-comparison__you">You</span>}
                  {row.subText && <span className="participant-comparison__subtext">{row.subText}</span>}
                </div>
                <span className={`badge ${row.badgeClass}`}>{row.badgeLabel}</span>
              </div>

              {/* Metrics values */}
              <div className="participant-comparison__metrics-grid">
                <div className="participant-comparison__metric">
                  <span className="participant-comparison__metric-label">{row.metric1Label}</span>
                  <strong className="participant-comparison__metric-value participant-comparison__metric-value--primary">
                    {formatINR(row.metric1Value)}
                  </strong>
                </div>
                <div className="participant-comparison__metric">
                  <span className="participant-comparison__metric-label">{row.metric2Label}</span>
                  <strong className="participant-comparison__metric-value participant-comparison__metric-value--secondary">
                    {formatINR(row.metric2Value)}
                  </strong>
                </div>
              </div>

              {/* Visual Comparative Bars */}
              <div className="participant-comparison__bar-group" aria-hidden="true">
                <div className="participant-comparison__track">
                  <div
                    className="participant-comparison__fill participant-comparison__fill--primary"
                    style={{ width: `${row.bar1Percent}%` }}
                    title={`${row.metric1Label}: ${formatINR(row.metric1Value)}`}
                  />
                </div>
                <div className="participant-comparison__track">
                  <div
                    className="participant-comparison__fill participant-comparison__fill--secondary"
                    style={{ width: `${row.bar2Percent}%` }}
                    title={`${row.metric2Label}: ${formatINR(row.metric2Value)}`}
                  />
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
