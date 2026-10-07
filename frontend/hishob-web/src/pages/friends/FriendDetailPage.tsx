import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import EmptyState from '@/components/common/EmptyState';
import LoadingState from '@/components/common/LoadingState';
import { blockFriend, getFriends, removeFriend } from '@/services/friendService';
import { getFriendBalance } from '@/services/personalBalanceService';
import { deletePersonalExpense, getFriendPersonalExpenses } from '@/services/personalExpenseService';
import { getFriendSettlementHistory } from '@/services/personalSettlementService';
import type { Friend } from '@/types/friends';
import type { FriendBalanceResponse } from '@/types/personalBalances';
import type { PersonalExpenseResponse } from '@/types/personalExpenses';
import type { PersonalSettlementHistoryResponse } from '@/types/personalSettlements';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage } from '@/utils/errorUtils';
import PersonalExpenseCard from '@/components/personal/PersonalExpenseCard';
import AddPersonalExpenseModal from '@/components/personal/AddPersonalExpenseModal';
import EditPersonalExpenseModal from '@/components/personal/EditPersonalExpenseModal';
import PersonalExpenseDetailModal from '@/components/personal/PersonalExpenseDetailModal';
import ConfirmPersonalSettlementModal from '@/components/personal/ConfirmPersonalSettlementModal';
import PersonalSettlementHistory from '@/components/personal/PersonalSettlementHistory';

function displayName(name: string | null): string {
  return name?.trim() || 'Friend';
}

function initialFor(name: string | null, email: string): string {
  const source = name?.trim() || email.trim();
  return source ? source.charAt(0).toUpperCase() : '?';
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  } catch {
    return '';
  }
}

