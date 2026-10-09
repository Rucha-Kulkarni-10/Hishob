/**
 * ExpenseCommentsSection — comments and discussion panel for expenses.
 */

import { useCallback, useEffect, useState } from 'react';
import type { ExpenseCommentResponse } from '@/types/comments';
import {
  createExpenseComment,
  deleteExpenseComment,
  getExpenseComments,
  updateExpenseComment,
} from '@/services/expenseCommentService';
import { getErrorMessage } from '@/utils/errorUtils';
import CommentItem from './CommentItem';

interface ExpenseCommentsSectionProps {
  expenseId: string;
  expenseCreatorId: string;
  memberNames: Record<string, string>;
  currentUserId: string | null;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function resolveName(
  userId: string,
  memberNames: Record<string, string>,
  currentUserId: string | null
): string {
  if (currentUserId && userId === currentUserId) {
    return 'You';
  }
  const resolved = memberNames[userId];
  if (!resolved || UUID_REGEX.test(resolved.trim())) {
    return 'Unknown member';
  }
  return resolved.trim();
}

export default function ExpenseCommentsSection({
  expenseId,
  expenseCreatorId,
  memberNames,
  currentUserId,
}: ExpenseCommentsSectionProps) {
  const [comments, setComments] = useState<ExpenseCommentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [newComment, setNewComment] = useState('');
  const [isPosting, setIsPosting] = useState(false);
  const [postError, setPostError] = useState<string | null>(null);

  const loadComments = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getExpenseComments(expenseId);
      setComments(data);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [expenseId]);

  useEffect(() => {
    queueMicrotask(() => {
      void loadComments();
    });
  }, [loadComments]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newComment.trim();
    if (!trimmed) {
      setPostError('Comment cannot be blank');
      return;
    }
    if (trimmed.length > 1000) {
      setPostError('Comment exceeds maximum length of 1,000 characters');
      return;
    }

    setIsPosting(true);
    setPostError(null);
    try {
      const created = await createExpenseComment(expenseId, { content: trimmed });
      setComments((prev) => [...prev, created]);
      setNewComment('');
    } catch (err) {
      setPostError(getErrorMessage(err));
    } finally {
      setIsPosting(false);
    }
  };

  const handleUpdate = async (commentId: string, newContent: string) => {
    const updated = await updateExpenseComment(expenseId, commentId, { content: newContent });
    setComments((prev) => prev.map((c) => (c.id === commentId ? updated : c)));
  };

  const handleDelete = async (commentId: string) => {
    await deleteExpenseComment(expenseId, commentId);
    setComments((prev) => prev.filter((c) => c.id !== commentId));
  };

  return (
    <section className="expense-comments-section" aria-label="Expense comments">
      <div className="expense-comments-section__header">
        <h5 className="expense-comments-section__heading">
          Comments {comments.length > 0 && `(${comments.length})`}
        </h5>
      </div>

      {isLoading ? (
        <div className="expense-comments-section__loading" aria-live="polite">
          <span className="spinner spinner--sm" aria-hidden="true" />
          <span>Loading comments…</span>
        </div>
      ) : error ? (
        <div className="alert alert--error expense-comments-section__alert" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void loadComments()}
          >
            Retry
          </button>
        </div>
      ) : (
        <div className="expense-comments-section__list">
          {comments.length === 0 ? (
            <div className="expense-comments-section__empty">
              <p>No comments yet. Start the conversation!</p>
            </div>
          ) : (
            comments.map((comment) => {
              const isAuthor = Boolean(currentUserId && comment.authorUserId === currentUserId);
              const isExpenseCreator = Boolean(currentUserId && expenseCreatorId === currentUserId);
              const canDelete = isAuthor || isExpenseCreator;
              const authorName = resolveName(comment.authorUserId, memberNames, currentUserId);

              return (
                <CommentItem
                  key={comment.id}
                  comment={comment}
                  authorName={authorName}
                  isAuthor={isAuthor}
                  canDelete={canDelete}
                  onUpdate={handleUpdate}
                  onDelete={handleDelete}
                />
              );
            })
          )}
        </div>
      )}

      {/* Add comment input */}
      <form onSubmit={handleCreate} className="expense-comments-section__form">
        <div className="form-field">
          <textarea
            id={`comment-input-${expenseId}`}
            className="form-field__input expense-comments-section__input"
            placeholder="Add a comment or note about this expense…"
            value={newComment}
            onChange={(e) => {
              setNewComment(e.target.value);
              if (postError) setPostError(null);
            }}
            maxLength={1000}
            rows={2}
            disabled={isPosting}
            aria-label="New comment"
          />
        </div>

        <div className="expense-comments-section__form-footer">
          <span
            className={`expense-comments-section__char-count ${
              newComment.length > 900 ? 'expense-comments-section__char-count--warn' : ''
            }`}
          >
            {newComment.length}/1000
          </span>
          <button
            type="submit"
            className="btn btn--primary btn--sm"
            disabled={isPosting || !newComment.trim()}
            aria-busy={isPosting}
          >
            {isPosting ? 'Posting…' : 'Post comment'}
          </button>
        </div>

        {postError && (
          <div className="form-field__error expense-comments-section__form-error" role="alert">
            {postError}
          </div>
        )}
      </form>
    </section>
  );
}
