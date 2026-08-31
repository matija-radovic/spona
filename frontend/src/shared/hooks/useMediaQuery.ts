import { useSyncExternalStore } from 'react'

interface QueryStore {
  subscribe: (onStoreChange: () => void) => () => void
  getSnapshot: () => boolean
}

const stores = new Map<string, QueryStore>()

const getStore = (query: string): QueryStore => {
  const cached = stores.get(query)
  if (cached) return cached

  const mql = window.matchMedia(query)
  const listeners = new Set<() => void>()
  mql.addEventListener('change', () => listeners.forEach((notify) => notify()))

  const store: QueryStore = {
    subscribe: (onStoreChange) => {
      listeners.add(onStoreChange)
      return () => {
        listeners.delete(onStoreChange)
      }
    },
    getSnapshot: () => mql.matches,
  }

  stores.set(query, store)
  return store
}

export const matchesQuery = (query: string) => getStore(query).getSnapshot()

/** Pass a string from MQ object in mediaqueries.ts or a custom query*/
const useMediaQuery = (query: string) => {
  const store = getStore(query)
  return useSyncExternalStore(store.subscribe, store.getSnapshot)
}

export default useMediaQuery
