import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const STORAGE_KEY = 'spona:last-section'

/**
 * Sections that "stick": once a logged-out visitor opens one of these, it
 * becomes their landing spot instead of the onboarding home page.
 */
export const REMEMBERED_SECTIONS = ['/jobs', '/companies'] as const

export const getLastSection = (): string | null => {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export const clearLastSection = (): void => {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* storage unavailable - ignore */
  }
}

/**
 * Module-level flag, reset on every full page (re)load. It distinguishes the
 * very first render of a page-load session from later in-session navigation,
 * so the "remember last section" redirect only happens when the visitor lands
 * on `/` fresh - not when they deliberately click back to the home page.
 */
let initialLandingPending = true

/**
 * Pure read - `true` while we're still on the initial page-load render, before
 * the app shell has committed. Used by the index route to decide whether `/`
 * should redirect to the remembered section. Never mutates, so it stays safe
 * under StrictMode's double-render in development.
 */
export const isInitialLanding = (): boolean => initialLandingPending

/**
 * Marks the initial page load as handled. Called from the app shell's mount
 * effect (after the first commit), so any later visit to `/` is treated as
 * in-session navigation and shows home instead of redirecting.
 */
export const markInitialLoadDone = (): void => {
  initialLandingPending = false
}

/**
 * Persists the most recent rememberable section the visitor lands on, so the
 * index route can redirect them straight back there next time.
 */
export const useTrackLastSection = (): void => {
  const { pathname } = useLocation()

  useEffect(() => {
    if ((REMEMBERED_SECTIONS as readonly string[]).includes(pathname)) {
      try {
        localStorage.setItem(STORAGE_KEY, pathname)
      } catch {
        /* storage unavailable - ignore */
      }
    }
  }, [pathname])
}
