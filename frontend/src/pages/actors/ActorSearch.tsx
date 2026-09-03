import './ActorSearch.scss'
import { useState, type ChangeEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconSearch, IconBuildingFactory2, IconUsers } from '@tabler/icons-react'
import { Input, InputShape } from '@shared/components'
import useDebounce from '@shared/hooks/useDebounce'
import { ActorResults } from './components/ActorResults'

interface ActorSearchProps {
  type: 'org' | 'user'
}

const ActorSearch = ({ type }: ActorSearchProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const [value, setValue] = useState(() => searchParams.get('q') ?? '')
  const debounced = useDebounce(value.trim(), 350)

  const onInput = (event: ChangeEvent<HTMLInputElement>) => {
    const next = event.target.value
    setValue(next)
    const params = new URLSearchParams(searchParams)
    if (next.trim()) params.set('q', next.trim())
    else params.delete('q')
    setSearchParams(params, { replace: true })
  }

  const switchMode = (nextType: 'org' | 'user') => {
    if (nextType === type) return
    const pathname = nextType === 'org' ? '/companies' : '/users'
    const query = value.trim()
    navigate({ pathname, search: query ? `?q=${encodeURIComponent(query)}` : '' })
  }

  return (
    <div className="actor-search container">
      <div className="actor-search-bar">
        <Input
          className="actor-search-input"
          shape={InputShape.PILL}
          leading={<IconSearch aria-hidden />}
          type="search"
          value={value}
          onChange={onInput}
          placeholder={t('actors.search.placeholder')}
          enterKeyHint="search"
          aria-label={t('actors.search.placeholder')}
        />

        <div className="actor-search-modes" role="group" aria-label={t('nav.profileMenu')}>
          <button
            type="button"
            className={`actor-search-mode${type === 'org' ? ' is-active' : ''}`}
            onClick={() => switchMode('org')}
            aria-pressed={type === 'org'}
            aria-label={t('actors.search.modeCompanies')}
          >
            <IconBuildingFactory2 aria-hidden />
          </button>
          <button
            type="button"
            className={`actor-search-mode${type === 'user' ? ' is-active' : ''}`}
            onClick={() => switchMode('user')}
            aria-pressed={type === 'user'}
            aria-label={t('actors.search.modeUsers')}
          >
            <IconUsers aria-hidden />
          </button>
        </div>
      </div>

      {debounced ? (
        <ActorResults key={`${type}::${debounced}`} query={debounced} type={type} />
      ) : (
        <p className="actor-search-hint">{t('actors.search.hint')}</p>
      )}
    </div>
  )
}

export default ActorSearch
