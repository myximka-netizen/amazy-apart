import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { Shield, Clock, MapPin, Users, Star, Heart } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { SEO } from "@/components/SEO";
import { Breadcrumbs } from "@/components/Breadcrumbs";
import { generateArticleData, generateBreadcrumbData } from "@/components/StructuredData";

const About = () => {
  const { t } = useTranslation();
  const stats = [{
    number: "2500+",
    label: t('about.stats.guests')
  }, {
    number: "4.9/5",
    label: t('about.stats.rating')
  }, {
    number: "24/7",
    label: t('about.stats.support')
  }];
  const values = [{
    icon: Heart,
    title: t('about.values.care.title'),
    description: t('about.values.care.description')
  }, {
    icon: Shield,
    title: t('about.values.honesty.title'),
    description: t('about.values.honesty.description')
  }, {
    icon: Star,
    title: t('about.values.quality.title'),
    description: t('about.values.quality.description')
  }];
  return <div className="min-h-screen bg-background">
      <SEO
        title={t('about.seo.title')}
        description={t('about.seo.description')}
        keywords="о компании, аренда апартаментов москва, история компании, честная аренда жилья"
        type="article"
        structuredData={[
          generateArticleData({
            title: t('about.seo.title'),
            description: t('about.seo.description'),
            datePublished: "2020-01-01",
            dateModified: "2025-10-03",
            image: "https://amazy-apart.ru/og-image.jpg"
          }),
          generateBreadcrumbData([
            { name: "Главная", url: "https://amazy-apart.ru/" },
            { name: "О нас", url: "https://amazy-apart.ru/about" }
          ])
        ]}
      />
      <Header />

      {/* Hero Section */}
      <section id="main-content" className="bg-gradient-hero py-12 md:py-16">
        <div className="container mx-auto px-4">
          <Breadcrumbs items={[{ label: t('about.title') }]} />
        </div>
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto text-center space-y-6">
            <h1 className="text-4xl md:text-6xl font-bold text-foreground">
              {t('about.title')}
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              {t('about.description')}
            </p>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="py-16 bg-surface">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('about.achievements.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('about.achievements.subtitle')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {stats.map((stat, index) => <div key={index} className="text-center">
                  <div className="text-4xl md:text-5xl font-bold text-luxury mb-2">
                    {stat.number}
                  </div>
                  <div className="text-muted-foreground">{stat.label}</div>
                </div>)}
            </div>
          </div>
        </div>
      </section>

      {/* Values */}
      <section className="py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('about.values.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('about.values.description')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {values.map((value, index) => <Card key={index} className="text-center border-0 shadow-card hover:shadow-floating transition-shadow">
                  <CardContent className="p-8">
                    <div className="w-16 h-16 mx-auto mb-6 bg-gradient-luxury rounded-2xl flex items-center justify-center">
                      <value.icon className="h-8 w-8 text-luxury-foreground" />
                    </div>
                    <h3 className="text-xl font-semibold text-foreground mb-4">
                      {value.title}
                    </h3>
                    <p className="text-muted-foreground">
                      {value.description}
                    </p>
                  </CardContent>
                </Card>)}
            </div>
          </div>
        </div>
      </section>

      {/* Our Story */}
      <section className="py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-8">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('about.story.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('about.story.description')}
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            <div className="space-y-6">
                <p className="text-muted-foreground leading-relaxed">
                  {t('about.story.content1')}
                </p>
                <p className="text-muted-foreground leading-relaxed">
                  {t('about.story.content2')}
                </p>
                <p className="text-muted-foreground leading-relaxed">
                  {t('about.story.content3')}
                </p>
              </div>

              <div className="relative">
                <div className="bg-gradient-luxury rounded-2xl p-8 text-luxury-foreground">
                  <blockquote className="text-lg italic">
                    "{t('about.quote.text')}"
                  </blockquote>
                  <footer className="mt-4">
                    <cite className="font-semibold">{t('about.quote.author')}</cite>
                  </footer>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>


      <Footer />
    </div>;
};
export default About;
