/**
 * GroupBalancesPage — /groups/:id/balances
 *
 * Loads in parallel:
 *   - GET /api/groups/:id          (group name for breadcrumb)
 *   - GET /api/groups/:id/members  (for name mapping)
 *   - GET /api/groups/:id/balances (balance data)
 *   - GET /api/groups/:id/settlements (simplified debt suggestions)
 *
 * Sections:
 *   - BalanceSummary   (current user's own balance, prominent)
 *   - MemberBalanceList (all members)
 *   - SettlementSuggestions (who pays whom)
 *
 * Error handling:
 *   - 403 → "not a member" screen
 *   - 404 → "group not found" screen
 *   - 503/network → error with retry
 *   - Partial failure (balances vs suggestions) shown per-section
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGroupDetail, listMembers } from '@/services/groupService';
import { getGroupBalances, getSettlementSuggestions } from '@/services/balanceService';
import { recordSettlement, getSettlementHistory } from '@/services/settlementService';
import type { GroupResponse, MemberResponse } from '@/types/groups';
import type { MemberBalance, SettlementSuggestion } from '@/types/balances';
import type { SettlementHistoryItem } from '@/types/settlements';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage, isAxiosError } from '@/utils/errorUtils';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import BalanceSummary from '@/components/balances/BalanceSummary';
import MemberBalanceList from '@/components/balances/MemberBalanceList';
import SettlementSuggestions from '@/components/balances/SettlementSuggestions';
import ConfirmSettlementModal from '@/components/settlements/ConfirmSettlementModal';
import SettlementHistory from '@/components/settlements/SettlementHistory';

export default function GroupBalancesPage() {
  const { id: groupId } = useParams<{ id: string }>();
  const { user } = useAuth();

  // ── Page-level state ───────────────────────────────────────────────────────
  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [balances, setBalances] = useState<MemberBalance[]>([]);
  const [suggestions, setSuggestions] = useState<SettlementSuggestion[]>([]);
  const [history, setHistory] = useState<SettlementHistoryItem[]>([]);

  const [pageLoading, setPageLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [is404, setIs404] = useState(false);
  const [is403, setIs403] = useState(false);

  const [balanceLoading, setBalanceLoading] = useState(false);
  const [balanceError, setBalanceError] = useState<string | null>(null);

  const [suggestLoading, setSuggestLoading] = useState(false);
  const [suggestError, setSuggestError] = useState<string | null>(null);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // Tab navigation state
  const [activeTab, setActiveTab] = useState<'suggestions' | 'history'>('suggestions');

  // Settlement modal state
  const [settlementToConfirm, setSettlementToConfirm] = useState<SettlementSuggestion | null>(null);
  const [isSubmittingSettlement, setIsSubmittingSettlement] = useState(false);

  // ── Member name map ────────────────────────────────────────────────────────
  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const m of members) {
      map[m.userId] = m.name || (user && m.userId === user.id ? user.name : 'Unknown member');
    }
    return map;
  }, [members, user]);

  // ── Loaders ────────────────────────────────────────────────────────────────
  const loadBalances = useCallback(async (gId: string) => {
    await Promise.resolve();
    setBalanceLoading(true);
    setBalanceError(null);
    try {
      const data = await getGroupBalances(gId);
      setBalances(data.balances);
    } catch (err) {
      setBalanceError(getErrorMessage(err));
    } finally {
      setBalanceLoading(false);
    }
  }, []);

  const loadSuggestions = useCallback(async (gId: string) => {
    await Promise.resolve();
    setSuggestLoading(true);
    setSuggestError(null);
    try {
      const data = await getSettlementSuggestions(gId);
      setSuggestions(data);
    } catch (err) {
      setSuggestError(getErrorMessage(err));
    } finally {
      setSuggestLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async (gId: string) => {
    await Promise.resolve();
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const data = await getSettlementHistory(gId);
      setHistory(data);
    } catch (err) {
      setHistoryError(getErrorMessage(err));
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const loadPage = useCallback(async (gId: string) => {
    await Promise.resolve();
    setPageLoading(true);
    setPageError(null);
    setIs404(false);
    setIs403(false);

    try {
      const [g, ms] = await Promise.all([getGroupDetail(gId), listMembers(gId)]);
      setGroup(g);
      setMembers(ms);
    } catch (err: unknown) {
      if (isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 404) { setIs404(true); return; }
        if (status === 403) { setIs403(true); return; }
      }
      setPageError(getErrorMessage(err));
      return;
    } finally {
      setPageLoading(false);
    }

    // Load balances + suggestions in parallel (independent of each other)
    void loadBalances(gId);
    void loadSuggestions(gId);
    void loadHistory(gId);
  }, [loadBalances, loadSuggestions, loadHistory]);

  useEffect(() => {
    if (!groupId) return;
    queueMicrotask(() => {
      void loadPage(groupId);
    });
  }, [groupId, loadPage]);

  const handleRefresh = () => {
    if (!groupId) return;
    void loadBalances(groupId);
    void loadSuggestions(groupId);
    void loadHistory(groupId);
  };

  const handleSettleUp = (suggestion: SettlementSuggestion) => {
    setSettlementToConfirm(suggestion);
  };

  const handleConfirmSettlement = async (amount: number, notes?: string) => {
    if (!groupId || !settlementToConfirm || isSubmittingSettlement) return;

    if (settlementToConfirm.fromUserId === settlementToConfirm.toUserId) {
      alert('Cannot settle with yourself. Payer and recipient cannot be the same user.');
      return;
    }

    setIsSubmittingSettlement(true);
    try {
      await recordSettlement(groupId, {
        toUserId: settlementToConfirm.toUserId,
        amount,
        notes,
      });

      // Close modal
      setSettlementToConfirm(null);

      // Refresh all data
      await Promise.all([
        loadBalances(groupId),
        loadSuggestions(groupId),
        loadHistory(groupId),
      ]);

      // Switch to history tab to show the recorded settlement
      setActiveTab('history');
    } catch (err) {
      alert('Failed to record settlement: ' + getErrorMessage(err));
    } finally {
      setIsSubmittingSettlement(false);
    }
  };

  const handleCancelSettlement = () => {
    setSettlementToConfirm(null);
  };

  // ── Render guards ──────────────────────────────────────────────────────────
  if (pageLoading) {
    return <LoadingState label="Loading balances…" height="lg" />;
  }

  if (is404) {
    return (
      <EmptyState
        icon="🔍"
        heading="Group not found"
        description="This group may have been deleted or you followed a bad link."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (is403) {
    return (
      <EmptyState
        icon="🔒"
        heading="You can't view this group"
        description="You must be a member of this group to see its balances."
        action={<Link to="/groups" className="btn btn--primary">Back to My Groups</Link>}
      />
    );
  }

  if (pageError) {
    return (
      <div className="alert alert--error groups-page__alert" role="alert">
        <span>{pageError}</span>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => groupId && loadPage(groupId)}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!group) return null;

  const allSettled =
    !balanceLoading &&
    !balanceError &&
    balances.length > 0 &&
    balances.every((b) => b.status === 'SETTLED');

  // ── Main render ────────────────────────────────────────────────────────────
  return (
    <div className="group-details-page">
      {/* ── Page header ── */}
      <header className="page-header">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/groups">My Groups</Link>
            <span aria-hidden="true"> / </span>
            <Link to={`/groups/${groupId}`}>{group.name}</Link>
            <span aria-hidden="true"> / </span>
            <span>Balances</span>
          </nav>
          <div className="group-details-title-row">
            <h2 className="page-header__heading">Balances</h2>
          </div>
          <p className="group-details-meta">{group.name}</p>
        </div>
        <button
          type="button"
          className="btn btn--ghost"
          onClick={handleRefresh}
          disabled={balanceLoading || suggestLoading}
          aria-label="Refresh balances"
        >
          {balanceLoading || suggestLoading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </header>

      {/* ── Your balance summary ── */}
      <section className="group-details-section" aria-label="Your balance">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">Your Balance</h3>
        </div>
        {balanceLoading ? (
          <LoadingState label="Calculating…" height="sm" />
        ) : balanceError ? (
          <div className="alert alert--error groups-page__alert" role="alert">
            <span>{balanceError}</span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => groupId && loadBalances(groupId)}
            >
              Retry
            </button>
          </div>
        ) : (
          <BalanceSummary
            balances={balances}
            currentUserId={user?.id ?? null}
          />
        )}
      </section>

      {/* ── All members ── */}
      <section className="group-details-section" aria-label="All member balances">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">All Members</h3>
        </div>
        {balanceLoading ? (
          <LoadingState label="Calculating…" height="sm" />
        ) : balanceError ? null : (
          <MemberBalanceList
            balances={balances}
            memberNames={memberNames}
            currentUserId={user?.id ?? null}
          />
        )}
      </section>

      {/* ── Settlement suggestions & history ── */}
      <section className="group-details-section" aria-label="Settlements">
        <div className="page-header page-header--section">
          <h3 className="page-header__heading page-header__heading--sm">Settlements</h3>
        </div>

        {/* Tab navigation */}
        <div className="tabs" role="tablist">
          <button
            role="tab"
            aria-selected={activeTab === 'suggestions'}
            aria-controls="suggestions-panel"
            className={`tabs__tab${activeTab === 'suggestions' ? ' tabs__tab--active' : ''}`}
            onClick={() => setActiveTab('suggestions')}
          >
            Suggestions
          </button>
          <button
            role="tab"
            aria-selected={activeTab === 'history'}
            aria-controls="history-panel"
            className={`tabs__tab${activeTab === 'history' ? ' tabs__tab--active' : ''}`}
            onClick={() => setActiveTab('history')}
          >
            History
          </button>
        </div>

        {/* Suggestions tab */}
        {activeTab === 'suggestions' && (
          <div id="suggestions-panel" role="tabpanel" aria-labelledby="suggestions-tab">
            <div className="page-header__sub" style={{ marginBottom: '1rem' }}>
              Suggested transactions to settle all debts
            </div>
            {suggestLoading ? (
              <LoadingState label="Simplifying debts…" height="sm" />
            ) : suggestError ? (
              <div className="alert alert--error groups-page__alert" role="alert">
                <span>{suggestError}</span>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => groupId && loadSuggestions(groupId)}
                >
                  Retry
                </button>
              </div>
            ) : allSettled ? (
              <div className="settlement-empty">
                <span className="settlement-empty__icon">🎉</span>
                <p className="settlement-empty__text">Everyone is settled up.</p>
              </div>
            ) : (
              <SettlementSuggestions
                suggestions={suggestions}
                memberNames={memberNames}
                currentUserId={user?.id ?? null}
                onSettleUp={handleSettleUp}
              />
            )}
          </div>
        )}

        {/* History tab */}
        {activeTab === 'history' && (
          <div id="history-panel" role="tabpanel" aria-labelledby="history-tab">
            {historyLoading ? (
              <LoadingState label="Loading settlement history…" height="sm" />
            ) : historyError ? (
              <div className="alert alert--error groups-page__alert" role="alert">
                <span>{historyError}</span>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => groupId && loadHistory(groupId)}
                >
                  Retry
                </button>
              </div>
            ) : (
              <SettlementHistory
                settlements={history}
                memberNames={memberNames}
                currentUserId={user?.id ?? null}
                isLoading={historyLoading}
              />
            )}
          </div>
        )}
      </section>

      {/* Settlement confirmation modal */}
      {settlementToConfirm && (
        <ConfirmSettlementModal
          suggestion={settlementToConfirm}
          fromUserName={memberNames[settlementToConfirm.fromUserId] || (user && settlementToConfirm.fromUserId === user.id ? user.name : 'Unknown member')}
          toUserName={memberNames[settlementToConfirm.toUserId] || (user && settlementToConfirm.toUserId === user.id ? user.name : 'Unknown member')}
          onConfirm={handleConfirmSettlement}
          onCancel={handleCancelSettlement}
          isSubmitting={isSubmittingSettlement}
        />
      )}
    </div>
  );
}
