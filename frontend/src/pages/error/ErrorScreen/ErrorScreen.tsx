import './ErrorScreen.scss'
import { useMemo } from 'react'
import { Trans } from 'react-i18next'
import { Link } from 'react-router-dom'
import { getRandomCat } from '@utils/constants/cats'

interface ErrorScreenProps {
  code: number
}

const ErrorScreen = ({ code }: ErrorScreenProps) => {
  const cat = useMemo(() => getRandomCat(), [])

  return (
    <div className="error-screen">
      <img className="error-screen-cat" src={cat} alt="" />
      <h1 className="error-screen-code">{code}</h1>
      <p className="error-screen-back">
        <Trans i18nKey="errors.page.back" components={{ home: <Link to="/" /> }} />
      </p>
    </div>
  )
}

export default ErrorScreen
