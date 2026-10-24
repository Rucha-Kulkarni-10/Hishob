import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AddFriendModal from '@/components/friends/AddFriendModal';
import FriendRequests from '@/components/friends/FriendRequests';
import FriendsList from '@/components/friends/FriendsList';
import EmptyState from '@/components/common/EmptyState';
import LoadingState from '@/components/common/LoadingState';
import {
  acceptFriendRequest,
  cancelFriendRequest,
  getFriends,
  getIncomingRequests,
  getOutgoingRequests,
  rejectFriendRequest,
  removeFriend,
} from '@/services/friendService';
import { getDashboardOverview } from '@/services/expenseService';
import type { DashboardOverviewResponse } from '@/types/dashboard';
import type { Friend, FriendRequest } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';

type FriendsTab = 'friends' | 'requests';

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function FriendsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const initialRouteMessage = (location.state as { message?: string } | null)?.message ?? '';
  const [friends, setFriends] = useState<Friend[]>([]);
  const [incoming, setIncoming] = useState<FriendRequest[]>([]);
  const [outgoing, setOutgoing] = useState<FriendRequest[]>([]);
  const [activeTab, setActiveTab] = useState<FriendsTab>('friends');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<DashboardOverviewResponse | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [notice, setNotice] = useState(initialRouteMessage);
  const [addOpen, setAddOpen] = useState(false);
  const [processingRequestId, setProcessingRequestId] = useState<string | null>(null);
  const [removingUserId, setRemovingUserId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    setOverviewLoading(true);
    const [friendsRes, incomingRes, outgoingRes, overviewRes] = await Promise.allSettled([
      getFriends(),
      getIncomingRequests(),
      getOutgoingRequests(),
      getDashboardOverview(),
    ]);

    if (friendsRes.status === 'fulfilled') {
      setFriends(friendsRes.value);
    } else {
      setError(getErrorMessage(friendsRes.reason));
    }
    if (incomingRes.status === 'fulfilled') {
      setIncoming(incomingRes.value);
    } else {
      setError(getErrorMessage(incomingRes.reason));
    }
    if (outgoingRes.status === 'fulfilled') {
      setOutgoing(outgoingRes.value);
    } else {
      setError(getErrorMessage(outgoingRes.reason));
    }

    // The overall total is the disjoint group + individual sum from /api/expenses/overview.
    // Recorded spend only — shares, payments and balances are separate metrics.
    if (overviewRes.status === 'fulfilled') {
      setOverview(overviewRes.value);
      setOverviewError(null);
    } else {
      setOverview(null);
      setOverviewError(getErrorMessage(overviewRes.reason));
    }

    setIsLoading(false);
    setOverviewLoading(false);
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void reload();
    });
  }, [reload]);

  useEffect(() => {
    if (initialRouteMessage) {
      navigate(location.pathname, { replace: true });
    }
  }, [initialRouteMessage, location.pathname, navigate]);

  const showNotice = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(''), 3500);
  };

  const handleAccept = async (request: FriendRequest) => {
    setProcessingRequestId(request.requestId);
    setError(null);
    try {
      await acceptFriendRequest(request.requestId);
      await reload();
      showNotice('Friend request accepted.');
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setProcessingRequestId(null);
    }
  };

  const handleReject = async (request: FriendRequest) => {
    setProcessingRequestId(request.requestId);
    setError(null);
    try {
      await rejectFriendRequest(request.requestId);
      await reload();
      showNotice('Friend request rejected.');
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setProcessingRequestId(null);
    }
  };

  const handleCancel = async (request: FriendRequest) => {
    setProcessingRequestId(request.requestId);
    setError(null);
    try {
      await cancelFriendRequest(request.requestId);
      await reload();
      showNotice('Friend request canceled.');
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setProcessingRequestId(null);
    }
  };

  const handleRemove = async (friend: Friend) => {
    const name = friend.name?.trim() || friend.email;
    const confirmed = window.confirm(`Remove ${name} from your friends?`);
    if (!confirmed) return;

    setRemovingUserId(friend.userId);
    setError(null);
    try {
      await removeFriend(friend.userId);
      await reload();
      showNotice('Friend removed.');
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setRemovingUserId(null);
    }
  };

  const handleSent = () => {
    void reload();
    showNotice('Friend request sent.');
  };

  return (
    <div className="friends-page">
      <header className="page-header">
        <div className="page-header__title">
          <h2 className="page-header__heading">Friends</h2>
          <p className="page-header__sub">
            Manage your friendships and shared connections.
          </p>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setAddOpen(true)}
        >
          + Add Friend
        </button>
      </header>

      <section className="friends-summary" style={{ gridTemplateColumns: '1fr' }} aria-label="Overall expense total">
        <article className="friends-summary__card">
          <span className="friends-summary__label">Total Recorded Expenses</span>
          <strong className="friends-summary__value">
            {overviewLoading ? '…' : overview ? `₹${formatAmount(overview.combined.recordedSpend)}` : '—'}
          </strong>
          <span className="friends-summary__label" style={{ marginTop: 'var(--space-2)', marginBottom: 0 }}>
            {overviewLoading
              ? 'Loading your expenses…'
              : overview
                ? `${overview.combined.expenseCount} expenses recorded — group and 1-on-1, each counted once`
                : 'Group and individual totals are unavailable right now'}
          </span>
          <div>
            <Link to="/expense-overview" className="btn btn--secondary btn--sm" style={{ marginTop: 'var(--space-3)' }}>
              View Details
            </Link>
          </div>
        </article>
      </section>

      {overviewError && !overviewLoading && (
        <div className="alert alert--error friends-page__alert" role="alert">
          <span>Could not load the overall expense total: {overviewError}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void reload()}
          >
            Retry
          </button>
        </div>
      )}

      <section className="friends-summary" aria-label="Friends summary">
        <article className="friends-summary__card">
          <span className="friends-summary__label">Total Friends</span>
          <strong className="friends-summary__value">{friends.length}</strong>
        </article>
        <article className="friends-summary__card">
          <span className="friends-summary__label">Incoming Requests</span>
          <strong className="friends-summary__value">{incoming.length}</strong>
        </article>
        <article className="friends-summary__card">
          <span className="friends-summary__label">Outgoing Requests</span>
          <strong className="friends-summary__value">{outgoing.length}</strong>
        </article>
      </section>

      {notice && (
        <div className="alert alert--success friends-page__alert" role="status">
          {notice}
        </div>
      )}

      {error && !isLoading && (
        <div className="alert alert--error friends-page__alert" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void reload()}
          >
            Retry
          </button>
        </div>
      )}

      <div className="friends-tabs" role="tablist" aria-label="Friends sections">
        <button
          type="button"
          role="tab"
          className={`friends-tabs__button${activeTab === 'friends' ? ' is-active' : ''}`}
          aria-selected={activeTab === 'friends'}
          onClick={() => setActiveTab('friends')}
        >
          Friends
        </button>
        <button
          type="button"
          role="tab"
          className={`friends-tabs__button${activeTab === 'requests' ? ' is-active' : ''}`}
          aria-selected={activeTab === 'requests'}
          onClick={() => setActiveTab('requests')}
        >
          Requests
        </button>
      </div>

      {isLoading && <LoadingState label="Loading friends..." height="md" />}

      {!isLoading && !error && activeTab === 'friends' && friends.length === 0 && (
        <EmptyState
          icon="Friends"
          heading="No friends yet"
          description="Search for someone by name or email to send your first friend request."
          action={
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => setAddOpen(true)}
            >
              + Add Friend
            </button>
          }
        />
      )}

      {!isLoading && !error && activeTab === 'friends' && friends.length > 0 && (
        <FriendsList
          friends={friends}
          removingUserId={removingUserId}
          onRemove={handleRemove}
        />
      )}

      {!isLoading && !error && activeTab === 'requests' && (
        <FriendRequests
          incoming={incoming}
          outgoing={outgoing}
          processingId={processingRequestId}
          onAccept={(request) => void handleAccept(request)}
          onReject={(request) => void handleReject(request)}
          onCancel={(request) => void handleCancel(request)}
        />
      )}

      <AddFriendModal
        isOpen={addOpen}
        onClose={() => setAddOpen(false)}
        onSent={handleSent}
      />
    </div>
  );
}
