/**
 * GroupsList — responsive grid of GroupCards.
 *
 * Each card links to /groups/:id, shows name, description,
 * created date, and an "Owner" badge if the current user is
 * the creator (matches group.createdBy === currentUserId).
 */

import { Link } from 'react-router-dom';
import type { GroupResponse } from '@/types/groups';
import type { User } from '@/types/auth';

interface GroupsListProps {
  groups: GroupResponse[];
  currentUser: User | null;
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

export default function GroupsList({ groups, currentUser }: GroupsListProps) {
  if (groups.length === 0) return null;

  return (
    <div className="groups-grid">
      {groups.map((g) => {
        const isOwner = currentUser ? g.createdBy === currentUser.id : false;
        return (
          <Link
            key={g.id}
            to={`/groups/${g.id}`}
            className="group-card"
            aria-label={`Open group ${g.name}`}
          >
            <header className="group-card__header">
              <h3 className="group-card__name">{g.name}</h3>
              {isOwner && (
                <span className="badge badge--primary" aria-label="Owner">
                  Owner
                </span>
              )}
            </header>

            {g.description && (
              <p className="group-card__desc" title={g.description}>
                {g.description}
              </p>
            )}

            <footer className="group-card__footer">
              <span className="group-card__meta">
                Created {formatDate(g.createdAt)}
              </span>
              <span className="group-card__cta" aria-hidden="true">
                Open →
              </span>
            </footer>
          </Link>
        );
      })}
    </div>
  );
}
