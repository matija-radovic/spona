import { formatNumber } from '@utils/format'
import type { Salary, SalaryPeriod } from '@utils/api/jobs'

type Translate = (key: string, opts?: Record<string, unknown>) => string

const PERIOD_KEY: Record<SalaryPeriod, string> = {
  HOUR: 'jobs.salary.perHour',
  DAY: 'jobs.salary.perDay',
  WEEK: 'jobs.salary.perWeek',
  MONTH: 'jobs.salary.perMonth',
  JOB: 'jobs.salary.perJob',
}

/** A `displayText` override wins; otherwise a single figure or an inclusive range. */
export const formatSalary = (salary: Salary, t: Translate, locale: string): string => {
  if (salary.displayText) return salary.displayText

  const groupThousands = (n: number): string => formatNumber(n, locale)

  const amount =
    salary.max === null
      ? groupThousands(salary.min)
      : t('jobs.salary.range', { min: groupThousands(salary.min), max: groupThousands(salary.max) })

  return `${amount} ${salary.currency} ${t(PERIOD_KEY[salary.period])}`
}
