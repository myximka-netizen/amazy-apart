// Obfuscation avoids literal numbers in the shipped JavaScript. It is reversible;
// the owner explicitly keeps public phone/WhatsApp link destinations unchanged.
const phone = atob('Kzc5OTU1MDg1ODA4');
const maxPhone = atob('Kzc5OTk5OTQ3MzU0');
export const SITE = {
  origin: 'https://amazy-apart.ru', brand: 'Волшебно тут | Amazy Apart',
  phone,
  email: 'info@volshebno-tut.ru', supportEmail: 'support@volshebno-tut.ru',
  telegram: 'https://t.me/Volshebno_tyt', whatsapp: `https://wa.me/${phone.slice(1)}`,
  maxPhone,
  // Profile link supplied by the owner; keep the complete case-sensitive URL.
  maxProfileUrl: 'https://max.ru/u/f9LHodD0cOIwf5cut6Q6zehywppvSEDtNHLjrHEdFoocJ4wMC6UtJJZ7TJk' as string,
  logo: 'https://amazy-apart.ru/logo.png',
  socialImage: 'https://amazy-apart.ru/og-image.png',
  bookingToken: 'HYkUIAGFQD',
  // Existing public offer from the supplied site's translations.
  firstBooking: { discount: 5, code: 'Online5' },
} as const;
export const languages = ['ru', 'en', 'zh'] as const;
export type Language = typeof languages[number];
export function languageFromPath(path: string): Language {
  return /^\/en(?:\/|$)/.test(path) ? 'en' : /^\/zh(?:\/|$)/.test(path) ? 'zh' : 'ru';
}
export function stripLanguage(path: string) { return path.replace(/^\/(en|zh)(?=\/|$)/, '') || '/'; }
export function localizedPath(path: string, language: string) {
  const clean = stripLanguage(path).replace(/\/+$/, '') || '/';
  return `${language === 'en' || language === 'zh' ? `/${language}` : ''}${clean === '/' ? '/' : `${clean}/`}`;
}
export function siteUrl(path: string, language = 'ru') { return `${SITE.origin}${localizedPath(path, language)}`; }
export function whatsappDraft(message: string) { return `${SITE.whatsapp}?text=${encodeURIComponent(message)}`; }
export type Goal = 'booking_click' | 'phone_click' | 'telegram_click' | 'whatsapp_click' | 'corporate_request' | 'long_stay_request' | 'contact_draft' | 'max_click' | 'max_phone_copy';
export function trackGoal(goal: Goal) {
  if (typeof window !== 'undefined' && typeof window.ym === 'function') window.ym(104380673, 'reachGoal', goal);
}
