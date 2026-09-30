import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowRight, ShieldCheck, Tag, MessageCircle } from 'lucide-react';
import { Button } from './ui/button';
import { SITE, trackGoal, whatsappDraft } from '@/lib/site';

export function DirectBooking({ compact = false, showClaim = false }: { compact?: boolean; showClaim?: boolean }) {
  const { t } = useTranslation();
  return <aside className={`rounded-2xl border border-primary/20 bg-warm text-foreground ${compact ? 'p-5' : 'p-6 md:p-8'}`} aria-label={t('guarantee.why')}>
    <h2 className="text-2xl font-bold">{t('guarantee.why')}</h2>
    <div className={`mt-6 grid gap-6 ${compact ? '' : 'md:grid-cols-3'}`}>
      <div><ShieldCheck className="mb-3 text-primary" aria-hidden="true" /><h3 className="font-semibold">{t('guarantee.title')}</h3><p className="mt-2 text-sm leading-relaxed">{t('guarantee.terms')}</p></div>
      <div><Tag className="mb-3 text-primary" aria-hidden="true" /><h3 className="font-semibold">{t('guarantee.discountTitle')}</h3><p className="mt-2 text-sm leading-relaxed">{t('guarantee.discountText')}</p><code className="mt-3 inline-block rounded border border-primary/30 bg-white px-3 py-1 font-bold">{SITE.firstBooking.code}</code></div>
      <div><MessageCircle className="mb-3 text-primary" aria-hidden="true" /><h3 className="font-semibold">{t('guarantee.contactTitle')}</h3><p className="mt-2 text-sm leading-relaxed">{t('guarantee.contactText')}</p><Link to="/contacts/" className="mt-3 inline-block text-sm underline underline-offset-4">{t('navigation.contacts')}</Link></div>
    </div>
    <div className="mt-6 flex flex-wrap items-center gap-4"><Button asChild size="lg" className="h-auto min-h-11 whitespace-normal py-3"><Link to="/apartments/" onClick={() => trackGoal('booking_click')}>{t('guarantee.cta')}<ArrowRight className="ml-2 h-4 w-4 shrink-0" /></Link></Button><Link to="/offers/" className="text-sm underline underline-offset-4">{t('upgrade.offerTerms')}</Link></div>
    {showClaim && <div className="mt-6 border-t border-primary/20 pt-6"><h3 className="font-semibold">{t('guarantee.claim')}</h3><p className="mt-2 max-w-3xl text-sm leading-relaxed">{t('guarantee.claimText')}</p><a className="mt-3 inline-block font-semibold underline underline-offset-4" href={whatsappDraft(t('guarantee.claimDraft'))} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('whatsapp_click')}>{t('guarantee.claimCta')}</a></div>}
  </aside>;
}
