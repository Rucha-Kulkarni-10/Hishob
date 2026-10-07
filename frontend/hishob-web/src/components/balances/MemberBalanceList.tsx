/**
 * MemberBalanceList — shows every group member's balance in a table.
 *
 * For each member:
 *   - Name (from memberNames map, never raw UUID in normal rows)
 *   - "(You)" tag for the current user
 *   - Total paid
 *   - Share
 *   - Net balance with sign
 *   - Status badge: GETS / OWES / SETTLED
 *
 * Sorted: current user first, then by netBalance descending (biggest GETS first).
 */

import type { MemberBalance } from '@/types/balances';

interface MemberBalanceListProps {
  balances: MemberBalance[];
  memberNames: Record<string, string>;
  currentUserId: string | null;
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function signedAmount(amount: number): string {
  const abs = Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return `${amount >= 0 ? '+' : '−'}₹${abs}`;
}

export default function MemberBalanceList({
  balances,
  memberNames,
  currentUserId,
}: MemberBalanceListProps) {
  if (balances.length === 0) {
    return (
      <p className="members-empty">No balance data available.</p>
    );
  }

  // Sort: current user first, then by netBalance descending
  const sorted = [...balances].sort((a, b) => {
    const aIsMe = a.userId === currentUserId ? -1 : 0;
    const bIsMe = b.userId === currentUserId ? -1 : 0;
    if (aIsMe !== bIsMe) return aIsMe - bIsMe;
    return b.netBalance - a.netBalance;
  });

  return (
    <div className="members">
      <table className="members-table balance-table">
        <thead>
          <tr>
            <th>Member</th>
            <th className="balance-table__num">Paid</th>
            <th className="balance-table__num">Share</th>
            <th className="balance-table__num">Net Balance</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((b) => {
            const isMe = b.userId === currentUserId;
            const name = memberNames[b.userId] || 'Unknown member';
            const statusMod =
              b.status === 'GETS' ? 'gets' : b.status === 'OWES' ? 'owes' : 'settled';

            return (
              <tr
                key={b.userId}
                className={isMe ? 'members-table__row--self' : undefined}
              >
                <td>
                  <span className="balance-table__name">{name}</span>
                  {isMe && (
                    <span className="members-table__self-tag">(You)</span>
                  )}
                </td>
                <td className="balance-table__num">₹{formatAmount(b.totalPaid)}</td>
                <td className="balance-table__num">₹{formatAmount(b.totalShare)}</td>
                <td className={`balance-table__num balance-table__net--${statusMod}`}>
                  {signedAmount(b.netBalance)}
                </td>
                <td>
                  <span className={`badge balance-badge--${statusMod}`}>
                    {b.status === 'GETS' ? 'Gets' : b.status === 'OWES' ? 'Owes' : 'Settled'}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
