import { DirectBooking } from '@/components/DirectBooking';
import { SEO } from '@/components/SEO';
import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { Gift, Calendar, Percent, Star, Clock, Heart } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";

const Offers = () => {
  const { t } = useTranslation();

  const offerIcons = [Gift, Calendar, Clock, Heart];

  return (
    <div className="min-h-screen bg-background">
      <SEO title={t('upgrade.seo.offersTitle')} description={t('upgrade.seo.offersDescription')} />
      <Header />

      {/* Hero Section */}
      <section id="main-content" className="bg-gradient-hero py-12 md:py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto text-center space-y-6">
            <h1 className="text-4xl md:text-6xl font-bold text-foreground">
              {t('offers.hero.title')}
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              {t('offers.hero.subtitle')}
            </p>
          </div>
        </div>
      </section>

      <section className="container mx-auto px-4 pt-10"><DirectBooking showClaim /></section>

      {/* Main Offers */}
      <section className="py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('offers.current.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('offers.current.subtitle')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {(t('offers.list', { returnObjects: true }) as any[]).map((offer: any, index: number) => {
                const IconComponent = offerIcons[index] || Gift;
                const colors = [
                  "bg-gradient-to-r from-green-500 to-emerald-600",
                  "bg-gradient-to-r from-blue-500 to-blue-600",
                  "bg-gradient-to-r from-purple-500 to-purple-600",
                  "bg-gradient-to-r from-pink-500 to-rose-600"
                ];

                return (
                  <Card key={index} className="border-0 shadow-card hover:shadow-floating transition-all duration-300 overflow-hidden">
                    <div className={`h-2 ${colors[index]}`}></div>
                    <CardHeader className="pb-4">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                          <div className="w-12 h-12 bg-gradient-luxury rounded-xl flex items-center justify-center">
                            <IconComponent className="h-6 w-6 text-luxury-foreground" />
                          </div>
                          <CardTitle className="text-xl">{offer.title}</CardTitle>
                        </div>
                        <Badge variant="secondary" className="text-2xl font-bold px-4 py-2">
                          -{offer.discount}
                        </Badge>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <p className="text-muted-foreground mb-4">
                        {offer.description}
                      </p>
                      {offer.code && (
                        <div className="bg-surface p-4 rounded-lg">
                          <p className="text-sm text-muted-foreground mb-2">{t('offers.labels.promoCode')}</p>
                          <div className="flex items-center space-x-2">
                            <code className="bg-gradient-luxury text-luxury-foreground px-3 py-1 rounded font-mono font-bold">
                              {offer.code}
                            </code>
                            <span className="text-sm text-muted-foreground">
                              {t('offers.labels.enterWhenBooking')}
                            </span>
                          </div>
                        </div>
                      )}
                      {!offer.code && (
                        <div className="bg-surface p-4 rounded-lg">
                          <p className="text-sm font-medium text-green-600">
                            {t('offers.labels.appliedAutomatically')}
                          </p>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* Additional Benefits */}
      <section className="py-16 bg-surface">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('offers.benefits.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('offers.benefits.subtitle')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {(t('offers.benefits.list', { returnObjects: true }) as any[]).map((benefit: any, index: number) => (
                <Card key={index} className="text-center border-0 shadow-card">
                  <CardContent className="p-6">
                    <div className="w-16 h-16 mx-auto mb-4 bg-gradient-luxury rounded-2xl flex items-center justify-center">
                      {index === 0 ? <Star className="h-8 w-8 text-luxury-foreground" /> : <Gift className="h-8 w-8 text-luxury-foreground" />}
                    </div>
                    <h3 className="text-lg font-semibold text-foreground mb-3">
                      {benefit.title}
                    </h3>
                    <p className="text-muted-foreground text-sm">
                      {benefit.description}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* How to Use */}
      <section className="py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('offers.howToUse.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('offers.howToUse.subtitle')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {(t('offers.howToUse.steps', { returnObjects: true }) as any[]).map((step: any, index: number) => (
                <div key={index} className="text-center space-y-4">
                  <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center">
                    <span className="text-2xl font-bold text-luxury-foreground">{index + 1}</span>
                  </div>
                  <h3 className="text-xl font-semibold text-foreground">
                    {step.title}
                  </h3>
                  <p className="text-muted-foreground">
                    {step.description}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 bg-gradient-hero">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto text-center space-y-8">
            <h2 className="text-3xl md:text-4xl font-bold text-foreground">
              {t('offers.cta.title')}
            </h2>
            <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
              {t('offers.cta.subtitle')}
            </p>

            <Link to="/apartments">
              <Button size="lg" className="bg-gradient-luxury text-luxury-foreground hover:shadow-luxury hover:scale-105">
                {t('offers.cta.button')}
              </Button>
            </Link>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Offers;
