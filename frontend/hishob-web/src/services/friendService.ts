import api from './api';
import type {
  Friend,
  FriendRequest,
  SendFriendRequestPayload,
  UserSearchResult,
} from '@/types/friends';

export async function getFriends(): Promise<Friend[]> {
  const response = await api.get<Friend[]>('/api/friends');
  return response.data;
}

export async function getIncomingRequests(): Promise<FriendRequest[]> {
  const response = await api.get<FriendRequest[]>('/api/friends/requests/incoming');
  return response.data;
}

export async function getOutgoingRequests(): Promise<FriendRequest[]> {
  const response = await api.get<FriendRequest[]>('/api/friends/requests/outgoing');
  return response.data;
}

export async function sendFriendRequest(
  recipientUserId: string
): Promise<FriendRequest> {
  const payload: SendFriendRequestPayload = { recipientUserId };
  const response = await api.post<FriendRequest>('/api/friends/requests', payload);
  return response.data;
}

export async function acceptFriendRequest(requestId: string): Promise<Friend> {
  const response = await api.post<Friend>(
    `/api/friends/requests/${requestId}/accept`
  );
  return response.data;
}

export async function rejectFriendRequest(requestId: string): Promise<void> {
  await api.post<void>(`/api/friends/requests/${requestId}/reject`);
}

export async function cancelFriendRequest(requestId: string): Promise<void> {
  await api.delete<void>(`/api/friends/requests/${requestId}/cancel`);
}

export async function removeFriend(friendUserId: string): Promise<void> {
  await api.delete<void>(`/api/friends/${friendUserId}`);
}

export async function searchUsers(query: string): Promise<UserSearchResult[]> {
  const response = await api.get<UserSearchResult[]>('/api/users/search', {
    params: { q: query },
  });
  return response.data;
}

export async function blockFriend(targetUserId: string): Promise<void> {
  await api.post<void>(`/api/friends/${targetUserId}/block`);
}
