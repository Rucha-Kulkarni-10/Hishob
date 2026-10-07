/**
 * Group Service API client.
 *
 * Every call routes through the centralized Axios `api` instance,
 * which targets the API Gateway (http://localhost:8080) and attaches
 * the user's Bearer token automatically.
 *
 * Backend endpoints: GroupController.java  @RequestMapping("/api/groups")
 */

import type {
  AddMemberRequest,
  CreateGroupRequest,
  GroupResponse,
  MemberResponse,
} from '@/types/groups';
import api from './api';

/** Create a new group. The caller becomes the group OWNER.  201 CREATED */
export async function createGroup(data: CreateGroupRequest): Promise<GroupResponse> {
  const response = await api.post<GroupResponse>('/api/groups', data);
  return response.data;
}

/** List every group the authenticated user is a member/owner of. */
export async function listMyGroups(): Promise<GroupResponse[]> {
  const response = await api.get<GroupResponse[]>('/api/groups');
  return response.data;
}

/** Fetch the full detail payload for a single group by UUID. */
export async function getGroupDetail(groupId: string): Promise<GroupResponse> {
  const response = await api.get<GroupResponse>(`/api/groups/${groupId}`);
  return response.data;
}

/** List members of a group (requires caller to be a member). */
export async function listMembers(groupId: string): Promise<MemberResponse[]> {
  const response = await api.get<MemberResponse[]>(`/api/groups/${groupId}/members`);
  return response.data;
}

/**
 * Add a user to a group. Requires the caller to have OWNER role.
 * `userId` must be the user's UUID (not an email).
 */
export async function addMember(
  groupId: string,
  data: AddMemberRequest
): Promise<MemberResponse> {
  const response = await api.post<MemberResponse>(
    `/api/groups/${groupId}/members`,
    data
  );
  return response.data;
}
