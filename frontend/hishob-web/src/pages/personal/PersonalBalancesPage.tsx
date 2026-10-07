/**
 * PersonalBalancesPage — comprehensive personal balances, simplification, and settlements view.
 *
 * Route: /balances
 *
 * Features:
 *   - Overall Personal Balance Summary: You owe, You are owed, Net balance
 *   - Per-Friend Balances Breakdown (Total Paid, Total Share, Net, Status)
 *   - Debt Simplification suggestions via GET /api/personal-balances/simplify
 *   - Settle Up action (only when current user is debtor) via ConfirmPersonalSettlementModal
 *   - Settlement History tab/view via GET /api/personal-settlements/history
 *   - All UUIDs converted to user/friend names
 *   - Full loading, empty, and error handling
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { getPersonalBalances, getSimplification } from '@/services/personalBalanceService';
import { getPersonalSettlementHistory } from '@/services/personalSettlementService';
import { getFriends } from '@/services/friendService';
import type {
  PersonalBalanceResponse,
  PersonalDebtSimplificationSuggestion,
} from '@/types/personalBalances';
import type { PersonalSettlementHistoryResponse } from '@/types/personalSettlements';
import type { Friend } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import ConfirmPersonalSettlementModal from '@/components/personal/ConfirmPersonalSettlementModal';
import PersonalSettlementHistory from '@/components/personal/PersonalSettlementHistory';

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function PersonalBalancesPage() {
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState<'balances' | 'settlements'>('balances');
  const [balances, setBalances] = useState<PersonalBalanceResponse[]>([]);
  const [suggestions, setSuggestions] = useState<PersonalDebtSimplificationSuggestion[]>([]);
  const [settlementHistory, setSettlementHistory] = useState<PersonalSettlementHistoryResponse[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [isSimplifying, setIsSimplifying] = useState(false);
  const [simplifyError, setSimplifyError] = useState<string | null>(null);
  const [hasLoadedSimplification, setHasLoadedSimplification] = useState(false);

  // Settlement modal state
  const [selectedSuggestion, setSelectedSuggestion] = useState<PersonalDebtSimplificationSuggestion | null>(null);

  // Map of userId -> display name
  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    if (user) {
      map[user.id] = user.name ? `${user.name} (You)` : 'You';
    }
    friends.forEach((f) => {
      map[f.userId] = f.name || f.email || 'Friend';
    });
    return map;
  }, [user, friends]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [balanceData, friendsData, historyData] = await Promise.all([
        getPersonalBalances(),
        getFriends().catch(() => [] as Friend[]),
        getPersonalSettlementHistory().catch(() => [] as PersonalSettlementHistoryResponse[]),
      ]);

      setBalances(balanceData.balances || []);
      setFriends(friendsData);
      setSettlementHistory(historyData || []);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadSimplification = useCallback(async () => {
    setIsSimplifying(true);
    setSimplifyError(null);
    try {
      const res = await getSimplification();
      setSuggestions(res.suggestions || []);
      setHasLoadedSimplification(true);
    } catch (err) {
      setSimplifyError(getErrorMessage(err));
    } finally {
      setIsSimplifying(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void loadData();
    });
  }, [loadData]);

  // Summary aggregation
  const { totalOwed, totalGets, netBalance } = useMemo(() => {
    let owed = 0;
    let gets = 0;

    balances.forEach((b) => {
      if (b.netBalance < 0) {
        owed += Math.abs(b.netBalance);
      } else if (b.netBalance > 0) {
        gets += b.netBalance;
      }
    });

    return {
      totalOwed: owed,
      totalGets: gets,
      netBalance: gets - owed,
    };
  }, [balances]);

  const handleSettlementSuccess = () => {
    void loadData();
    if (hasLoadedSimplification) {
      void loadSimplification();
    }
    setSelectedSuggestion(null);
  };

  return (
    <div className="group-details-page">
      {/* Page Header */}
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <div className="group-details-title-row">
            <h2 className="page-header__heading">Personal Balances</h2>
            <span className="badge badge--primary">Personal</span>
          </div>
          <p className="page-header__sub">
            Overview of who owes whom across all your personal expenses and settlements.
          </p>
        </div>

        <div className="group-details-actions">
          <button
            type="button"
            className="btn btn--secondary"
            onClick={() => void loadSimplification()}
            disabled={isSimplifying}
            aria-busy={isSimplifying}
          >
            {isSimplifying ? 'Simplifying…' : 'Simplify Debts'}
          </button>
        </div>
      </header>

      {/* Tabs navigation */}
      <div className="tab-nav" role="tablist" style={{ marginBottom: '1.5rem', display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--color-border)', paddingBottom: '0.5rem' }}>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'balances'}
          className={`btn btn--sm ${activeTab === 'balances' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setActiveTab('balances')}
        >
          Balances & Simplification
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'settlements'}
          className={`btn btn--sm ${activeTab === 'settlements' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setActiveTab('settlements')}
        >
          Settlement History ({settlementHistory.length})
        </button>
      </div>

      {isLoading && (
        <LoadingState label="Loading personal balances…" height="lg" />
      )}

      {!isLoading && error && (
        <div className="alert alert--error" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void loadData()}
            style={{ marginLeft: '1rem' }}
          >
            Retry
          </button>
        </div>
      )}

      {!isLoading && !error && activeTab === 'balances' && (
        <>
          {/* Summary Cards */}
          <section className="dashboard-section" aria-label="Balance Summary">
            <div className="balance-cards-grid">
              <div className="balance-card">
                <span className="balance-card__label">You owe</span>
                <span className={`balance-card__amount ${totalOwed > 0 ? 'balance-card__amount--owe' : ''}`}>
                  ₹{formatAmount(totalOwed)}
                </span>
                <span className="balance-card__hint">Total amount you need to pay back</span>
              </div>
              <div className="balance-card">
                <span className="balance-card__label">You are owed</span>
                <span className={`balance-card__amount ${totalGets > 0 ? 'balance-card__amount--gets' : ''}`}>
                  ₹{formatAmount(totalGets)}
                </span>
                <span className="balance-card__hint">Total amount friends need to pay you</span>
              </div>
              <div className="balance-card">
                <span className="balance-card__label">Net Balance</span>
                <span
                  className={`balance-card__amount ${
                    netBalance > 0
                      ? 'balance-card__amount--gets'
                      : netBalance < 0
                      ? 'balance-card__amount--owe'
                      : ''
                  }`}
                >
                  {netBalance > 0 ? `+₹${formatAmount(netBalance)}` : netBalance < 0 ? `-₹${formatAmount(netBalance)}` : '₹0.00'}
                </span>
                <span className="balance-card__hint">Your overall financial standing</span>
              </div>
            </div>
          </section>

          {/* Debt Simplification Suggestions */}
          {hasLoadedSimplification && (
            <section className="group-details-section" aria-labelledby="simplification-heading" style={{ marginTop: '1.5rem' }}>
              <div className="page-header page-header--section">
                <div className="page-header__title">
                  <h3 id="simplification-heading" className="page-header__heading page-header__heading--sm">
                    Debt Simplification Suggestions
                  </h3>
                  <p className="page-header__sub">
                    Optimized settlement paths to settle all debts with minimum transactions.
                  </p>
                </div>
              </div>

              {simplifyError && (
                <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
                  {simplifyError}
                </div>
              )}

              {suggestions.length === 0 ? (
                <div className="alert alert--success" role="status">
                  ✓ You're all settled up! No simplified transactions are needed.
                </div>
              ) : (
                <div className="settlement-suggestions-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {suggestions.map((suggestion, idx) => {
                    const fromIsMe = user ? suggestion.fromUserId === user.id : false;
                    const toIsMe = user ? suggestion.toUserId === user.id : false;

                    const fromName = fromIsMe ? 'You' : memberNames[suggestion.fromUserId] || 'Friend';
                    const toName = toIsMe ? 'You' : memberNames[suggestion.toUserId] || 'Friend';

                    return (
                      <div
                        key={`${suggestion.fromUserId}-${suggestion.toUserId}-${idx}`}
                        className={`settlement-suggestion-card ${fromIsMe ? 'settlement-suggestion-card--owes' : ''}`}
                        style={{
                          background: 'var(--color-surface)',
                          border: '1px solid var(--color-border)',
                          borderRadius: 'var(--radius-lg)',
                          padding: '1rem 1.25rem',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          flexWrap: 'wrap',
                          gap: '1rem',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                          <span style={{ fontWeight: 600 }}>{fromName}</span>
                          <span style={{ color: 'var(--color-text-muted)' }} aria-hidden="true">→</span>
                          <span style={{ fontWeight: 600 }}>{toName}</span>
                          <span style={{ fontWeight: 700, color: 'var(--color-primary)', marginLeft: '0.5rem' }}>
                            ₹{formatAmount(suggestion.amount)}
                          </span>
                        </div>

                        {/* Only show Settle Up if currentUser === fromUserId */}
                        {fromIsMe ? (
                          <button
                            type="button"
                            className="btn btn--primary btn--sm"
                            onClick={() => setSelectedSuggestion(suggestion)}
                          >
                            Settle Up ₹{formatAmount(suggestion.amount)}
                          </button>
                        ) : (
                          <span className="badge badge--muted">
                            {toIsMe ? 'Waiting for payment' : 'Pending'}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          )}

          {/* People Balances List */}
          <section className="group-details-section" aria-labelledby="people-balances-heading" style={{ marginTop: '1.5rem' }}>
            <div className="page-header page-header--section">
              <div className="page-header__title">
                <h3 id="people-balances-heading" className="page-header__heading page-header__heading--sm">
                  Balances by Person
                </h3>
                <p className="page-header__sub">Individual standing with each participant</p>
              </div>
            </div>

            {balances.length === 0 ? (
              <EmptyState
                icon="🤝"
                heading="No personal balances"
                description="You're all settled up with your friends."
              />
            ) : (
              <div className="members-table-wrapper" style={{ overflowX: 'auto' }}>
                <table className="members-table">
                  <thead>
                    <tr>
                      <th>Person</th>
                      <th>Total Paid</th>
                      <th>Total Share</th>
                      <th>Net Balance</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {balances.map((item) => {
                      const isMe = user && item.userId === user.id;
                      const displayName = memberNames[item.userId] || (isMe ? 'You' : 'Friend');

                      let statusBadge = <span className="badge badge--muted">Settled</span>;
                      if (item.status === 'GETS') {
                        statusBadge = <span className="badge badge--success">Gets ₹{formatAmount(item.netBalance)}</span>;
                      } else if (item.status === 'OWES') {
                        statusBadge = <span className="badge badge--danger">Owes ₹{formatAmount(item.netBalance)}</span>;
                      }

                      return (
                        <tr key={item.userId}>
                          <td>
                            <strong>{displayName}</strong>
                          </td>
                          <td>₹{formatAmount(item.totalPaid)}</td>
                          <td>₹{formatAmount(item.totalShare)}</td>
                          <td style={{ fontWeight: 600 }}>
                            {item.netBalance > 0 ? `+₹${formatAmount(item.netBalance)}` : item.netBalance < 0 ? `-₹${formatAmount(item.netBalance)}` : '₹0.00'}
                          </td>
                          <td>{statusBadge}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}

      {!isLoading && !error && activeTab === 'settlements' && (
        <section className="group-details-section" aria-label="Settlement History">
          <PersonalSettlementHistory
            settlements={settlementHistory}
            memberNames={memberNames}
            currentUserId={user?.id ?? null}
          />
        </section>
      )}

      {/* Settle Up Confirmation Modal */}
      {selectedSuggestion && (
        <ConfirmPersonalSettlementModal
          isOpen={!!selectedSuggestion}
          onClose={() => setSelectedSuggestion(null)}
          suggestion={selectedSuggestion}
          toUserName={memberNames[selectedSuggestion.toUserId] || 'Friend'}
          onSuccess={handleSettlementSuccess}
        />
      )}
    </div>
  );
}
