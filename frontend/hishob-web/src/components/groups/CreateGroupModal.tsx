/**
 * CreateGroupModal — form for creating a new group.
 *
 * Fields (matches CreateGroupRequest.java):
 *   - name        (required, 2-150 chars)
 *   - description (optional, max 1000 chars)
 *
 * Submits POST /api/groups through the Axios Gateway client.
 * Calls onCreated(newGroup) on success so the parent page can refresh.
 */

import { type FormEvent, useState } from 'react';
import Modal from '@/components/common/Modal';
import FormField from '@/components/common/FormField';
import { createGroup } from '@/services/groupService';
import type { CreateGroupRequest, GroupResponse } from '@/types/groups';
import { getErrorMessage, isAxiosError } from '@/utils/errorUtils';
import type { ApiError } from '@/types/auth';

interface CreateGroupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (group: GroupResponse) => void;
}

export default function CreateGroupModal({
  isOpen,
  onClose,
  onCreated,
}: CreateGroupModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<{ name?: string; description?: string }>({});
  const [apiError, setApiError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const resetState = () => {
    setName('');
    setDescription('');
    setErrors({});
    setApiError('');
    setIsLoading(false);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const validate = (): boolean => {
    const next: typeof errors = {};
    const trimmed = name.trim();
    if (!trimmed) {
      next.name = 'Group name is required.';
    } else if (trimmed.length < 2 || trimmed.length > 150) {
      next.name = 'Group name must be between 2 and 150 characters.';
    }
    if (description.length > 1000) {
      next.description = 'Description cannot exceed 1000 characters.';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError('');
    if (!validate()) return;

    const payload: CreateGroupRequest = {
      name: name.trim(),
    };
    if (description.trim()) {
      payload.description = description.trim();
    }

    setIsLoading(true);
    try {
      const group = await createGroup(payload);
      onCreated(group);
      resetState();
      onClose();
    } catch (err: unknown) {
      // Surface backend per-field validationErrors if present
      if (isAxiosError(err)) {
        const data = err.response?.data as ApiError | undefined;
        if (data?.validationErrors) {
          const next: typeof errors = {};
          if (data.validationErrors.name) next.name = data.validationErrors.name;
          if (data.validationErrors.description)
            next.description = data.validationErrors.description;
          setErrors(next);
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
      title="Create a new group"
      description="Groups are where you track shared expenses with friends, family, or teammates."
    >
      <form onSubmit={handleSubmit} noValidate>
        {apiError && (
          <div className="alert alert--error" role="alert">
            {apiError}
          </div>
        )}

        <FormField
          id="group-name"
          label="Group name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
          placeholder="e.g. Weekend Trip, Apartment, Team Lunch"
          required
          disabled={isLoading}
          autoComplete="off"
        />

        <FormField
          id="group-description"
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          placeholder="Optional — what's this group for?"
          disabled={isLoading}
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
            disabled={isLoading}
            aria-busy={isLoading}
          >
            {isLoading ? 'Creating…' : 'Create group'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
