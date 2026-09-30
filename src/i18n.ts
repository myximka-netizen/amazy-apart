import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import ru from './locales/ru.json';
import zh from './locales/zh.json';
import { languageFromPath } from './lib/site';
i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, ru: { translation: ru }, zh: { translation: zh } },
  lng: typeof window === 'undefined' ? 'ru' : languageFromPath(window.location.pathname),
  fallbackLng: 'ru', supportedLngs: ['ru', 'en', 'zh'], interpolation: { escapeValue: false },
});
export default i18n;
