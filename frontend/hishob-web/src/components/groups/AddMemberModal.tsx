/**
 * AddMemberModal — adds an existing user to a group.
 *
 * Backend expects a raw user UUID in the request body, not an email,
 * because the Group Service does not yet expose a user lookup by email.
 *
 * Requires OWNER role (backend returns 403 for MEMBER callers). We show
 * a disabled/notice state if canAdd is false, but the server will always
 * re-validate.
 */

import { type FormEvent, useState } from 'react';
import Modal from '@/components/common/Modal';
import FormField from '@/components/common/FormField';
import { addMember } from '@/services/groupService';
import type { AddMemberRequest, MemberResponse } from '@/types/groups';
import { getErrorMessage, isAxiosError } from '@/utils/errorUtils';
import type { ApiError } from '@/types/auth';

const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

interface AddMemberModalProps {
  isOpen: boolean;
  onClose: () => void;
  groupId: string;
  canAdd: boolean;   // true if the current user has OWNER role
  onAdded: (member: MemberResponse) => void;
}

export default function AddMemberModal({
  isOpen,
  onClose,
  groupId,
  canAdd,
  onAdded,
}: AddMemberModalProps) {
  const [userId, setUserId] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const resetState = () => {
    setUserId('');
    setFieldError(undefined);
    setApiError('');
    setIsLoading(false);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const validate = (): boolean => {
    const next = (() => {
      const trimmed = userId.trim();
      if (!trimmed) return 'User ID is required.';
      if (!UUID_RE.test(trimmed))
        return 'User ID must be a valid UUID (8-4-4-4-12 hex).';
      return undefined;
    })();
    setFieldError(next);
    return next === undefined;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');
    if (!canAdd) return;
    if (!validate()) return;

    const payload: AddMemberRequest = { userId: userId.trim() };
    setIsLoading(true);
    try {
      const member = await addMember(groupId, payload);
      onAdded(member);
      resetState();
      onClose();
    } catch (err: unknown) {
      if (isAxiosError(err)) {
        const data = err.response?.data as ApiError | undefined;
        if (data?.validationErrors?.userId) {
          setFieldError(data.validationErrors.userId);
        }
      }
      setApiError(getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Add a member"
      description="Enter the user's UUID. (Email-based invites will be added in a future phase.)"
    >
      {!canAdd && (
        <div className="alert alert--error" role="alert">
          Only the group owner can add new members.
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        <FormField
          id="member-userid"
          label="User ID (UUID)"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          error={fieldError}
          placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
          required
          disabled={isLoading || !canAdd}
          autoComplete="off"
        />

        <div className="modal__actions">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={handleClose}
            disabled={isLoading}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={isLoading || !canAdd}
            aria-busy={isLoading}
          >
            {isLoading ? 'Adding…' : 'Add member'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
