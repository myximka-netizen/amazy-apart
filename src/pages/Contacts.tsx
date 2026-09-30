import { MaxContact } from '@/components/MaxContact';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { Phone, Mail, MessageCircle, Copy, MapPin } from 'lucide-react';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { SEO } from '@/components/SEO';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { type Area } from '@/components/LocationLinks';
import { SITE, siteUrl, whatsappDraft, trackGoal } from '@/lib/site';
import { generateOrganizationData, generateBreadcrumbData } from '@/components/StructuredData';
export default function Contacts() {
  const { t, i18n } = useTranslation();
  const [params] = useSearchParams();
  const [name, setName] = useState('');
  const [message, setMessage] = useState('');
  const [topic, setTopic] = useState(params.get('topic') || 'booking');
  const [draft, setDraft] = useState('');
  const [copyStatus, setCopyStatus] = useState('');
  const topics = [['booking', t('navigation.apartments')], ['business', t('upgrade.businessCard')], ['long-stay', t('upgrade.longCard')], ['other', t('contact.yourMessage')]];
  const areas = t('upgrade.locations', { returnObjects: true }) as Area[];
  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const text = `${t('upgrade.name')}: ${name.trim()}\n${t('upgrade.topic')}: ${topics.find(([value]) => value === topic)?.[1] || topic}\n\n${message.trim()}`;
    const url = whatsappDraft(text);
    setDraft(url); trackGoal('contact_draft');
    window.open(url, '_blank', 'noopener,noreferrer');
  };
  const copy = async () => { try { await navigator.clipboard.writeText(SITE.phone); setCopyStatus(t('upgrade.copied')); } catch { setCopyStatus(t('upgrade.copyFailed')); } };
  return <div className="min-h-screen bg-background"><SEO title={t('upgrade.contactTitle')} description={t('upgrade.contactIntro')} structuredData={[{ '@context': 'https://schema.org', '@type': 'ContactPage', name: t('upgrade.contactTitle'), url: siteUrl('/contacts/', i18n.language), mainEntity: generateOrganizationData() }, generateBreadcrumbData([{ name: t('navigation.home'), url: siteUrl('/', i18n.language) }, { name: t('navigation.contacts'), url: siteUrl('/contacts/', i18n.language) }])]} /><Header />
    <main id="main-content"><section className="bg-warm py-12"><div className="container mx-auto px-4"><h1 className="text-3xl font-bold md:text-5xl">{t('upgrade.contactTitle')}</h1><p className="mt-5 max-w-3xl text-lg text-muted-foreground">{t('upgrade.contactIntro')}</p></div></section>
      <section className="container mx-auto grid gap-5 px-4 py-10 md:grid-cols-3">
        <article className="rounded-2xl border p-6"><Phone className="mb-4 text-primary" /><h2 className="font-semibold">{t('upgrade.phoneLabel')}</h2><a className="mt-3 block text-xl font-bold" href={`tel:${SITE.phone}`} onClick={() => trackGoal('phone_click')}>{SITE.displayPhone}</a><Button variant="ghost" className="mt-2 px-0" onClick={copy}><Copy size={16} className="mr-2" />{t('upgrade.copy')}</Button><p role="status" className="text-sm text-muted-foreground">{copyStatus}</p></article>
        <article className="rounded-2xl border p-6"><MessageCircle className="mb-4 text-primary" /><h2 className="font-semibold">{t('contact.messengersTitle')}</h2><div className="mt-4 flex flex-wrap gap-3"><Button asChild variant="outline"><a href={SITE.telegram} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('telegram_click')}>Telegram</a></Button><Button asChild variant="outline"><a href={SITE.whatsapp} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('whatsapp_click')}>WhatsApp</a></Button><MaxContact className="min-h-10 rounded-md border px-4 py-2 text-sm" /></div><p className="mt-4 text-sm text-muted-foreground">MAX: {SITE.maxDisplayPhone}</p></article>
        <article className="rounded-2xl border p-6"><Mail className="mb-4 text-primary" /><h2 className="font-semibold">{t('upgrade.emailLabel')}</h2><a className="mt-3 block break-all underline" href={`mailto:${SITE.email}`}>{SITE.email}</a><p className="mt-3 text-sm text-muted-foreground">{t('upgrade.supportLabel')}</p><a className="mt-1 block break-all text-sm underline" href={`mailto:${SITE.supportEmail}`}>{SITE.supportEmail}</a></article>
      </section>
      <section className="container mx-auto grid gap-10 px-4 pb-16 lg:grid-cols-2"><div className="rounded-2xl bg-surface p-6 md:p-8"><h2 className="text-2xl font-bold">{t('upgrade.formTitle')}</h2><p className="mt-3 text-muted-foreground">{t('upgrade.formIntro')}</p>
        <form onSubmit={submit} className="mt-6 space-y-5"><div><Label htmlFor="contact-name">{t('upgrade.name')}</Label><Input id="contact-name" autoComplete="name" maxLength={100} value={name} onChange={e => setName(e.target.value)} required /></div><div><Label htmlFor="contact-topic">{t('upgrade.topic')}</Label><select id="contact-topic" value={topic} onChange={e => setTopic(e.target.value)} className="mt-1 h-11 w-full rounded-md border bg-background px-3">{topics.map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></div><div><Label htmlFor="contact-message">{t('upgrade.message')}</Label><Textarea id="contact-message" value={message} onChange={e => setMessage(e.target.value)} rows={5} maxLength={3000} required /></div><Button type="submit" size="lg" className="w-full">{t('upgrade.draftButton')}</Button></form>
        {draft && <div role="status" className="mt-5 rounded-xl border bg-background p-4"><p>{t('upgrade.draftNotice')}</p><a href={draft} target="_blank" rel="noopener noreferrer" className="mt-3 inline-block font-semibold underline">{t('upgrade.openDraft')}</a></div>}
      </div><div><h2 className="text-2xl font-bold">{t('upgrade.mapTitle')}</h2><p className="mt-3 text-muted-foreground">{t('upgrade.mapDescription')}</p><div className="mt-6 grid gap-4">{areas.map(area => <a key={area.slug} href={`https://yandex.ru/maps/?text=${encodeURIComponent(area.query)}`} target="_blank" rel="noopener noreferrer" className="flex items-start gap-3 rounded-2xl border p-5 hover:border-primary"><MapPin className="shrink-0 text-primary" /><span><strong className="block">{area.title}</strong><span className="mt-1 block text-sm text-muted-foreground">{t('upgrade.map')}</span></span></a>)}</div></div></section>
    </main><Footer /></div>;
}
