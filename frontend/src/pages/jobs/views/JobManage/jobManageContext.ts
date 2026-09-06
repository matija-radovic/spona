import { useOutletContext } from 'react-router-dom'
import type { JobDetailDto } from '@utils/api/jobs'

export interface JobManageOutletContext {
  job: JobDetailDto
  refetch: () => Promise<void>
}

export const useJobManageContext = () => useOutletContext<JobManageOutletContext>()
