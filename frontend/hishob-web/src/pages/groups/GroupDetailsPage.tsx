/**
 * GroupDetailsPage — /groups/:id
 *
 * Loads:
 *   - GET /api/groups/:id          (group detail)
 *   - GET /api/groups/:id/members  (member list)
 * Both fire in parallel on mount + whenever :id changes.
 *
 * Displays:
 *   - Name, description, created date, Owner badge
 *   - Members table (with "You" marker for self)
 *   - "Add member" CTA enabled for OWNER role
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import GroupMembersList from '@/components/groups/GroupMembersList';
import AddMemberModal from '@/components/groups/AddMemberModal';
import LoadingState from '@/components/common/LoadingState';
import EmptyState from '@/components/common/EmptyState';
import { getGroupDetail, listMembers } from '@/services/groupService';
import type { GroupResponse, MemberResponse, MemberRole } from '@/types/groups';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage, isAxiosError } from '@/utils/errorUtils';

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

export default function GroupDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();

  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [is404, setIs404] = useState(false);
  const [is403, setIs403] = useState(false);
  const [addOpen, setAddOpen] = useState(false);

  const currentRole: MemberRole | null = useMemo(() => {
    if (!user) return null;
    const mine = members.find((m) => m.userId === user.id);
    return mine?.role ?? null;
  }, [members, user]);

  const isOwner = currentRole === 'OWNER';

  const reload = useCallback(async (groupId: string) => {
    await Promise.resolve();
    setIsLoading(true);
    setError(null);
    setIs404(false);
    setIs403(false);
    try {
      const [g, ms] = await Promise.all([
        getGroupDetail(groupId),
        listMembers(groupId),
      ]);
      setGroup(g);
      setMembers(ms);
    } catch (err: unknown) {
      if (isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 404) {
          setIs404(true);
        } else if (status === 403) {
          setIs403(true);
        } else {
          setError(getErrorMessage(err));
        }
      } else {
        setError(getErrorMessage(err));
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!id) return;
    queueMicrotask(() => {
      void reload(id);
    });
  }, [id, reload]);

  const handleAdded = () => {
    if (id) void reload(id);
  };

  // ── States: loading / 404 / 403 / error ───────────────────────────────────
  if (isLoading) {
    return <LoadingState label="Loading group…" height="lg" />;
  }

  if (is404) {
    return (
      <EmptyState
        icon="🔍"
        heading="Group not found"
        description="This group may have been deleted or you followed a bad link."
        action={
          <Link to="/groups" className="btn btn--primary">
            Back to My Groups
          </Link>
        }
      />
    );
  }

  if (is403) {
    return (
      <EmptyState
        icon="🔒"
        heading="You can't view this group"
        description="You must be a member of this group to see its details."
        action={
          <Link to="/groups" className="btn btn--primary">
            Back to My Groups
          </Link>
        }
      />
    );
  }

  if (error) {
    return (
      <div className="alert alert--error groups-page__alert" role="alert">
        <span>{error}</span>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => id && reload(id)}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!group) return null;

  // ── Ready state ────────────────────────────────────────────────────────────
  return (
    <div className="group-details-page">
      <header className="page-header page-header--details">
        <div className="page-header__title">
          <nav aria-label="Breadcrumb" className="group-details-breadcrumb">
            <Link to="/groups">My Groups</Link>
            <span aria-hidden="true"> / </span>
            <span>{group.name}</span>
          </nav>
          <div className="group-details-title-row">
            <h2 className="page-header__heading">{group.name}</h2>
            {isOwner && (
              <span className="badge badge--primary" aria-label="Owner">
                Owner
              </span>
            )}
          </div>
          {group.description && (
            <p className="page-header__sub page-header__sub--desc">
              {group.description}
            </p>
          )}
          <p className="group-details-meta">
            Created {formatDate(group.createdAt)}
            {members.length > 0 ? ` · ${members.length} member${members.length === 1 ? '' : 's'}` : ''}
          </p>
        </div>
        <div className="group-details-actions">
          <Link
            to={`/groups/${id}/expenses`}
            className="btn btn--ghost"
            aria-label="View expenses for this group"
          >
            💸 Expenses
          </Link>
          <Link
            to={`/groups/${id}/balances`}
            className="btn btn--ghost"
            aria-label="View balances for this group"
          >
            ⚖️ Balances
          </Link>
          <Link
            to={`/groups/${id}/reports`}
            className="btn btn--primary"
            aria-label="View reports and analytics for this group"
          >
            📊 Reports
          </Link>
        </div>
      </header>

      <section
        className="group-details-section"
        aria-labelledby="group-members-heading"
      >
        <div className="page-header page-header--section">
          <div className="page-header__title">
            <h3 id="group-members-heading" className="page-header__heading page-header__heading--sm">
              Members
            </h3>
            <p className="page-header__sub">
              People who can view expenses and split bills in this group.
            </p>
          </div>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setAddOpen(true)}
            disabled={!isOwner}
            title={
              isOwner
                ? 'Add a new member to this group'
                : 'Only the group owner can add members'
            }
          >
            + Add member
          </button>
        </div>

        {members.length === 0 ? (
          <EmptyState
            icon="👤"
            heading="No members yet"
            description={
              isOwner
                ? 'Use the Add member button to invite the first person.'
                : 'Members will appear here.'
            }
          />
        ) : (
          <GroupMembersList members={members} currentUserId={user?.id ?? null} />
        )}
      </section>

      {id && (
        <AddMemberModal
          isOpen={addOpen}
          onClose={() => setAddOpen(false)}
          groupId={id}
          canAdd={isOwner}
          onAdded={handleAdded}
        />
      )}
    </div>
  );
}
