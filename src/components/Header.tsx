import { PhoneNumber } from '@/components/PhoneNumber';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Menu, X, Home, Building2, Info, Phone, Users, Gift } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';
import { LanguageSwitcher } from './LanguageSwitcher';
import { SITE, trackGoal } from '@/lib/site';

export function Header() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const { t } = useTranslation();
  useEffect(() => setOpen(false), [location.pathname]);
  const navigation = [
    { label: t('navigation.home'), href: '/', icon: Home },
    { label: t('navigation.apartments'), href: '/apartments/', icon: Building2 },
    { label: t('navigation.about'), href: '/about/', icon: Info },
    { label: t('navigation.contacts'), href: '/contacts/', icon: Phone },
    { label: t('navigation.owners'), href: '/owners/', icon: Users },
    { label: t('navigation.offers'), href: '/offers/', icon: Gift },
  ];
  const isCurrent = (href: string) => location.pathname.replace(/\/$/, '') === href.replace(/\/$/, '');

  return <header className="sticky top-0 z-50 w-full border-b bg-background/80 backdrop-blur-md">
    <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-[60] focus:bg-white focus:p-4">{t('upgrade.skip')}</a>
    <div className="container mx-auto px-4">
      <div className="flex min-h-16 items-center justify-between gap-2 xl:gap-3">
        <Link to="/" className="flex shrink-0 items-center gap-2 xl:gap-3" aria-label={SITE.brand}>
          <img src="/logo.png?v=2" alt="" className="h-10 w-10 object-contain rounded-md bg-[#faf7f2] sm:h-14 sm:w-14" width="56" height="56" />
          <span className="flex flex-col"><span className="font-heading text-base sm:text-xl font-bold leading-snug">Волшебно тут</span><span className="text-xs sm:text-sm text-muted-foreground">Amazy Apart · {t('upgrade.moscow')}</span></span>
        </Link>
        <nav aria-label={t('upgrade.menu')} className="hidden lg:flex items-center justify-end gap-0.5 xl:gap-1">
          {navigation.map(({ href, label, icon: Icon }) => <Link key={href} to={href} aria-current={isCurrent(href) ? 'page' : undefined} className="flex items-center whitespace-nowrap rounded-lg px-2 py-2 text-[13px] xl:px-3 xl:text-sm font-medium text-muted-foreground transition-colors hover:text-foreground hover:bg-surface aria-[current=page]:bg-warm aria-[current=page]:text-warm-foreground"><Icon className="mr-2 hidden h-4 w-4 xl:block" aria-hidden="true" />{label}</Link>)}
        </nav>
        <div className="flex shrink-0 items-center gap-1">
          <LanguageSwitcher />
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t(open ? 'upgrade.closeMenu' : 'upgrade.menu')} aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen(!open)}>{open ? <X /> : <Menu />}</Button>
        </div>
      </div>
      {open && <nav id="mobile-navigation" aria-label={t('upgrade.menu')} className="lg:hidden grid gap-1 border-t py-4">
        {navigation.map(({ href, label, icon: Icon }) => <Link key={href} to={href} aria-current={isCurrent(href) ? 'page' : undefined} className="flex items-center rounded-lg px-4 py-3 text-muted-foreground hover:text-foreground hover:bg-surface aria-[current=page]:bg-warm aria-[current=page]:text-warm-foreground"><Icon className="mr-3 h-5 w-5" aria-hidden="true" />{label}</Link>)}
        <a href={`tel:${SITE.phone}`} className="px-4 py-3 font-semibold" onClick={() => trackGoal('phone_click')}><PhoneNumber /></a>
      </nav>}
    </div>
  </header>;
}
