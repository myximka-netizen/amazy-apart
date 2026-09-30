import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, MessageCircle } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { SITE, trackGoal } from '@/lib/site';

export function MaxContact({ className = '' }: { className?: string }) {
  const { t } = useTranslation();
  const [status, setStatus] = useState('');
  const style = `inline-flex items-center gap-2 text-left ${className}`;
  if (SITE.maxProfileUrl) return <a className={style} href={SITE.maxProfileUrl} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('max_click')}><MessageCircle className="h-5 w-5 shrink-0" aria-hidden="true" />{t('max.write')}</a>;
  async function copy() {
    try {
      await navigator.clipboard.writeText(SITE.maxPhone);
      setStatus(t('max.copied'));
      trackGoal('max_phone_copy');
    } catch { setStatus(t('max.failed')); }
  }
  return <Dialog onOpenChange={() => setStatus('')}>
    <DialogTrigger asChild><button type="button" className={style}><MessageCircle className="h-5 w-5 shrink-0" aria-hidden="true" />{t('max.contact')}</button></DialogTrigger>
    <DialogContent className="z-[120]"><DialogTitle>{t('max.contact')}</DialogTitle><DialogDescription>{t('max.description')}</DialogDescription><p className="select-all text-2xl font-bold">{SITE.maxDisplayPhone}</p><Button onClick={copy}><Copy className="mr-2 h-4 w-4" />{t('max.copy')}</Button><p role="status" aria-live="polite" className="text-sm">{status}</p><div className="flex flex-wrap gap-4 text-sm"><a className="underline" href={`tel:${SITE.phone}`} onClick={() => trackGoal('phone_click')}>{SITE.displayPhone}</a><a className="underline" href={SITE.telegram} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('telegram_click')}>Telegram</a></div></DialogContent>
  </Dialog>;
}
