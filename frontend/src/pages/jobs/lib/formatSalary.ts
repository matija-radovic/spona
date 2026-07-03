import type { Salary, SalaryPeriod } from '@utils/api/jobs'

type Translate = (key: string, opts?: Record<string, unknown>) => string

const PERIOD_KEY: Record<SalaryPeriod, string> = {
  HOUR: 'jobs.salary.perHour',
  DAY: 'jobs.salary.perDay',
  WEEK: 'jobs.salary.perWeek',
  MONTH: 'jobs.salary.perMonth',
  JOB: 'jobs.salary.perJob',
}

const groupThousands = (n: number): string => n.toLocaleString('sr-RS')

/**
 * Shared salary formatter (job-page.md §5.4). Honors a `displayText` override;
 * otherwise renders a single figure or an inclusive range, plus currency and a
 * cadence label. Used by both the card and the detail panel.
 */
export const formatSalary = (salary: Salary, t: Translate): string => {
  if (salary.displayText) return salary.displayText

  const amount =
    salary.max === null
      ? groupThousands(salary.min)
      : t('jobs.salary.range', { min: groupThousands(salary.min), max: groupThousands(salary.max) })

  return `${amount} ${salary.currency} ${t(PERIOD_KEY[salary.period])}`
}
