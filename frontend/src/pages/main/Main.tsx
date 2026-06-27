import './Main.scss'
import { useTranslation } from 'react-i18next'
import { motion } from 'motion/react'
import { Section } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/Button'
import { POPULAR_JOB_ROLES, jobsFilterPath } from '@utils/constants/jobRoles'

const reveal = {
  initial: { opacity: 0, y: 24 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: '-80px' },
  transition: { duration: 0.5, ease: 'easeOut' },
} as const

interface EmployerStep {
  titleKey: string
  descKey: string
}

const EMPLOYER_STEPS: readonly EmployerStep[] = [
  { titleKey: 'home.employersStep1Title', descKey: 'home.employersStep1Desc' },
  { titleKey: 'home.employersStep2Title', descKey: 'home.employersStep2Desc' },
  { titleKey: 'home.employersStep3Title', descKey: 'home.employersStep3Desc' },
]

const COMMUNITY_FEATURES: readonly string[] = [
  'home.communityFeed',
  'home.communityFollow',
  'home.communityOrgs',
]

const Main = () => {
  const { t } = useTranslation()

  return (
    <div className="home">
      <Section className="home-hero" isContainer>
        <div className="home-hero-copy">
          <h1>{t('home.heroTitle')}</h1>
          <p className="home-lead">{t('home.heroSubtitle')}</p>
          <div className="home-hero-actions">
            <Button to="/register" variant={ButtonVariant.PRIMARY}>
              {t('home.heroPrimaryCta')}
            </Button>
            <Button to="/register" variant={ButtonVariant.SECONDARY}>
              {t('home.heroSecondaryCta')}
            </Button>
          </div>
        </div>

        <div className="home-hero-image">
          <img src="" alt="" />
        </div>
      </Section>

      <Section className="home-section" isContainer>
        <motion.div {...reveal}>
          <h2>{t('home.rolesTitle')}</h2>
          <p className="home-lead">{t('home.rolesSubtitle')}</p>

          <div className="home-roles">
            {POPULAR_JOB_ROLES.map((role) => (
              <Button
                key={role.slug}
                to={jobsFilterPath(role.slug)}
                variant={ButtonVariant.QUATERNARY}
              >
                {role.label}
              </Button>
            ))}
          </div>

          <div className="home-section-cta">
            <Button to="/jobs" variant={ButtonVariant.TERTIARY}>
              {t('home.rolesAll')}
            </Button>
          </div>
        </motion.div>
      </Section>

      <Section className="home-section" isContainer>
        <motion.div {...reveal}>
          <h2>{t('home.employersTitle')}</h2>
          <p className="home-lead">{t('home.employersSubtitle')}</p>

          <ol className="home-steps">
            {EMPLOYER_STEPS.map(({ titleKey, descKey }, index) => (
              <li key={titleKey} className="home-step">
                <span className="home-step-num">{index + 1}</span>
                <h3>{t(titleKey)}</h3>
                <p>{t(descKey)}</p>
              </li>
            ))}
          </ol>

          <div className="home-section-cta">
            <Button to="/register?org=1" variant={ButtonVariant.SECONDARY}>
              {t('home.employersCta')}
            </Button>
          </div>
        </motion.div>
      </Section>

      <Section className="home-section home-section--split" isContainer>
        <motion.div className="home-community-copy" {...reveal}>
          <h2>{t('home.communityTitle')}</h2>
          <p className="home-lead">{t('home.communitySubtitle')}</p>

          <ul className="home-feature-list">
            {COMMUNITY_FEATURES.map((key) => (
              <li key={key}>{t(key)}</li>
            ))}
          </ul>

          <div className="home-section-cta home-section-cta--left">
            <Button to="/companies" variant={ButtonVariant.TERTIARY}>
              {t('home.communityCta')}
            </Button>
          </div>
        </motion.div>

        {/* Community visual - replace with a screenshot/illustration. */}
        <div className="home-community-image">
          <img src="" alt="" />
        </div>
      </Section>

      <Section className="home-section home-final" isContainer>
        <motion.div {...reveal}>
          <h2>{t('home.finalTitle')}</h2>
          <p className="home-lead">{t('home.finalSubtitle')}</p>
          <div className="home-hero-actions home-hero-actions--center">
            <Button to="/register" variant={ButtonVariant.PRIMARY}>
              {t('home.finalPrimaryCta')}
            </Button>
            <Button to="/login" variant={ButtonVariant.GHOST}>
              {t('home.finalSecondaryCta')}
            </Button>
          </div>
        </motion.div>
      </Section>
    </div>
  )
}

export default Main
