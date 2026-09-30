import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Menu, X, Phone } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';
import { LanguageSwitcher } from './LanguageSwitcher';
import { SITE, trackGoal } from '@/lib/site';
const logo = '/logo.png';
export function Header() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const { t } = useTranslation();
  useEffect(() => setOpen(false), [location.pathname]);
  const links = [['/apartments/', t('navigation.apartments')], ['/business-travel/', t('upgrade.businessNav')], ['/long-stay/', t('upgrade.longNav')], ['/contacts/', t('navigation.contacts')]];
  return <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur-md">
    <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-[60] focus:bg-white focus:p-4">{t('upgrade.skip')}</a>
    <div className="container mx-auto px-4"><div className="flex min-h-20 items-center justify-between gap-3">
      <Link to="/" className="flex shrink-0 items-center gap-2" aria-label={SITE.brand}>
        <img src={logo} alt="" className="h-9 w-9 object-contain sm:h-14 sm:w-14" width="56" height="56" />
        <span className="flex flex-col"><span className="text-base sm:text-lg font-bold leading-snug">Волшебно тут</span><span className="text-xs sm:text-sm text-muted-foreground">Amazy Apart · {t('upgrade.moscow')}</span></span>
      </Link>
      <nav aria-label={t('upgrade.menu')} className="hidden xl:flex items-center gap-1">{links.map(([href, label]) => <Link key={href} to={href} aria-current={location.pathname.replace(/\/$/, '') === href.replace(/\/$/, '') ? 'page' : undefined} className="rounded-lg px-3 py-3 text-sm font-medium hover:bg-accent aria-[current=page]:bg-accent">{label}</Link>)}</nav>
      <div className="flex items-center gap-1 sm:gap-3">
        <a href={`tel:${SITE.phone}`} onClick={() => trackGoal('phone_click')} className="hidden lg:flex items-center gap-2 text-sm font-semibold"><Phone size={16} />{SITE.displayPhone}</a>
        <LanguageSwitcher />
        <Button className="hidden sm:inline-flex" asChild><Link to="/apartments/" onClick={() => trackGoal('booking_click')}>{t('upgrade.book')}</Link></Button>
        <Button variant="ghost" size="icon" className="xl:hidden" aria-label={t(open ? 'upgrade.closeMenu' : 'upgrade.menu')} aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen(!open)}>{open ? <X /> : <Menu />}</Button>
      </div>
    </div>
    {open && <nav id="mobile-navigation" aria-label={t('upgrade.menu')} className="xl:hidden grid gap-1 border-t py-4">{[...links, ['/about/', t('navigation.about')], ['/offers/', t('navigation.offers')], ['/owners/', t('navigation.owners')]].map(([href, label]) => <Link key={href} to={href} className="rounded-lg px-4 py-3 hover:bg-accent">{label}</Link>)}<a href={`tel:${SITE.phone}`} className="px-4 py-3 font-semibold" onClick={() => trackGoal('phone_click')}>{SITE.displayPhone}</a></nav>}
    </div>
  </header>;
}
