const KEY = 'spona:auth-token'

export const getToken = (): string | null => {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

export const setToken = (token: string): void => {
  try {
    localStorage.setItem(KEY, token)
  } catch {
    /* storage unavailable - the session simply does not persist */
  }
}

export const clearToken = (): void => {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* storage unavailable - nothing to clear */
  }
}
