import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import { useAuthorizedFetch } from '../auth/useAuthorizedFetch'
import {
  fetchDasGuides,
  payDasGuide,
  type DasGuide,
  type PayDasRequest,
} from './das-api'

const dasQueryKey = (year: number) => ['das', year] as const

export function useDasGuidesQuery(year: number) {
  const { token } = useAuth()
  const authorizedFetch = useAuthorizedFetch()
  return useQuery({
    queryKey: dasQueryKey(year),
    queryFn: () => authorizedFetch((t) => fetchDasGuides(t, year)),
    enabled: Boolean(token),
  })
}

export function usePayDasMutation(year: number) {
  const authorizedFetch = useAuthorizedFetch()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: PayDasRequest }) =>
      authorizedFetch((t) => payDasGuide(t, id, request)),
    onSuccess: (paid) => {
      queryClient.setQueryData<DasGuide[]>(dasQueryKey(year), (guides) =>
        guides?.map((guide) => (guide.id === paid.id ? paid : guide)),
      )
    },
  })
}

/** Reloads the guides, e.g. when the server says one was already paid. */
export function useRefreshDasGuides(year: number) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: dasQueryKey(year) })
}
