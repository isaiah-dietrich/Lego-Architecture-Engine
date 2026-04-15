import { useQuery } from '@tanstack/react-query'
import { pollJob } from '../api/jobs'
import type { JobStatusResponse } from '../types/api'

/**
 * Polls a job until it reaches a terminal state (done or error).
 * Refetches every 1.5 s while the job is active; stops automatically on completion.
 *
 * @param jobId  the job to poll, or null to skip polling
 */
export function useJobPoller(jobId: string | null) {
  return useQuery<JobStatusResponse>({
    queryKey: ['job', jobId],
    queryFn: () => pollJob(jobId!),
    enabled: jobId !== null,
    refetchInterval(query) {
      const status = query.state.data?.status
      if (status === 'done' || status === 'error') return false
      return 1500
    },
    retry: 2,
  })
}
