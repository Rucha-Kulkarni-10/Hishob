/**
 * CommentItem — individual comment component with edit and delete capabilities.
 */

import { useState } from 'react';
import type { ExpenseCommentResponse } from '@/types/comments';
import { getErrorMessage } from '@/utils/errorUtils';

interface CommentItemProps {
  comment: ExpenseCommentResponse;
  authorName: string;
  isAuthor: boolean;
  canDelete: boolean;
  onUpdate: (commentId: string, newContent: string) => Promise<void>;
  onDelete: (commentId: string) => Promise<void>;
}

function formatCommentDate(iso: string): string {
  try {
    const date = new Date(iso);
    const now = new Date();
    const isToday =
      date.getDate() === now.getDate() &&
      date.getMonth() === now.getMonth() &&
      date.getFullYear() === now.getFullYear();

    if (isToday) {
      return date.toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit',
      });
    }

    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

function getInitial(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return '?';
  return trimmed.charAt(0).toUpperCase();
}

export default function CommentItem({
  comment,
  authorName,
  isAuthor,
  canDelete,
  onUpdate,
  onDelete,
}: CommentItemProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(comment.content);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = editContent.trim();
    if (!trimmed) {
      setEditError('Comment cannot be blank');
      return;
    }
    if (trimmed.length > 1000) {
      setEditError('Comment exceeds maximum length of 1,000 characters');
      return;
    }
    if (trimmed === comment.content.trim()) {
      setIsEditing(false);
      return;
    }

    setIsSubmitting(true);
    setEditError(null);
    try {
      await onUpdate(comment.id, trimmed);
      setIsEditing(false);
    } catch (err) {
      setEditError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await onDelete(comment.id);
      setShowDeleteConfirm(false);
    } catch (err) {
      setDeleteError(getErrorMessage(err));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="expense-comment-item" role="article" aria-label={`Comment by ${authorName}`}>
      <div className="expense-comment-item__header">
        <div className="expense-comment-item__author-info">
          <div className="expense-comment-item__avatar" aria-hidden="true">
            {getInitial(authorName)}
          </div>
          <span className="expense-comment-item__author">{authorName}</span>
          <span className="expense-comment-item__timestamp">
            {formatCommentDate(comment.createdAt)}
          </span>
          {comment.isEdited && (
            <span className="expense-comment-item__edited-badge" title={`Edited on ${formatCommentDate(comment.updatedAt)}`}>
              (edited)
            </span>
          )}
        </div>

        {!isEditing && !showDeleteConfirm && (isAuthor || canDelete) && (
          <div className="expense-comment-item__actions">
            {isAuthor && (
              <button
                type="button"
                className="btn btn--ghost btn--sm expense-comment-item__btn"
                onClick={() => {
                  setEditContent(comment.content);
                  setEditError(null);
                  setIsEditing(true);
                }}
                aria-label="Edit comment"
              >
                Edit
              </button>
            )}
            {canDelete && (
              <button
                type="button"
                className="btn btn--ghost btn--sm expense-comment-item__btn expense-comment-item__btn--danger"
                onClick={() => {
                  setDeleteError(null);
                  setShowDeleteConfirm(true);
                }}
                aria-label="Delete comment"
              >
                Delete
              </button>
            )}
          </div>
        )}
      </div>

      {isEditing ? (
        <form onSubmit={handleSaveEdit} className="expense-comment-item__edit-form">
          <textarea
            className="form-field__input expense-comment-item__edit-input"
            value={editContent}
            onChange={(e) => {
              setEditContent(e.target.value);
              if (editError) setEditError(null);
            }}
            maxLength={1000}
            rows={3}
            disabled={isSubmitting}
            aria-label="Edit comment content"
            autoFocus
          />
          <div className="expense-comment-item__edit-footer">
            <span
              className={`expense-comment-item__char-count ${
                editContent.length > 900 ? 'expense-comment-item__char-count--warn' : ''
              }`}
            >
              {editContent.length}/1000
            </span>
            <div className="expense-comment-item__edit-actions">
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => {
                  setIsEditing(false);
                  setEditContent(comment.content);
                  setEditError(null);
                }}
                disabled={isSubmitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="btn btn--primary btn--sm"
                disabled={isSubmitting || !editContent.trim()}
                aria-busy={isSubmitting}
              >
                {isSubmitting ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
          {editError && (
            <div className="form-field__error expense-comment-item__error" role="alert">
              {editError}
            </div>
          )}
        </form>
      ) : showDeleteConfirm ? (
        <div className="expense-comment-item__delete-confirm" role="alert">
          <span>Delete this comment?</span>
          {deleteError && (
            <span className="form-field__error expense-comment-item__error">{deleteError}</span>
          )}
          <div className="expense-comment-item__delete-actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => {
                setShowDeleteConfirm(false);
                setDeleteError(null);
              }}
              disabled={isDeleting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn--danger btn--sm"
              onClick={handleDelete}
              disabled={isDeleting}
              aria-busy={isDeleting}
            >
              {isDeleting ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>
      ) : (
        <div className="expense-comment-item__body">
          <p className="expense-comment-item__content">{comment.content}</p>
        </div>
      )}
    </div>
  );
}
