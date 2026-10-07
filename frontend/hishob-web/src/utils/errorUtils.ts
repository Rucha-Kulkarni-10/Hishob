/**
 * Utilities for turning Axios / backend errors into user-readable strings.
 *
 * Backend error shape (ErrorResponse.java):
 *   { status, error, message, path, validationErrors? }
 *
 * Rules:
 *   - Never show raw Java stack traces.
 *   - Surface backend validation messages when available.
 *   - Fall back to a generic message for network errors.
 */

import axios from 'axios';
import type { ApiError } from '@/types/auth';

/**
 * Returns a user-friendly error message for a caught error.
 * Works with Axios errors that carry an ApiError response body,
 * and with plain network errors.
 */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError | undefined;

    if (!error.response) {
      return 'Unable to connect to HISHOB server.';
    }

    const status = error.response.status;

    if (data?.validationErrors && Object.keys(data.validationErrors).length > 0) {
      return Object.values(data.validationErrors).join(' ');
    }

    if (data?.message) {
      if (status === 409) {
        return 'This email is already registered. Please sign in instead.';
      }
      if (status === 401) {
        return 'Invalid email or password.';
      }
      return data.message;
    }

    switch (status) {
      case 400:
        return 'The request was invalid. Please check your input.';
      case 401:
        return 'Invalid email or password.';
      case 403:
        return 'You do not have permission to perform this action.';
      case 404:
        return 'The requested resource was not found.';
      case 409:
        return 'This email is already registered. Please sign in instead.';
      case 500:
      case 502:
      case 503:
        return 'Something went wrong on the server. Please try again later.';
      default:
        return `An unexpected error occurred (HTTP ${status}).`;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'An unexpected error occurred. Please try again.';
}
