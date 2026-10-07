import type { FriendRequest } from '@/types/friends';

interface FriendRequestsProps {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
  processingId: string | null;
  onAccept: (request: FriendRequest) => void;
  onReject: (request: FriendRequest) => void;
  onCancel: (request: FriendRequest) => void;
}

function displayName(name: string | null): string {
  return name?.trim() || 'Unknown user';
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

export default function FriendRequests({
  incoming,
  outgoing,
  processingId,
  onAccept,
  onReject,
  onCancel,
}: FriendRequestsProps) {
  return (
    <div className="friend-requests">
      <section className="friend-requests__section" aria-labelledby="incoming-heading">
        <div className="friend-requests__header">
          <h3 id="incoming-heading" className="friend-requests__heading">
            Incoming Requests
          </h3>
          <span className="badge badge--muted">{incoming.length}</span>
        </div>

        {incoming.length === 0 ? (
          <p className="friend-requests__empty">No incoming friend requests.</p>
        ) : (
          <div className="friend-request-list">
            {incoming.map((request) => {
              const isProcessing = processingId === request.requestId;
              return (
                <article key={request.requestId} className="friend-request-card">
                  <div className="friend-request-card__main">
                    <h4 className="friend-request-card__name">
                      {displayName(request.name)}
                    </h4>
                    <p className="friend-request-card__email">{request.email}</p>
                    <p className="friend-request-card__date">
                      Requested {formatDate(request.createdAt)}
                    </p>
                  </div>
                  <div className="friend-request-card__actions">
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      onClick={() => onAccept(request)}
                      disabled={isProcessing}
                      aria-busy={isProcessing}
                    >
                      Accept
                    </button>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      onClick={() => onReject(request)}
                      disabled={isProcessing}
                    >
                      Reject
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="friend-requests__section" aria-labelledby="outgoing-heading">
        <div className="friend-requests__header">
          <h3 id="outgoing-heading" className="friend-requests__heading">
            Outgoing Requests
          </h3>
          <span className="badge badge--muted">{outgoing.length}</span>
        </div>

        {outgoing.length === 0 ? (
          <p className="friend-requests__empty">No outgoing friend requests.</p>
        ) : (
          <div className="friend-request-list">
            {outgoing.map((request) => {
              const isProcessing = processingId === request.requestId;
              return (
                <article key={request.requestId} className="friend-request-card">
                  <div className="friend-request-card__main">
                    <h4 className="friend-request-card__name">
                      {displayName(request.name)}
                    </h4>
                    <p className="friend-request-card__email">{request.email}</p>
                    <p className="friend-request-card__date">
                      Sent {formatDate(request.createdAt)}
                    </p>
                  </div>
                  <div className="friend-request-card__actions">
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      onClick={() => onCancel(request)}
                      disabled={isProcessing}
                      aria-busy={isProcessing}
                    >
                      {isProcessing ? 'Canceling...' : 'Cancel'}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
