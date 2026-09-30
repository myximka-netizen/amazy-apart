import { useTranslation } from 'react-i18next';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { SEO } from '@/components/SEO';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { generateBreadcrumbData } from '@/components/StructuredData';
import { ListWidget } from '@/components/ListWidget';
import { DirectBooking } from '@/components/DirectBooking';
import { LocationLinks } from '@/components/LocationLinks';
import { siteUrl } from '@/lib/site';
export default function Apartments() {
  const { t, i18n } = useTranslation();
  return <div className="min-h-screen bg-background"><SEO title={t('upgrade.catalogTitle')} description={t('upgrade.seo.catalogDescription')} structuredData={[generateBreadcrumbData([{ name: t('navigation.home'), url: siteUrl('/', i18n.language) }, { name: t('navigation.apartments'), url: siteUrl('/apartments/', i18n.language) }])]} /><Header />
    <main id="main-content"><div className="container mx-auto px-4 py-10"><Breadcrumbs items={[{ label: t('navigation.apartments') }]} /><h1 className="text-3xl font-bold md:text-5xl">{t('upgrade.catalogTitle')}</h1><p className="mt-5 max-w-3xl text-lg text-muted-foreground">{t('upgrade.catalogIntro')}</p><div className="my-8"><DirectBooking compact /></div><ListWidget className="w-full" /></div><LocationLinks /></main><Footer /></div>;
}
