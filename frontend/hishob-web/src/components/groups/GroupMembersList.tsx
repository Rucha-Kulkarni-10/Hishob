/**
 * GroupMembersList — table of members for a single group.
 *
 * Rows show: role badge (OWNER / MEMBER), user UUID, "You" marker
 * for the current user, and joined-at date.
 */

import type { MemberResponse } from '@/types/groups';

interface GroupMembersListProps {
  members: MemberResponse[];
  currentUserId: string | null; // null when user is loading
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '';
  }
}

export default function GroupMembersList({
  members,
  currentUserId,
}: GroupMembersListProps) {
  if (members.length === 0) {
    return <p className="members-empty">No members yet.</p>;
  }

  return (
    <div className="members" role="region" aria-label="Group members">
      <table className="members-table">
        <thead>
          <tr>
            <th scope="col">Role</th>
            <th scope="col">Member</th>
            <th scope="col">Joined</th>
          </tr>
        </thead>
        <tbody>
          {members.map((m) => {
            const isSelf = currentUserId ? m.userId === currentUserId : false;
            const badgeClass =
              m.role === 'OWNER' ? 'badge badge--primary' : 'badge badge--muted';
            const nameText = m.name || (isSelf ? 'You' : 'Unknown member');
            const showYouTag = isSelf && Boolean(m.name);
            return (
              <tr key={m.id} className={isSelf ? 'members-table__row--self' : ''}>
                <td>
                  <span className={badgeClass}>{m.role.toLowerCase()}</span>
                </td>
                <td className="members-table__member">
                  <div className="members-table__name">
                    <span>{nameText}</span>
                    {showYouTag && (
                      <span className="members-table__self-tag" aria-hidden="true">
                        (You)
                      </span>
                    )}
                  </div>
                </td>
                <td className="members-table__date">
                  {formatDate(m.joinedAt)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
