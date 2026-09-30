import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowUpRight, MapPin } from 'lucide-react';
export type Area = { slug: string; title: string; intro: string; body: string; query: string };
export function LocationLinks() {
  const { t } = useTranslation();
  const areas = t('upgrade.locations', { returnObjects: true }) as Area[];
  return <section className="py-12" aria-labelledby="location-heading"><div className="container mx-auto px-4">
    <h2 id="location-heading" className="text-3xl font-bold">{t('upgrade.locationsTitle')}</h2>
    <p className="mt-3 max-w-3xl text-muted-foreground">{t('upgrade.locationsIntro')}</p>
    <div className="mt-6 grid gap-4 md:grid-cols-3">{areas.map(area => <Link key={area.slug} to={`/apartments/${area.slug}/`} className="group flex items-start gap-3 rounded-2xl border bg-surface p-6 transition-colors hover:border-primary">
      <MapPin className="mt-1 h-5 w-5 shrink-0 text-primary" /><span className="flex-1"><h3 className="text-lg font-semibold">{area.title}</h3><p className="mt-2 text-sm text-muted-foreground">{area.intro}</p></span><ArrowUpRight className="h-5 w-5 shrink-0 text-primary" />
    </Link>)}</div>
  </div></section>;
}
