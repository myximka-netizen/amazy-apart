import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';
import { loadBookingWidget } from '@/lib/homereserve';
import { SITE, trackGoal } from '@/lib/site';
export function BookingWidget({ kind, className }: { kind: 'search' | 'list'; className?: string }) {
  const { t } = useTranslation();
  const mount = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setStatus('loading');
    const node = mount.current;
    node?.replaceChildren();
    const observer = new MutationObserver(() => { if (active && node?.childNodes.length) setStatus('ready'); });
    if (node) observer.observe(node, { childList: true, subtree: true });
    const timeout = setTimeout(() => { if (active && !node?.childNodes.length) setStatus('error'); }, 18000);
    loadBookingWidget().then(async api => {
      if (!active) return;
      const method = kind === 'search' ? api.initWidgetSearch : api.initWidgetList;
      if (typeof method !== 'function') throw new Error('Widget method unavailable');
      await method.call(api, { token: SITE.bookingToken });
    }).catch(() => { if (active) setStatus('error'); });
    return () => { active = false; clearTimeout(timeout); observer.disconnect(); node?.replaceChildren(); };
  }, [kind, attempt]);
  return <div className={className}>
    <div id="hr-widget" ref={mount} className="w-full" />
    {status !== 'ready' && <div className="rounded-xl border bg-background p-5 text-foreground" role="status">
      <p>{t(status === 'error' ? 'upgrade.searchError' : 'upgrade.searchLoading')}</p>
      {status === 'error' && <Button variant="outline" className="mt-4" onClick={() => setAttempt(a => a + 1)}>{t('upgrade.searchRetry')}</Button>}
    </div>}
    <div className="mt-4 flex flex-wrap justify-center items-center gap-4 text-sm">
      <Button asChild variant={kind === 'search' ? 'default' : 'outline'} className="h-auto min-h-11 whitespace-normal py-3"><a href={`https://homereserve.ru/${SITE.bookingToken}`} target="_blank" rel="noopener noreferrer" onClick={() => trackGoal('booking_click')}>{t(kind === 'search' ? 'guarantee.cta' : 'upgrade.allApartments')}</a></Button>
      <a href={SITE.telegram} target="_blank" rel="noopener noreferrer" className="underline underline-offset-4" onClick={() => trackGoal('telegram_click')}>{t('upgrade.contactFallback')}</a>
    </div>
  </div>;
}
