import './SectionLayout.scss'
import { Outlet } from 'react-router-dom'
import { SectionNav, type SectionItem } from '@shared/components/chrome/SectionNav'

interface SectionLayoutProps<TContext> {
  sections: readonly SectionItem[]
  /** Handed to every section through `useOutletContext`. */
  context: TContext
  navLabel: string
  className?: string
}

const SectionLayout = <TContext,>({
  sections,
  context,
  navLabel,
  className,
}: SectionLayoutProps<TContext>) => (
  <div className={['section-layout', className].filter(Boolean).join(' ')}>
    <SectionNav sections={sections} label={navLabel} />

    <div className="section-layout-main">
      <Outlet context={context} />
    </div>
  </div>
)

export default SectionLayout
