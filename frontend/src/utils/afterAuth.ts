const KEY = 'spona:after-auth'

export const rememberAfterAuth = (path: string): void => {
  try {
    sessionStorage.setItem(KEY, path)
  } catch {
    /* storage unavailable - they land on the default section instead */
  }
}

export const afterAuthPath = (): string | null => {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export const forgetAfterAuth = (): void => {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* storage unavailable - nothing to clear */
  }
}
