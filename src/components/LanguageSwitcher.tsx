import { Globe } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from './ui/dropdown-menu';
import { localizedPath } from '@/lib/site';
const languages = [{ code: 'ru', name: 'Русский' }, { code: 'en', name: 'English' }, { code: 'zh', name: '中文' }];
export function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const location = useLocation();
  return <DropdownMenu>
    <DropdownMenuTrigger asChild><Button variant="ghost" size="sm" aria-label="Language / Язык" className="gap-2"><Globe className="h-4 w-4" /><span className="hidden sm:inline">{i18n.language.toUpperCase()}</span></Button></DropdownMenuTrigger>
    <DropdownMenuContent align="end">{languages.map(language => <DropdownMenuItem key={language.code} asChild>
      <a href={`${localizedPath(location.pathname, language.code)}${location.search}${location.hash}`} hrefLang={language.code} lang={language.code} aria-current={i18n.language === language.code ? 'true' : undefined}>{language.name}</a>
    </DropdownMenuItem>)}</DropdownMenuContent>
  </DropdownMenu>;
}
