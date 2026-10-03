import { useTranslation } from 'react-i18next';

// Outlined artwork keeps the visible number out of DOM text and attributes.
// Phone links remain readable by design; this is not complete anti-scraping protection.
export function PhoneNumber({ kind = 'main' }: { kind?: 'main' | 'max' }) {
  const { t } = useTranslation();
  const mask = `url('/contact-${kind}.svg')`;
  return <span
    role="img"
    aria-label={kind === 'main' ? t('upgrade.phoneLabel') : `${t('upgrade.phoneLabel')} MAX`}
    data-phone-display={kind}
    style={{
      display: 'inline-block', width: `${kind === 'main' ? 9.10791 : 9.19385}em`, height: '1em',
      verticalAlign: '-0.2em', backgroundColor: 'currentColor',
      maskImage: mask, WebkitMaskImage: mask,
      maskSize: '100% 100%', WebkitMaskSize: '100% 100%',
      maskRepeat: 'no-repeat', WebkitMaskRepeat: 'no-repeat',
    }}
  />;
}
