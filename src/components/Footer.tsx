import { MaxContact } from '@/components/MaxContact';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { SITE, trackGoal } from '@/lib/site';
const logo = '/logo.png';
export function Footer() {
  const { t } = useTranslation();
  const links = [['/apartments/', t('navigation.apartments')], ['/business-travel/', t('upgrade.businessNav')], ['/long-stay/', t('upgrade.longNav')], ['/offers/', t('navigation.offers')], ['/about/', t('navigation.about')], ['/owners/', t('navigation.owners')], ['/contacts/', t('navigation.contacts')]];
  return <footer className="border-t bg-surface"><div className="container mx-auto px-4 py-10"><div className="grid gap-8 md:grid-cols-3"><div><Link to="/" className="flex items-center gap-3"><img src={logo} alt="" width="48" height="48" className="h-12 w-12 object-contain" /><span><strong className="block">Волшебно тут</strong><span className="text-sm text-muted-foreground">Amazy Apart · {t('upgrade.moscow')}</span></span></Link><p className="mt-4 text-sm text-muted-foreground">{t('upgrade.directNote')}</p></div><nav aria-label={t('footer.quickLinks')}><h2 className="font-semibold">{t('footer.quickLinks')}</h2><ul className="mt-4 grid gap-2 text-sm">{links.map(([href,label]) => <li key={href}><Link to={href} className="text-muted-foreground hover:underline">{label}</Link></li>)}</ul></nav><div><h2 className="font-semibold">{t('footer.contactInfo')}</h2><a href={`tel:${SITE.phone}`} className="mt-4 block font-semibold" onClick={() => trackGoal('phone_click')}>{SITE.displayPhone}</a><a href={`mailto:${SITE.email}`} className="mt-3 block break-all text-sm">{SITE.email}</a><div className="mt-4 flex flex-wrap items-center gap-5 text-sm"><a href={SITE.telegram} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('telegram_click')}>Telegram</a><a href={SITE.whatsapp} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('whatsapp_click')}>WhatsApp</a><MaxContact /></div></div></div><p className="mt-8 border-t pt-6 text-sm text-muted-foreground">© {new Date().getFullYear()} {SITE.brand}. {t('footer.copyright')}</p></div></footer>;
}
export default Footer;
