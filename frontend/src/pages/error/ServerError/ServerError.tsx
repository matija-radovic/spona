import { useEffect } from 'react'
import { useRouteError } from 'react-router-dom'
import { ErrorScreen } from '../ErrorScreen'

const ServerError = () => {
  const error = useRouteError()

  useEffect(() => {
    console.error(error)
  }, [error])

  return <ErrorScreen code={500} />
}

export default ServerError
