declare global {
  interface Window {
    homereserve?: {
      initWidgetSearch: (config: { token: string }) => void | Promise<void>;
      initWidgetList: (config: { token: string }) => void | Promise<void>;
    };
  }
}
let pending: Promise<NonNullable<Window['homereserve']>> | undefined;
let failedLoads = 0;
export function loadBookingWidget() {
  if (window.homereserve) return Promise.resolve(window.homereserve);
  if (pending) return pending;
  const attempt = new Promise<NonNullable<Window['homereserve']>>((resolve, reject) => {
    // A failed module URL remains cached by the browser. Retry with a fresh URL.
    const src = `https://homereserve.ru/widget.js${failedLoads ? `?retry=${failedLoads}` : ''}`;
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${src}"]`);
    const script = existing || document.createElement('script');
    let timeout: ReturnType<typeof setTimeout>;
    const clean = () => { clearTimeout(timeout); script.removeEventListener('load', ready); script.removeEventListener('error', fail); };
    const fail = () => { clean(); script.remove(); failedLoads += 1; reject(new Error('Booking widget unavailable')); };
    const ready = () => { if (!window.homereserve) { fail(); return; } clean(); resolve(window.homereserve); };
    script.addEventListener('load', ready, { once: true });
    script.addEventListener('error', fail, { once: true });
    timeout = setTimeout(fail, 15000);
    if (!existing) { script.type = 'module'; script.src = src; document.head.appendChild(script); }
  });
  pending = attempt.catch(error => { pending = undefined; throw error; });
  return pending;
}
