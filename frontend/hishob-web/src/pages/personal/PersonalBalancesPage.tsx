/**
 * PersonalBalancesPage — comprehensive personal balances, simplification, and settlements view.
 *
 * Route: /balances
 *
 * Features:
 *   - Authoritative Personal Balance Summary: Derived strictly from current user's balance entry
 *   - Friend Balance Cards: Name, avatar/initial, clear debt direction, metadata, and direct Settle Up
 *   - Automatic Debt Simplification: Suggestions fetched automatically on page load
 *   - Settle Up action: Debtor-only settlement flow via ConfirmPersonalSettlementModal
 *   - Settlement History tab via GET /api/personal-settlements/history
 *   - Strict safe name resolution (no raw UUIDs, truncated IDs, or fake names)
 *   - Clean empty, loading, error, and settled states
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
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

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function resolveMemberName(
  userId: string | undefined | null,
  currentUserId: string | undefined | null,
  friendsList: Friend[]
): string {
  if (!userId) return 'Unknown member';
  if (currentUserId && userId === currentUserId) {
    return 'You';
  }
  const friend = friendsList.find((f) => f.userId === userId);
  if (friend) {
    if (friend.name && friend.name.trim() && !UUID_REGEX.test(friend.name.trim())) {
      return friend.name.trim();
    }
    if (friend.email && friend.email.trim()) {
      return friend.email.trim();
    }
  }
  return 'Unknown member';
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
      const name = (f.name && f.name.trim() && !UUID_REGEX.test(f.name.trim()))
        ? f.name.trim()
        : (f.email && f.email.trim() ? f.email.trim() : 'Unknown member');
      map[f.userId] = name;
    });
    return map;
  }, [user, friends]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [balanceData, friendsData, historyData, simplifyData] = await Promise.all([
        getPersonalBalances(),
        getFriends().catch(() => [] as Friend[]),
        getPersonalSettlementHistory().catch(() => [] as PersonalSettlementHistoryResponse[]),
        getSimplification().catch(() => ({ suggestions: [] })),
      ]);

      setBalances(balanceData.balances || []);
      setFriends(friendsData);
      setSettlementHistory(historyData || []);
      setSuggestions(simplifyData.suggestions || []);
      setHasLoadedSimplification(true);
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

  // Authoritative Personal Balance Summary derived from current user's balance entry
  const { youOwe, youAreOwed, netBalance } = useMemo(() => {
    const myEntry = balances.find((b) => b.userId === user?.id);
    const myNet = myEntry?.netBalance ?? 0;

    return {
      youOwe: myNet < 0 ? Math.abs(myNet) : 0,
      youAreOwed: myNet > 0 ? myNet : 0,
      netBalance: myNet,
    };
  }, [balances, user?.id]);

  // Friend-wise balance list (all participants except current user)
  const friendBalances = useMemo(() => {
    return balances.filter((b) => b.userId !== user?.id);
  }, [balances, user?.id]);

  // Direct Settle Up handler from friend card
  const handleSettleFriend = (friendBalance: PersonalBalanceResponse) => {
    if (!user?.id || !friendBalance.userId || friendBalance.netBalance <= 0) return;
    setSelectedSuggestion({
      fromUserId: user.id,
      toUserId: friendBalance.userId,
      amount: friendBalance.netBalance,
    });
  };

  const handleSettlementSuccess = () => {
    void loadData();
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
            Overview of who owes whom across all your 1-on-1 personal expenses and settlements.
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
            {isSimplifying ? 'Simplifying…' : 'Refresh Simplification'}
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
                <span className={`balance-card__amount ${youOwe > 0 ? 'balance-card__amount--owe' : ''}`}>
                  ₹{formatAmount(youOwe)}
                </span>
                <span className="balance-card__hint">Total amount you need to pay back</span>
              </div>
              <div className="balance-card">
                <span className="balance-card__label">You are owed</span>
                <span className={`balance-card__amount ${youAreOwed > 0 ? 'balance-card__amount--gets' : ''}`}>
                  ₹{formatAmount(youAreOwed)}
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
                <span className="balance-card__hint">
                  {netBalance === 0 ? 'All personal balances are settled up' : 'Your overall financial standing'}
                </span>
              </div>
            </div>
          </section>

          {/* Friend Balance Cards Section */}
          <section className="group-details-section" aria-labelledby="friend-balances-heading" style={{ marginTop: '1.5rem' }}>
            <div className="page-header page-header--section">
              <div className="page-header__title">
                <h3 id="friend-balances-heading" className="page-header__heading page-header__heading--sm">
                  Friend Balances
                </h3>
                <p className="page-header__sub">Individual standing with each friend</p>
              </div>
            </div>

            {friendBalances.length === 0 ? (
              <EmptyState
                icon="🤝"
                heading="No personal balances"
                description="You have no outstanding balances with your friends."
              />
            ) : (
              <div className="friend-balance-cards-grid">
                {friendBalances.map((item) => {
                  const friendName = resolveMemberName(item.userId, user?.id, friends);
                  const initial = (friendName.charAt(0) || '?').toUpperCase();
                  
                  // Pairwise standing with this friend, straight from GET /api/personal-balances:
                  // item.netBalance > 0 means the current user owes the friend exactly that amount,
                  // which is also the amount the settlement endpoint accepts for them.
                  // item.netBalance < 0 means the friend owes the current user that amount.
                  // item.netBalance === 0 means the two of them are settled up.
                  const userOwesFriend = item.netBalance > 0;
                  const friendOwesUser = item.netBalance < 0;
                  const isSettled = item.netBalance === 0;

                  return (
                    <div
                      key={item.userId}
                      className={`friend-balance-card ${
                        userOwesFriend
                          ? 'friend-balance-card--owes'
                          : friendOwesUser
                          ? 'friend-balance-card--gets'
                          : 'friend-balance-card--settled'
                      }`}
                    >
                      {/* Card Top: Avatar, Name & Direction Status */}
                      <div className="friend-balance-card__header">
                        <div className="friend-balance-card__user">
                          <div className="friend-balance-card__avatar" aria-hidden="true">
                            {initial}
                          </div>
                          <div className="friend-balance-card__info">
                            <h4 className="friend-balance-card__name">{friendName}</h4>
                            <div className="friend-balance-card__direction">
                              {userOwesFriend && (
                                <span className="friend-balance-card__direction-text friend-balance-card__direction-text--owes">
                                  You owe ₹{formatAmount(item.netBalance)}
                                </span>
                              )}
                              {friendOwesUser && (
                                <span className="friend-balance-card__direction-text friend-balance-card__direction-text--gets">
                                  {friendName} owes you ₹{formatAmount(item.netBalance)}
                                </span>
                              )}
                              {isSettled && (
                                <span className="friend-balance-card__direction-text friend-balance-card__direction-text--settled">
                                  Settled up
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="friend-balance-card__badge-wrapper">
                          {userOwesFriend && (
                            <span className="badge badge--danger">You owe</span>
                          )}
                          {friendOwesUser && (
                            <span className="badge badge--success">Owes you</span>
                          )}
                          {isSettled && (
                            <span className="badge badge--muted">Settled</span>
                          )}
                        </div>
                      </div>

                      {/* Card Meta: Total Paid & Total Share */}
                      <div className="friend-balance-card__meta-grid">
                        <div className="friend-balance-card__meta-item">
                          <span className="friend-balance-card__meta-label">Paid for you</span>
                          <span className="friend-balance-card__meta-value">₹{formatAmount(item.totalPaid)}</span>
                        </div>
                        <div className="friend-balance-card__meta-item">
                          <span className="friend-balance-card__meta-label">You paid</span>
                          <span className="friend-balance-card__meta-value">₹{formatAmount(item.totalShare)}</span>
                        </div>
                      </div>

                      {/* Card Actions: Settle Up (Debtor only) & View Details */}
                      <div className="friend-balance-card__actions">
                        <Link
                          to={`/friends/${item.userId}`}
                          className="btn btn--ghost btn--sm friend-balance-card__link"
                        >
                          View Details
                        </Link>
                        {userOwesFriend && (
                          <button
                            type="button"
                            className="btn btn--primary btn--sm"
                            onClick={() => handleSettleFriend(item)}
                          >
                            Settle Up
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {/* Debt Simplification Suggestions Section */}
          {hasLoadedSimplification && (
            <section className="group-details-section" aria-labelledby="simplification-heading" style={{ marginTop: '2rem' }}>
              <div className="page-header page-header--section">
                <div className="page-header__title">
                  <h3 id="simplification-heading" className="page-header__heading page-header__heading--sm">
                    Debt Simplification
                  </h3>
                  <p className="page-header__sub">
                    Recommended payments that reduce the total number of transactions needed to settle all debts.
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
                  ✓ All personal debts are settled. No simplified transactions are needed.
                </div>
              ) : (
                <div className="settlement-suggestions-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {suggestions.map((suggestion, idx) => {
                    const fromIsMe = user ? suggestion.fromUserId === user.id : false;
                    const toIsMe = user ? suggestion.toUserId === user.id : false;

                    const fromName = resolveMemberName(suggestion.fromUserId, user?.id, friends);
                    const toName = resolveMemberName(suggestion.toUserId, user?.id, friends);

                    return (
                      <div
                        key={`${suggestion.fromUserId}-${suggestion.toUserId}-${idx}`}
                        className={`settlement-suggestion-card ${fromIsMe ? 'settlement-suggestion-card--owes' : ''}`}
                      >
                        <div className="settlement-suggestion-card__main">
                          <div className="settlement-suggestion-card__direction">
                            {fromIsMe ? (
                              <span>
                                <strong>You</strong> pay <strong>{toName}</strong>
                              </span>
                            ) : toIsMe ? (
                              <span>
                                <strong>{fromName}</strong> pays <strong>you</strong>
                              </span>
                            ) : (
                              <span>
                                <strong>{fromName}</strong> pays <strong>{toName}</strong>
                              </span>
                            )}
                          </div>
                          <span className="settlement-suggestion-card__amount">
                            ₹{formatAmount(suggestion.amount)}
                          </span>
                        </div>

                        {/* Only show Settle Up if currentUser is debtor (fromUserId) */}
                        <div className="settlement-suggestion-card__action">
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
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          )}
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
          toUserName={resolveMemberName(selectedSuggestion.toUserId, user?.id, friends)}
          onSuccess={handleSettlementSuccess}
        />
      )}
    </div>
  );
}

