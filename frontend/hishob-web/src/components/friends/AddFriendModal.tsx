import { type FormEvent, useState } from 'react';
import Modal from '@/components/common/Modal';
import FormField from '@/components/common/FormField';
import { searchUsers, sendFriendRequest } from '@/services/friendService';
import type { UserSearchResult } from '@/types/friends';
import { getErrorMessage } from '@/utils/errorUtils';

interface AddFriendModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSent: () => void;
}

function displayName(name: string | null): string {
  return name?.trim() || 'Unknown user';
}

export default function AddFriendModal({
  isOpen,
  onClose,
  onSent,
}: AddFriendModalProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [apiError, setApiError] = useState('');
  const [success, setSuccess] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [sendingUserId, setSendingUserId] = useState<string | null>(null);

  const resetState = () => {
    setQuery('');
    setResults([]);
    setHasSearched(false);
    setApiError('');
    setSuccess('');
    setIsSearching(false);
    setSendingUserId(null);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handleSearch = async (e: FormEvent) => {
    e.preventDefault();
    const trimmed = query.trim();
    setApiError('');
    setSuccess('');

    if (!trimmed) {
      setApiError('Enter a name or email to search.');
      return;
    }

    setIsSearching(true);
    setHasSearched(true);
    try {
      const data = await searchUsers(trimmed);
      setResults(data);
    } catch (err: unknown) {
      setResults([]);
      setApiError(getErrorMessage(err));
    } finally {
      setIsSearching(false);
    }
  };

  const handleSend = async (user: UserSearchResult) => {
    setApiError('');
    setSuccess('');
    setSendingUserId(user.userId);
    try {
      await sendFriendRequest(user.userId);
      setSuccess('Friend request sent.');
      onSent();
      setTimeout(() => {
        handleClose();
      }, 450);
    } catch (err: unknown) {
      setApiError(getErrorMessage(err));
    } finally {
      setSendingUserId(null);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Add Friend"
      description="Search by name or email, then send a friend request."
    >
      <form onSubmit={handleSearch} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}
        {success && (
          <div className="alert alert--success" role="status">
            {success}
          </div>
        )}

        <div className="friend-search-row">
          <FormField
            id="friend-search"
            label="Search users"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Name or email"
            required
            disabled={isSearching || sendingUserId !== null}
            autoComplete="off"
          />
          <button
            type="submit"
            className="btn btn--primary friend-search-row__button"
            disabled={isSearching || sendingUserId !== null}
            aria-busy={isSearching}
          >
            {isSearching ? 'Searching...' : 'Search'}
          </button>
        </div>
      </form>

      <div className="friend-search-results" aria-live="polite">
        {hasSearched && !isSearching && results.length === 0 && !apiError && (
          <p className="friend-search-results__empty">No matching users found.</p>
        )}

        {results.map((user) => {
          const isSending = sendingUserId === user.userId;
          return (
            <article key={user.userId} className="friend-search-result">
              <div>
                <h4 className="friend-search-result__name">
                  {displayName(user.name)}
                </h4>
              </div>
              <button
                type="button"
                className="btn btn--primary btn--sm"
                onClick={() => void handleSend(user)}
                disabled={sendingUserId !== null}
                aria-busy={isSending}
              >
                {isSending ? 'Sending...' : 'Send Request'}
              </button>
            </article>
          );
        })}
      </div>
    </Modal>
  );
}
