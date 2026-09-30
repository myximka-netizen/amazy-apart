import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { SEO } from '@/components/SEO';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { ListWidget } from '@/components/ListWidget';
import { LocationLinks, type Area } from '@/components/LocationLinks';
import { Button } from '@/components/ui/button';
import { generateBreadcrumbData } from '@/components/StructuredData';
import { siteUrl } from '@/lib/site';
import NotFound from './NotFound';
export default function Location() {
  const { slug } = useParams();
  const { t, i18n } = useTranslation();
  const area = (t('upgrade.locations', { returnObjects: true }) as Area[]).find(a => a.slug === slug);
  if (!area) return <NotFound />;
  return <div className="min-h-screen bg-background"><SEO title={area.title} description={`${area.intro} ${t('upgrade.directNote')}`} structuredData={[generateBreadcrumbData([{ name: t('navigation.home'), url: siteUrl('/', i18n.language) }, { name: t('navigation.apartments'), url: siteUrl('/apartments/', i18n.language) }, { name: area.title, url: siteUrl(`/apartments/${area.slug}/`, i18n.language) }])]} /><Header />
    <main id="main-content"><section className="bg-warm py-12"><div className="container mx-auto px-4"><Breadcrumbs items={[{ label: t('navigation.apartments'), path: '/apartments/' }, { label: area.title }]} /><h1 className="max-w-4xl text-3xl font-bold md:text-5xl">{area.title}</h1><p className="mt-5 max-w-3xl text-lg text-muted-foreground">{area.intro}</p></div></section>
    <section className="container mx-auto px-4 py-10"><div className="grid gap-8 lg:grid-cols-[2fr_1fr]"><p className="max-w-3xl leading-relaxed text-muted-foreground">{area.body}</p><div className="flex flex-col items-start gap-4"><Button asChild variant="outline"><a href={`https://yandex.ru/maps/?text=${encodeURIComponent(area.query)}`} target="_blank" rel="noopener noreferrer">{t('upgrade.map')}</a></Button><Link to="/business-travel/" className="underline">{t('upgrade.businessCard')}</Link><Link to="/long-stay/" className="underline">{t('upgrade.longCard')}</Link></div></div></section>
    <section className="container mx-auto px-4 pb-12"><h2 className="text-2xl font-bold">{t('upgrade.allApartments')}</h2><p className="my-4 text-muted-foreground">{t('upgrade.locationHint')}</p><ListWidget /></section><LocationLinks /></main><Footer /></div>;
}
