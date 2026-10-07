/**
 * GroupsPage — /groups
 *
 * Page title: "My Groups"
 * CTA: "Create Group" button
 * States: loading, error, empty, ready
 * Calls listMyGroups() + renders <GroupsList />
 */

import { useCallback, useEffect, useState } from 'react';
import CreateGroupModal from '@/components/groups/CreateGroupModal';
import GroupsList from '@/components/groups/GroupsList';
import EmptyState from '@/components/common/EmptyState';
import LoadingState from '@/components/common/LoadingState';
import { listMyGroups } from '@/services/groupService';
import type { GroupResponse } from '@/types/groups';
import { useAuth } from '@/hooks/useAuth';
import { getErrorMessage } from '@/utils/errorUtils';

export default function GroupsPage() {
  const { user } = useAuth();
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const reload = useCallback(async () => {
    await Promise.resolve();
    setIsLoading(true);
    setError(null);
    try {
      const data = await listMyGroups();
      setGroups(data);
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      void reload();
    });
  }, [reload]);

  const handleCreated = () => {
    void reload();
  };

  return (
    <div className="groups-page">
      <header className="page-header">
        <div className="page-header__title">
          <h2 className="page-header__heading">My Groups</h2>
          <p className="page-header__sub">
            Create or open groups to track shared expenses with people you split bills with.
          </p>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => setModalOpen(true)}
        >
          + Create Group
        </button>
      </header>

      {error && !isLoading && (
        <div className="alert alert--error groups-page__alert" role="alert">
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

      {isLoading && <LoadingState label="Loading groups…" height="md" />}

      {!isLoading && !error && groups.length === 0 && (
        <EmptyState
          icon="👥"
          heading="No groups yet"
          description="Create your first group to start splitting expenses."
          action={
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => setModalOpen(true)}
            >
              + Create Group
            </button>
          }
        />
      )}

      {!isLoading && !error && groups.length > 0 && (
        <GroupsList groups={groups} currentUser={user} />
      )}

      <CreateGroupModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
