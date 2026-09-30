import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Check, ArrowRight } from 'lucide-react';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { SEO } from '@/components/SEO';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { Button } from '@/components/ui/button';
import { LocationLinks } from '@/components/LocationLinks';
import { siteUrl, whatsappDraft, trackGoal } from '@/lib/site';
import { generateBreadcrumbData } from '@/components/StructuredData';
export default function Stay({ business = false }: { business?: boolean }) {
  const { t, i18n } = useTranslation();
  const prefix = business ? 'business' : 'long';
  const title = t(`upgrade.${prefix}Title`);
  const intro = t(`upgrade.${prefix}Intro`);
  const points = t(`upgrade.${business ? 'businessPoints' : 'longPlans'}`, { returnObjects: true }) as { title: string; text: string }[];
  const conditions = t('upgrade.conditions', { returnObjects: true }) as string[];
  const action = <Button asChild size="lg"><a href={whatsappDraft(t(`upgrade.${prefix}Draft`))} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal(business ? 'corporate_request' : 'long_stay_request')}>{t(`upgrade.${prefix}Cta`)}<ArrowRight size={18} className="ml-2" /></a></Button>;
  return <div className="min-h-screen bg-background"><SEO title={title} description={intro} structuredData={[generateBreadcrumbData([{ name: t('navigation.home'), url: siteUrl('/', i18n.language) }, { name: title, url: siteUrl(business ? '/business-travel/' : '/long-stay/', i18n.language) }])]} /><Header />
    <main id="main-content">
      <section className="bg-warm py-12 md:py-16"><div className="container mx-auto px-4"><Breadcrumbs items={[{ label: title }]} /><div className="max-w-3xl"><p className="mb-4 text-sm font-semibold uppercase tracking-wider text-primary">Amazy Apart · {t('upgrade.moscow')}</p><h1 className="text-3xl font-bold leading-tight md:text-5xl">{title}</h1><p className="mt-6 mb-8 text-lg text-muted-foreground">{intro}</p>{action}</div></div></section>
      <section className="container mx-auto px-4 py-12"><div className="grid gap-5 md:grid-cols-2 lg:grid-cols-4">{points.map(point => <article key={point.title} className="rounded-2xl border p-6"><h2 className="text-xl font-semibold">{point.title}</h2><p className="mt-3 text-muted-foreground">{point.text}</p></article>)}</div>
        {!business && <p className="mt-6 max-w-4xl text-muted-foreground">{t('upgrade.longService')} <Link to="/offers/" className="underline">{t('upgrade.offerTerms')}</Link></p>}
      </section>
      <section className="container mx-auto px-4 pb-12"><div className="grid gap-8 rounded-2xl bg-surface p-6 md:grid-cols-2 md:p-8"><div><h2 className="text-2xl font-bold">{t(business ? 'upgrade.requestTitle' : 'upgrade.conditionsTitle')}</h2><ul className="mt-5 space-y-4">{(business ? t('upgrade.businessSteps', { returnObjects: true }) as string[] : conditions).map(line => <li key={line} className="flex gap-3"><Check className="mt-1 h-5 w-5 shrink-0 text-primary" /><span>{line}</span></li>)}</ul></div><div className="flex flex-col items-start justify-center gap-4">{action}<Button asChild variant="outline"><Link to="/apartments/">{t('upgrade.allApartments')}</Link></Button><p className="text-sm text-muted-foreground">{t('upgrade.directNote')}</p></div></div></section>
      <LocationLinks />
    </main><Footer /></div>;
}
