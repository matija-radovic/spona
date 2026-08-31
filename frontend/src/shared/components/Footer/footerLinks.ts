export interface FooterLink {
  labelKey: string
  href: string
}

export const FOOTER_LINKS: readonly FooterLink[] = [
  { labelKey: 'footer.about', href: '#' },
  { labelKey: 'footer.cookiePolicy', href: '#' },
  { labelKey: 'footer.privacyPolicy', href: '#' },
  { labelKey: 'footer.userAgreement', href: '#' },
  { labelKey: 'footer.license', href: 'https://opensource.org/licenses/MIT' },
]
