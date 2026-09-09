/* eslint-disable react-refresh/only-export-components -- Re-export barrel: it defines no component of its own, so it is never a fast-refresh boundary. */
import DefaultLayout from "./-layout/DefaultLayout/DefaultLayout";
import AuthLayout from "./-layout/AuthLayout/AuthLayout";
export { DefaultLayout, AuthLayout }

export { Header } from './chrome/Header'
export { Footer } from './chrome/Footer'
export { Logo } from './chrome/Logo'
export { Section } from './chrome/Section'
export { SectionLayout } from './chrome/SectionLayout'
export { SectionNav, type SectionItem } from './chrome/SectionNav'
export { SectionPanel } from './chrome/SectionPanel'
export { AuthCard } from './chrome/AuthCard'
export { ScrollContainer } from './chrome/ScrollContainer'
export { PlaceholderPage } from './chrome/PlaceholderPage'
export { RailLegal } from './chrome/RailLegal'

export { Button, ButtonVariant, ButtonSize } from './form/Button'
export { SaveBar } from './form/SaveBar'
export { FieldShell } from './form/FieldShell'
export { useFieldContext, type FieldContextValue } from './form/FieldShell/fieldContext'
export { FieldGroup } from './form/FieldGroup'
export { Input, InputShape } from './form/Input'
export { TagInput } from './form/TagInput'
export { SelectTrigger } from './form/SelectTrigger'
export { Dropdown, type DropdownNoMatch, type DropdownOption } from './form/Dropdown'
export { AvatarPicker } from './form/AvatarPicker'
export {
  ContactInfoWidget,
  ContactValue,
  contactTypeOption,
  contactHref,
  ContactType,
  type ContactEntry,
} from './form/ContactInfo'

export { Modal } from './overlay/Modal'
export { ConfirmDialog } from './overlay/ConfirmDialog'
export { UnsavedChangesModal } from './overlay/UnsavedChangesModal'
export { Tooltip, TooltipAlign } from './overlay/Tooltip'
export { ActionMenu, MenuAlign, contentMenuItems, type ActionMenuItem } from './overlay/ActionMenu'

export { ActorAvatar, ActorAvatarSize } from './actor/ActorAvatar'
export { ActorByline } from './actor/ActorByline'
export { ActorRow, ActorRowVariant } from './actor/ActorRow'
export { FollowButton } from './actor/FollowButton'
export { PostAsSelect, PostAsSurface, type PostAsValue } from './actor/PostAsSelect'

export { PostCard, PostCardVariant } from './content/PostCard'
export { ContentEditor } from './content/ContentEditor'
export { ReactionPicker } from './content/ReactionPicker'
export { ReactionSummary } from './content/ReactionSummary'
export { CopyLinkButton } from './content/CopyLinkButton'
