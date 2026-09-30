import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { SITE, languages, siteUrl } from '@/lib/site';
interface SEOProps {
  title: string; description: string; keywords?: string; canonical?: string;
  ogImage?: string; ogImageWidth?: number; ogImageHeight?: number; ogImageAlt?: string; ogSiteName?: string;
  type?: 'website' | 'article' | 'product'; structuredData?: Record<string, unknown>[];
  locale?: string; localeAlternates?: string[];
  productPrice?: string; productCurrency?: string; productAvailability?: string; noindex?: boolean;
}
export function SEO({ title, description, keywords, canonical, ogImage = SITE.socialImage, ogImageWidth, ogImageHeight, ogImageAlt, ogSiteName = SITE.brand, type = 'website', structuredData = [], productPrice, productCurrency = 'RUB', productAvailability, noindex = false }: SEOProps) {
  const { i18n } = useTranslation();
  const { pathname } = useLocation();
  const currentUrl = canonical || siteUrl(pathname, i18n.language);
  const fullTitle = `${title} | ${i18n.language === 'ru' ? 'Волшебно тут' : 'Amazy Apart'}`;
  const locales: Record<string, string> = { ru: 'ru_RU', en: 'en_US', zh: 'zh_CN' };
  return <Helmet htmlAttributes={{ lang: i18n.language }}>
    <title>{fullTitle}</title><meta name="description" content={description} />
    {keywords && <meta name="keywords" content={keywords} />}
    <meta name="robots" content={noindex ? 'noindex, follow' : 'index, follow, max-image-preview:large'} />
    <link rel="canonical" href={currentUrl} />
    {!noindex && languages.map(language => <link key={language} rel="alternate" hrefLang={language} href={siteUrl(pathname, language)} />)}
    {!noindex && <link rel="alternate" hrefLang="x-default" href={siteUrl(pathname, 'ru')} />}
    <meta property="og:site_name" content={ogSiteName} /><meta property="og:title" content={fullTitle} /><meta property="og:description" content={description} /><meta property="og:type" content={type} /><meta property="og:url" content={currentUrl} /><meta property="og:image" content={ogImage} />{(ogImageWidth || ogImage === SITE.socialImage) && <meta property="og:image:width" content={String(ogImageWidth || 1730)} />}{(ogImageHeight || ogImage === SITE.socialImage) && <meta property="og:image:height" content={String(ogImageHeight || 909)} />}<meta property="og:image:alt" content={ogImageAlt || description} /><meta property="og:locale" content={locales[i18n.language] || 'ru_RU'} />
    {languages.filter(l => l !== i18n.language).map(l => <meta key={l} property="og:locale:alternate" content={locales[l]} />)}
    <meta name="twitter:card" content="summary_large_image" /><meta name="twitter:title" content={fullTitle} /><meta name="twitter:description" content={description} /><meta name="twitter:image" content={ogImage} />
    {type === 'product' && productPrice && <meta property="product:price:amount" content={productPrice} />}
    {type === 'product' && productPrice && <meta property="product:price:currency" content={productCurrency} />}
    {type === 'product' && productAvailability && <meta property="product:availability" content={productAvailability} />}
    {structuredData.map((data, index) => <script key={index} type="application/ld+json">{JSON.stringify(data).replace(/</g, '\\u003c')}</script>)}
  </Helmet>;
}
