import { apiFetch } from '../../lib/http'

/**
 * Contract proposed to the backend (RN05, OF14, module `das`). Monetary
 * values are JSON numbers in BRL; dates are ISO 8601 (YYYY-MM-DD).
 */
export type DasStatus = 'PENDING' | 'OVERDUE' | 'PAID'

export type DasGuide = {
  id: string
  /** Month the guide refers to (YYYY-MM). */
  competence: string
  /** Day 20 of the following month, adjusted to the next business day (RN05). */
  dueDate: string
  amount: number
  /** OVERDUE: not paid and past the due date. */
  status: DasStatus
  paidAt: string | null
}

export type PayDasRequest = {
  paidAt: string
}

/** GET /das?year=YYYY -> 200 DasGuide[], the 12 competences from January to December. */
export function fetchDasGuides(token: string | null, year: number) {
  const query = new URLSearchParams({ year: String(year) })
  return apiFetch<DasGuide[]>(`/das?${query}`, { token })
}

/**
 * POST /das/{id}/payment -> 200 DasGuide
 *   400 VALIDATION_ERROR with fieldErrors (e.g. future paidAt)
 *   404 when the guide belongs to another user
 *   409 DAS_ALREADY_PAID
 */
export function payDasGuide(
  token: string | null,
  id: string,
  request: PayDasRequest,
) {
  return apiFetch<DasGuide>(`/das/${encodeURIComponent(id)}/payment`, {
    token,
    method: 'POST',
    body: JSON.stringify(request),
  })
}
