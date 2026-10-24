/**
 * Combined dashboard overview types.
 *
 * Mirrors expense-service DashboardOverviewResponse. Group and personal scopes are
 * separate systems; combined is their disjoint sum (an expense is counted once).
 *
 * Metric meanings (never interchangeable):
 *   recordedSpend — full amount of every expense visible in the scope
 *   yourShare     — the user's portion from split rules (membership adds nothing)
 *   youPaid       — amount the user actually fronted (paidBy = user)
 */

export interface ScopeTotals {
  expenseCount: number;
  recordedSpend: number;
  yourShare: number;
  youPaid: number;
}

export interface DashboardOverviewResponse {
  group: ScopeTotals;
  personal: ScopeTotals;
  combined: ScopeTotals;
}
