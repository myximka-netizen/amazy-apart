import { SITE } from '@/lib/site';
import { useEffect, useRef } from "react";

interface StructuredDataProps {
  data: Record<string, any>;
}

export function StructuredData({ data }: StructuredDataProps) {
  const scriptRef = useRef<HTMLScriptElement | null>(null);

  useEffect(() => {
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.text = JSON.stringify(data);
    scriptRef.current = script;
    document.head.appendChild(script);

    return () => {
      if (scriptRef.current && document.head.contains(scriptRef.current)) {
        document.head.removeChild(scriptRef.current);
      }
    };
  }, [data]);

  return null;
}

export function generateOrganizationData() {
  return {
    '@context': 'https://schema.org', '@type': 'Organization',
    '@id': `${SITE.origin}/#organization`, name: 'Волшебно тут', alternateName: 'Amazy Apart',
    url: `${SITE.origin}/`, logo: SITE.logo,
    telephone: SITE.phone, email: SITE.email,
    contactPoint: { '@type': 'ContactPoint', telephone: SITE.phone, contactType: 'customer service', availableLanguage: ['Russian', 'English', 'Chinese'] },
    sameAs: [SITE.telegram, SITE.whatsapp, ...(SITE.maxProfileUrl ? [SITE.maxProfileUrl] : [])],
  };
}
export function generateWebSiteData() {
  return { '@context': 'https://schema.org', '@type': 'WebSite', '@id': `${SITE.origin}/#website`, name: 'Волшебно тут', alternateName: 'Amazy Apart', url: `${SITE.origin}/`, publisher: { '@id': `${SITE.origin}/#organization` } };
}
export function generateLocalBusinessData() {
  return { ...generateOrganizationData(), '@type': 'LodgingBusiness', '@id': `${SITE.origin}/#lodging`, image: SITE.socialImage, address: { '@type': 'PostalAddress', addressLocality: 'Москва', addressCountry: 'RU' } };
}

export function generateApartmentData(apartment: {
  id: string;
  title: string;
  description: string;
  price: number;
  location: string;
  guests: number;
  rating: number;
  image: string;
}) {
  return {
    "@context": "https://schema.org",
    "@type": "Product",
    "name": apartment.title,
    "image": apartment.image,
    "description": apartment.description,
    "offers": {
      "@type": "Offer",
      "price": apartment.price,
      "priceCurrency": "RUB",
      "availability": "https://schema.org/InStock",
      "url": `https://amazy-apart.ru/apartment/${apartment.id}/`
    },
    "address": {
      "@type": "PostalAddress",
      "addressLocality": "Moscow",
      "streetAddress": apartment.location,
      "addressCountry": "RU"
    }
  };
}

export function generateBreadcrumbData(items: Array<{ name: string; url: string }>) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    "itemListElement": items.map((item, index) => ({
      "@type": "ListItem",
      "position": index + 1,
      "name": item.name,
      "item": item.url
    }))
  };
}

export function generateArticleData(article: {
  title: string;
  description: string;
  datePublished: string;
  dateModified: string;
  image: string;
}) {
  return {
    "@context": "https://schema.org",
    "@type": "Article",
    "headline": article.title,
    "description": article.description,
    "image": article.image,
    "datePublished": article.datePublished,
    "dateModified": article.dateModified,
    "author": {
      "@type": "Organization",
      "name": "Волшебно тут"
    },
    "publisher": {
      "@type": "Organization",
      "name": "Волшебно тут",
      "logo": {
        "@type": "ImageObject",
        "url": SITE.logo
      }
    }
  };
}