function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function FriendDetailPage() {
  const { friendId } = useParams<{ friendId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'overview' | 'expenses' | 'settlements'>('overview');

  const [friends, setFriends] = useState<Friend[]>([]);
  const [balance, setBalance] = useState<FriendBalanceResponse | null>(null);
  const [expenses, setExpenses] = useState<PersonalExpenseResponse[]>([]);
  const [settlements, setSettlements] = useState<PersonalSettlementHistoryResponse[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [isRemoving, setIsRemoving] = useState(false);
  const [isBlocking, setIsBlocking] = useState(false);

  // Modals state
  const [addOpen, setAddOpen] = useState(false);
  const [viewExpense, setViewExpense] = useState<PersonalExpenseResponse | null>(null);
  const [editExpense, setEditExpense] = useState<PersonalExpenseResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PersonalExpenseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [settleOpen, setSettleOpen] = useState(false);

  const friend = useMemo(
    () => friends.find((item) => item.userId === friendId) ?? null,
    [friendId, friends]
  );

  const memberNames = useMemo<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    if (user) {
      map[user.id] = user.name ? `${user.name} (You)` : 'You';
    }
    if (friend) {
      map[friend.userId] = displayName(friend.name);
    }
    friends.forEach((f) => {
      map[f.userId] = displayName(f.name);
    });
    return map;
  }, [user, friend, friends]);

  const reload = useCallback(async () => {
    if (!friendId) return;
    setIsLoading(true);
    setError(null);
    try {
      const [friendsData, balanceData, expensesData, settlementsData] = await Promise.all([
        getFriends(),
        getFriendBalance(friendId).catch(() => null),
        getFriendPersonalExpenses(friendId).catch(() => [] as PersonalExpenseResponse[]),
        getFriendSettlementHistory(friendId).catch(() => [] as PersonalSettlementHistoryResponse[]),
      ]);

      setFriends(friendsData);
      setBalance(balanceData);
      setExpenses(expensesData);
      setSettlements(settlementsData);
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [friendId]);

  useEffect(() => {
    queueMicrotask(() => {
      void reload();
    });
  }, [reload]);

  const handleRemove = async () => {
    if (!friend) return;
    const confirmed = window.confirm(`Remove ${displayName(friend.name)} from your friends?`);
    if (!confirmed) return;

    setIsRemoving(true);
    setError(null);
    try {
      await removeFriend(friend.userId);
      navigate('/friends', {
        replace: true,
        state: { message: 'Friend removed.' },
      });
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setIsRemoving(false);
    }
  };

  const handleBlock = async () => {
    if (!friend) return;
    const confirmed = window.confirm(
      `Block ${displayName(friend.name)}? They will not be able to send new friend requests.`
    );
    if (!confirmed) return;

    setIsBlocking(true);
    setError(null);
    try {
      await blockFriend(friend.userId);
      navigate('/friends', {
        replace: true,
        state: { message: 'Friend blocked.' },
      });
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setIsBlocking(false);
    }
  };

  const handleExpenseCreated = (created: PersonalExpenseResponse) => {
    setExpenses((prev) => [created, ...prev]);
    void reload();
  };

  const handleExpenseUpdated = (updated: PersonalExpenseResponse) => {
    setExpenses((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
    if (viewExpense && viewExpense.id === updated.id) {
      setViewExpense(updated);
    }
    setEditExpense(null);
    void reload();
  };

  const handleDeleteExpenseConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await deletePersonalExpense(deleteTarget.id);
      setExpenses((prev) => prev.filter((e) => e.id !== deleteTarget.id));
      if (viewExpense && viewExpense.id === deleteTarget.id) {
        setViewExpense(null);
      }
      setDeleteTarget(null);
      void reload();
    } catch (err) {
      setDeleteError(getErrorMessage(err));
    } finally {
      setIsDeleting(false);
    }
  };

  const handleSettlementSuccess = () => {
    void reload();
    setSettleOpen(false);
  };

  if (isLoading) {
    return <LoadingState label="Loading friend details..." height="lg" />;
  }

  if (error) {
    return (
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
    );
  }

  if (!friend) {
    return (
      <EmptyState
        icon="?"
        heading="Friend not found"
        description="This friendship may have been removed or you followed an old link."
        action={
          <Link to="/friends" className="btn btn--primary">
            Back to Friends
          </Link>
        }
      />
    );
  }

  const friendName = displayName(friend.name);
  const userOwesFriend = balance?.currentUserStatus === 'OWES' && (balance.amount > 0 || balance.currentUserNetBalance < 0);
  const friendOwesUser = balance?.currentUserStatus === 'GETS' && (balance.amount > 0 || balance.currentUserNetBalance > 0);
  const balanceAmount = balance ? balance.amount || Math.abs(balance.currentUserNetBalance) : 0;

  return (
    <div className="friend-detail-page">
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/friends">Friends</Link>
            <span aria-hidden="true"> / </span>
            <span>{friendName}</span>
          </nav>

          <div className="friend-profile-heading">
            <div className="friend-avatar friend-avatar--lg" aria-hidden="true">
              {initialFor(friend.name, friend.email)}
            </div>
            <div>
              <div className="group-details-title-row">
                <h2 className="page-header__heading">{friendName}</h2>
                <span className="badge badge--primary">Friend</span>
              </div>
              <p className="page-header__sub page-header__sub--desc">
                {friend.email}
              </p>
              <p className="group-details-meta">
                Friends since {formatDate(friend.createdAt)}
              </p>
            </div>
          </div>
        </div>

        <div className="group-details-actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setAddOpen(true)}
          >
            + Add Expense
          </button>
          {userOwesFriend && (
            <button
              type="button"
              className="btn btn--secondary"
              onClick={() => setSettleOpen(true)}
            >
              Settle Up ₹{formatAmount(balanceAmount)}
            </button>
          )}
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => void handleRemove()}
            disabled={isRemoving || isBlocking}
            aria-busy={isRemoving}
          >
            {isRemoving ? 'Removing...' : 'Remove'}
          </button>
          <button
            type="button"
            className="btn btn--danger"
            onClick={() => void handleBlock()}
            disabled={isRemoving || isBlocking}
            aria-busy={isBlocking}
          >
            {isBlocking ? 'Blocking...' : 'Block'}
          </button>
        </div>
      </header>

      {/* Navigation Tabs */}
      <div className="tab-nav" role="tablist" style={{ marginTop: '1.5rem', marginBottom: '1.5rem', display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--color-border)', paddingBottom: '0.5rem' }}>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'overview'}
          className={`btn btn--sm ${activeTab === 'overview' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setActiveTab('overview')}
        >
          Overview & Balance
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'expenses'}
          className={`btn btn--sm ${activeTab === 'expenses' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setActiveTab('expenses')}
        >
          Shared Expenses ({expenses.length})
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'settlements'}
          className={`btn btn--sm ${activeTab === 'settlements' ? 'btn--primary' : 'btn--ghost'}`}
          onClick={() => setActiveTab('settlements')}
        >
          Settlement History ({settlements.length})
        </button>
      </div>

      {/* Delete confirmation banner */}
      {deleteTarget && (
        <div className="expense-delete-confirm" role="alert" style={{ marginBottom: '1.5rem' }}>
          <p>
            Delete <strong>"{deleteTarget.description}"</strong>? This will remove the expense.
          </p>
          {deleteError && (
            <span className="form-field__error">{deleteError}</span>
          )}
          <div className="expense-delete-confirm__actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => {
                setDeleteTarget(null);
                setDeleteError(null);
              }}
              disabled={isDeleting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn--danger btn--sm"
              onClick={handleDeleteExpenseConfirm}
              disabled={isDeleting}
              aria-busy={isDeleting}
            >
              {isDeleting ? 'Deleting…' : 'Yes, delete'}
            </button>
          </div>
        </div>
      )}

      {/* TAB 1: OVERVIEW & BALANCE */}
      {activeTab === 'overview' && (
        <>
          {/* Balance card */}
          <section className="group-details-section" aria-labelledby="friend-balance-heading">
            <div className="page-header page-header--section">
              <div className="page-header__title">
                <h3 id="friend-balance-heading" className="page-header__heading page-header__heading--sm">
                  Balance with {friendName}
                </h3>
              </div>
            </div>

            <div
              className={`balance-card ${
                userOwesFriend ? 'balance-card--owe' : friendOwesUser ? 'balance-card--gets' : ''
              }`}
              style={{ padding: '1.5rem', background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
                <div>
                  <h4 style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>
                    {userOwesFriend && `You owe ${friendName} ₹${formatAmount(balanceAmount)}`}
                    {friendOwesUser && `${friendName} owes you ₹${formatAmount(balanceAmount)}`}
                    {!userOwesFriend && !friendOwesUser && `You and ${friendName} are settled up`}
                  </h4>
                  <p className="page-header__sub">
                    {userOwesFriend && 'You need to settle this balance.'}
                    {friendOwesUser && `${friendName} has an outstanding balance with you.`}
                    {!userOwesFriend && !friendOwesUser && 'No outstanding debts between you two.'}
                  </p>
                </div>

                {userOwesFriend && (
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => setSettleOpen(true)}
                  >
                    Settle Up ₹{formatAmount(balanceAmount)}
                  </button>
                )}
              </div>
            </div>
          </section>

          {/* Friendship Information */}
          <section className="group-details-section" aria-labelledby="friend-info-heading" style={{ marginTop: '1.5rem' }}>
            <div className="page-header page-header--section">
              <div className="page-header__title">
                <h3 id="friend-info-heading" className="page-header__heading page-header__heading--sm">
                  Friendship Information
                </h3>
                <p className="page-header__sub">
                  Relationship details for this friend connection.
                </p>
              </div>
            </div>

            <dl className="friend-info-list">
              <div>
                <dt>Status</dt>
                <dd>Accepted</dd>
              </div>
              <div>
                <dt>Connected on</dt>
                <dd>{formatDate(friend.createdAt)}</dd>
              </div>
            </dl>
          </section>
        </>
      )}

      {/* TAB 2: SHARED EXPENSES */}
      {activeTab === 'expenses' && (
        <section className="group-details-section" aria-label="Shared personal expenses">
          <div className="page-header page-header--section">
            <div className="page-header__title">
              <h3 className="page-header__heading page-header__heading--sm">
                Shared Expenses
              </h3>
              <p className="page-header__sub">
                All personal expenses shared with {friendName}.
              </p>
            </div>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              onClick={() => setAddOpen(true)}
            >
              + Add Expense
            </button>
          </div>

          {expenses.length === 0 ? (
            <EmptyState
              icon="💸"
              heading="No shared expenses"
              description={`You haven't recorded any personal expenses with ${friendName} yet.`}
              action={
                <button
                  type="button"
                  className="btn btn--primary"
                  onClick={() => setAddOpen(true)}
                >
                  Add Personal Expense
                </button>
              }
            />
          ) : (
            <div className="expense-list">
              {expenses.map((expense) => (
                <PersonalExpenseCard
                  key={expense.id}
                  expense={expense}
                  memberNames={memberNames}
                  currentUserId={user?.id ?? null}
                  onView={setViewExpense}
                  onEdit={setEditExpense}
                  onDelete={setDeleteTarget}
                />
              ))}
            </div>
          )}
        </section>
      )}

      {/* TAB 3: SHARED SETTLEMENTS */}
      {activeTab === 'settlements' && (
        <section className="group-details-section" aria-label="Shared settlements">
          <div className="page-header page-header--section">
            <div className="page-header__title">
              <h3 className="page-header__heading page-header__heading--sm">
                Shared Settlements
              </h3>
              <p className="page-header__sub">
                Direct settlement history between you and {friendName}.
              </p>
            </div>
          </div>

          <PersonalSettlementHistory
            settlements={settlements}
            memberNames={memberNames}
            currentUserId={user?.id ?? null}
          />
        </section>
      )}

      {/* Add Expense Modal */}
      {addOpen && (
        <AddPersonalExpenseModal
          isOpen={addOpen}
          onClose={() => setAddOpen(false)}
          onCreated={handleExpenseCreated}
          preselectedFriendId={friend.userId}
        />
      )}

      {/* View Detail Modal */}
      {viewExpense && (
        <PersonalExpenseDetailModal
          isOpen={!!viewExpense}
          onClose={() => setViewExpense(null)}
          expense={viewExpense}
          memberNames={memberNames}
          currentUserId={user?.id ?? null}
          onEdit={(exp) => {
            setViewExpense(null);
            setEditExpense(exp);
          }}
          onDelete={(exp) => {
            setViewExpense(null);
            setDeleteTarget(exp);
          }}
        />
      )}

      {/* Edit Modal */}
      {editExpense && (
        <EditPersonalExpenseModal
          isOpen={!!editExpense}
          onClose={() => setEditExpense(null)}
          expense={editExpense}
          currentUserId={user?.id ?? null}
          onUpdated={handleExpenseUpdated}
        />
      )}

      {/* Settle Up Modal */}
      {settleOpen && user && (
        <ConfirmPersonalSettlementModal
          isOpen={settleOpen}
          onClose={() => setSettleOpen(false)}
          suggestion={{
            fromUserId: user.id,
            toUserId: friend.userId,
            amount: balanceAmount,
          }}
          toUserName={friendName}
          onSuccess={handleSettlementSuccess}
        />
      )}
    </div>
  );
}
