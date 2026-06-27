import type { Icon } from '@tabler/icons-react'
import { IconPhone, IconMail, IconPrinter, IconDots } from '@tabler/icons-react'

export interface ContactTypeOption {
  key: string
  icon: Icon
  labelKey: string
  hintKey: string
}

export const CONTACT_TYPE_OPTIONS: readonly ContactTypeOption[] = [
  { key: 'phone', icon: IconPhone, labelKey: 'auth.contact.types.phone', hintKey: 'auth.contact.hints.phone' },
  { key: 'email', icon: IconMail, labelKey: 'auth.contact.types.email', hintKey: 'auth.contact.hints.email' },
  { key: 'fax', icon: IconPrinter, labelKey: 'auth.contact.types.fax', hintKey: 'auth.contact.hints.fax' },
  { key: 'other', icon: IconDots, labelKey: 'auth.contact.types.other', hintKey: 'auth.contact.hints.other' },
]

export const contactTypeOption = (key: string): ContactTypeOption =>
  CONTACT_TYPE_OPTIONS.find((option) => option.key === key) ?? CONTACT_TYPE_OPTIONS[CONTACT_TYPE_OPTIONS.length - 1]
