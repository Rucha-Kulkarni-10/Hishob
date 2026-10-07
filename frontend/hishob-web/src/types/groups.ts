/**
 * TypeScript types derived directly from the HISHOB Group Service DTOs.
 *
 * Backend sources:
 *   GroupResponse.java     → GroupResponse
 *   MemberResponse.java    → MemberResponse
 *   MemberRole.java        → MemberRole
 *   CreateGroupRequest.java→ CreateGroupRequest
 *   AddMemberRequest.java  → AddMemberRequest
 */

export type MemberRole = 'OWNER' | 'MEMBER';

/** Response shape for GET /api/groups and GET /api/groups/{id} */
export interface GroupResponse {
  id: string;          // UUID
  name: string;
  description: string | null;
  createdBy: string;   // UUID of owner/creator user
  createdAt: string;   // ISO-8601 Instant
  updatedAt: string;   // ISO-8601 Instant
}

/** Response shape for GET /api/groups/{id}/members and POST add-member */
export interface MemberResponse {
  id: string;          // GroupMember UUID
  userId: string;      // User UUID
  role: MemberRole;
  joinedAt: string;    // ISO-8601 Instant
  name: string;
  email: string;
}

/** POST /api/groups request body */
export interface CreateGroupRequest {
  name: string;           // 2..150 chars
  description?: string;   // ≤ 1000 chars, optional
}

/** POST /api/groups/{groupId}/members request body */
export interface AddMemberRequest {
  userId: string;   // UUID of user to add
}
