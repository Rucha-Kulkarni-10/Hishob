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

export interface UserSearchResult {
  userId: string;
  name: string | null;
  email: string;
}

export interface SendFriendRequestPayload {
  recipientUserId: string;
}
