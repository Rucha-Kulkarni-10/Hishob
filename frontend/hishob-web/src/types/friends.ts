export interface Friend {
  friendshipId: string;
  userId: string;
  name: string | null;
  email: string;
  createdAt: string;
}

export interface FriendRequest {
  requestId: string;
  userId: string;
  name: string | null;
  email: string;
  createdAt: string;
}

/**
 * Pre-friendship search result. The backend deliberately omits email: a stranger must not be
 * able to harvest contact details by name lookup.
 */
export interface UserSearchResult {
  userId: string;
  name: string | null;
}

export interface SendFriendRequestPayload {
  recipientUserId: string;
}
