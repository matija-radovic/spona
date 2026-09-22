import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import type { ApiFieldError, ProblemDetail } from './types'

type Translate = (key: string | string[], opts?: Record<string, unknown>) => string

const SERVER_ERROR = 'errors.serverError'
const INVALID_VALUE = 'errors.validation.invalid'

/** A code the catalog lacks falls back to the generic message, never to the English `detail` */
export const problemMessage = (problem: ProblemDetail | null | undefined, t: Translate): string =>
  problem?.code ? t([`errors.${problem.code}`, SERVER_ERROR], problem.args) : t(SERVER_ERROR)

const fieldMessage = (error: ApiFieldError, t: Translate): string =>
  t([`errors.${error.code}`, INVALID_VALUE], error.args)

/** Sets each field error the server named on the form field of that name, and says whether there was any */
export const applyFieldErrors = <T extends FieldValues>(
  problem: ProblemDetail,
  setError: UseFormSetError<T>,
  t: Translate,
): boolean => {
  const entries = Object.entries(problem.errors ?? {})
  entries.forEach(([field, error]) =>
    setError(field.replace(/\[(\d+)\]/g, '.$1') as Path<T>, {
      type: 'server',
      message: fieldMessage(error, t),
    }),
  )
  return entries.length > 0
}
