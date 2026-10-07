import { Link } from 'react-router-dom';
import type { Friend } from '@/types/friends';

interface FriendsListProps {
  friends: Friend[];
  removingUserId: string | null;
  onRemove: (friend: Friend) => void;
}

function displayName(name: string | null): string {
  return name?.trim() || 'Unknown user';
}

function initialFor(name: string | null, email: string): string {
  const source = name?.trim() || email.trim();
  return source ? source.charAt(0).toUpperCase() : '?';
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

export default function FriendsList({
  friends,
  removingUserId,
  onRemove,
}: FriendsListProps) {
  if (friends.length === 0) return null;

  return (
    <div className="friends-grid">
      {friends.map((friend) => {
        const name = displayName(friend.name);
        const isRemoving = removingUserId === friend.userId;

        return (
          <article key={friend.friendshipId} className="friend-card">
            <div className="friend-card__identity">
              <div className="friend-avatar" aria-hidden="true">
                {initialFor(friend.name, friend.email)}
              </div>
              <div className="friend-card__person">
                <h3 className="friend-card__name">{name}</h3>
                <p className="friend-card__email">{friend.email}</p>
              </div>
            </div>

            <div className="friend-card__meta-row">
              <span className="badge badge--primary">Friend</span>
              <span className="friend-card__date">
                Since {formatDate(friend.createdAt)}
              </span>
            </div>

            <footer className="friend-card__actions">
              <Link
                to={`/friends/${friend.userId}`}
                className="btn btn--ghost btn--sm"
                aria-label={`View details for ${name}`}
              >
                View Details
              </Link>
              <button
                type="button"
                className="btn btn--danger btn--sm"
                onClick={() => onRemove(friend)}
                disabled={isRemoving}
                aria-busy={isRemoving}
              >
                {isRemoving ? 'Removing...' : 'Remove Friend'}
              </button>
            </footer>
          </article>
        );
      })}
    </div>
  );
}
