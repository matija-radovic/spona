import { replace, type LoaderFunctionArgs } from 'react-router-dom'

/** drops last segment until smth non 404able is reached */
export const unknownRouteLoader = ({ request }: LoaderFunctionArgs) => {
  const url = new URL(request.url)
  const parent = url.pathname.replace(/\/+$/, '').replace(/\/[^/]+$/, '')

  if (!parent) return null
  return replace(`${parent}${url.search}${url.hash}`)
}
