import './SettingsLayout.scss'
import { Outlet } from 'react-router-dom'
import { SettingsSidebar } from './components/SettingsSidebar'
import type { SettingsSection } from './lib/sections'
import type { SettingsOutletContext } from './lib/settingsOutletContext'

interface SettingsLayoutProps {
  sections: readonly SettingsSection[]
  /** The bootstrap DTO + refetch, handed to every section via `useOutletContext`. */
  context: SettingsOutletContext
}

const SettingsLayout = ({ sections, context }: SettingsLayoutProps) => (
  <div className="settings-layout container">
    <SettingsSidebar sections={sections} />

    <div className="settings-layout-main">
      <Outlet context={context} />
    </div>
  </div>
)

export default SettingsLayout
