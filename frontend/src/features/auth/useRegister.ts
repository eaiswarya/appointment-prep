import { useMutation } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { components } from '../../api/schema'
import { ApiError } from '../../lib/errors'

export type RegisterRequest = components['schemas']['RegisterRequest']

/** POST /api/auth/register. Rejects with ApiError on any non-2xx response. */
export function useRegister() {
  return useMutation({
    mutationFn: async (body: RegisterRequest) => {
      const { data, error, response } = await api.POST('/auth/register', { body })
      if (error !== undefined || !response.ok) {
        throw new ApiError(response.status, error)
      }
      return data
    },
  })
}
