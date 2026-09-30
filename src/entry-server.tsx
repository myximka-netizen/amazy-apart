import type { ComponentProps } from 'react';
import { renderToString } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import { TooltipProvider } from './components/ui/tooltip';
import { AppContent } from './App';
import i18n from './i18n';
import { languageFromPath } from './lib/site';
export async function render(url: string) {
  const language = languageFromPath(url);
  await i18n.changeLanguage(language);
  const context: ComponentProps<typeof HelmetProvider>['context'] = {};
  const html = renderToString(<HelmetProvider context={context}><TooltipProvider><StaticRouter location={url} basename={language === 'ru' ? '/' : `/${language}`}><AppContent /></StaticRouter></TooltipProvider></HelmetProvider>);
  const helmet = context.helmet!;
  return { html, head: helmet.title.toString() + helmet.meta.toString() + helmet.link.toString() + helmet.script.toString(), language };
}
