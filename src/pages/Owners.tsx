import { SEO } from '@/components/SEO';
import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { Shield, Clock, Star, DollarSign, Home, Users, Wrench, Paintbrush } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { useTranslation } from "react-i18next";
import { ImageComparisonSlider } from "@/components/ImageComparisonSlider";
import renovationBefore from "@/assets/renovation-before.jpg";
import renovationAfter from "@/assets/renovation-after.jpg";

const Owners = () => {
  const { t } = useTranslation();

  const renovationIcons = [Wrench, Paintbrush, Star];
  const statIcons = [DollarSign, Star, Shield];

  return (
    <div className="min-h-screen bg-background">
      <SEO title={t('upgrade.seo.ownersTitle')} description={t('upgrade.seo.ownersDescription')} />
      <Header />

      {/* Hero Section */}
      <section id="main-content" className="bg-gradient-hero py-12 md:py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto text-center space-y-6">
            <h1 className="text-4xl md:text-6xl font-bold text-foreground">
              {t('owners.hero.title')}
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              {t('owners.hero.subtitle')}
            </p>
          </div>
        </div>
      </section>

      {/* Renovation Services Section - Image Comparison */}
      <motion.section
        className="py-16 bg-gradient-warm"
        initial={{ opacity: 0, y: 50 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8 }}
      >
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <motion.h2
                className="text-3xl md:text-4xl font-bold text-foreground"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                {t('owners.renovation.title')}
              </motion.h2>
              <motion.p
                className="text-lg text-muted-foreground max-w-3xl mx-auto"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                {t('owners.renovation.subtitle')}
              </motion.p>
            </div>

            <motion.div
              className="max-w-4xl mx-auto rounded-2xl overflow-hidden shadow-floating"
              initial={{ opacity: 0, y: 50 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: 0.3 }}
            >
              <ImageComparisonSlider
                beforeImage={renovationBefore}
                afterImage={renovationAfter}
                beforeAlt="До ремонта"
                afterAlt="После ремонта"
              />
            </motion.div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mt-16">
              {(t('owners.renovation.services', { returnObjects: true }) as any[]).map((service: any, index: number) => {
                const IconComponent = renovationIcons[index] || Wrench;
                return (
                  <motion.div
                    key={index}
                    initial={{ opacity: 0, y: 30 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.6, delay: index * 0.2 }}
                  >
                    <Card className="text-center border-0 shadow-card hover:shadow-floating transition-all hover:scale-105">
                      <CardContent className="p-6">
                        <div className="w-16 h-16 mx-auto mb-6 bg-gradient-luxury rounded-2xl flex items-center justify-center">
                          <IconComponent className="h-8 w-8 text-luxury-foreground" />
                        </div>
                        <h3 className="text-xl font-semibold text-foreground mb-4">
                          {service.title}
                        </h3>
                        <p className="text-muted-foreground">
                          {service.description}
                        </p>
                      </CardContent>
                    </Card>
                  </motion.div>
                );
              })}
            </div>

            <div className="text-center pt-8">
              <motion.div
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.5 }}
              >
                <Link to="/contacts">
                  <Button size="lg" className="bg-gradient-luxury text-luxury-foreground hover:shadow-luxury hover:scale-105">
                    {t('owners.renovation.button')}
                  </Button>
                </Link>
              </motion.div>
            </div>
          </div>
        </div>
      </motion.section>

      {/* Problem Identification Section */}
      <motion.section
        className="py-16 bg-surface"
        initial={{ opacity: 0, y: 50 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8 }}
      >
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <motion.h2
                className="text-3xl md:text-4xl font-bold text-foreground"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                {t('owners.problems.title')}
              </motion.h2>
              <motion.p
                className="text-lg text-muted-foreground max-w-3xl mx-auto"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                {t('owners.problems.subtitle')}
              </motion.p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {(t('owners.problems.list', { returnObjects: true }) as string[]).map((problem: string, index: number) => (
                <motion.div
                  key={index}
                  className="bg-background p-6 rounded-lg border border-border hover:shadow-card transition-all"
                  initial={{ opacity: 0, y: 30 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.6, delay: index * 0.1 }}
                >
                  <div className="flex items-start space-x-3">
                    <div className="w-8 h-8 bg-gradient-luxury rounded-full flex items-center justify-center flex-shrink-0 mt-1">
                      <span className="text-luxury-foreground font-bold text-sm">{index + 1}</span>
                    </div>
                    <p className="text-foreground text-sm leading-relaxed">{problem}</p>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </motion.section>

      {/* Trust Statistics Section */}
      <motion.section
        className="py-16"
        initial={{ opacity: 0, y: 50 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8 }}
      >
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <motion.h2
                className="text-3xl md:text-4xl font-bold text-foreground"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                {t('owners.trustStats.title')}
              </motion.h2>
              <motion.p
                className="text-lg text-muted-foreground max-w-3xl mx-auto"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                {t('owners.trustStats.subtitle')}
              </motion.p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {[
                {
                  number: "100+",
                  text: t('owners.trustStats.stats.propertyValue'),
                  icon: statIcons[0]
                },
                {
                  number: "90%+",
                  text: t('owners.trustStats.stats.occupancy'),
                  icon: statIcons[1]
                },
                {
                  number: "50%",
                  text: t('owners.trustStats.stats.wearReduction'),
                  icon: statIcons[2]
                }
              ].map((stat, index) => (
                <motion.div
                  key={index}
                  className="text-center space-y-4"
                  initial={{ opacity: 0, y: 30 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.6, delay: index * 0.1 }}
                >
                  <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center">
                    <stat.icon className="h-8 w-8 text-luxury-foreground" />
                  </div>
                  <div className="text-3xl md:text-4xl font-bold text-primary">
                    {stat.number}
                  </div>
                  <p className="text-muted-foreground text-sm leading-relaxed">
                    {stat.text}
                  </p>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </motion.section>

      {/* Services Section */}
      <motion.section
        className="py-16 bg-gradient-warm"
        initial={{ opacity: 0, y: 50 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8 }}
      >
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <motion.h2
                className="text-3xl md:text-4xl font-bold text-foreground"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                {t('owners.services.title')}
              </motion.h2>
              <motion.p
                className="text-lg text-muted-foreground max-w-3xl mx-auto"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                {t('owners.services.subtitle')}
              </motion.p>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {(t('owners.services.list', { returnObjects: true }) as string[]).map((service: string, index: number) => (
                <motion.div
                  key={index}
                  className="bg-background/80 backdrop-blur-sm p-4 rounded-lg border border-border hover:shadow-card transition-all text-center"
                  initial={{ opacity: 0, y: 30 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.6, delay: index * 0.05 }}
                >
                  <p className="text-foreground text-sm font-medium">{service}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </motion.section>

      {/* How it works */}
      <section className="py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto space-y-12">
            <div className="text-center space-y-4">
              <h2 className="text-3xl md:text-4xl font-bold text-foreground">
                {t('owners.howItWorks.title')}
              </h2>
              <p className="text-lg text-muted-foreground">
                {t('owners.howItWorks.subtitle')}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {(t('owners.howItWorks.steps', { returnObjects: true }) as any[]).map((step: any, index: number) => (
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

      {/* CTA Section */}
      <section className="py-16 bg-gradient-hero">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto text-center space-y-8">
            <h2 className="text-3xl md:text-4xl font-bold text-foreground">
              {t('owners.cta.title')}
            </h2>
            <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
              {t('owners.cta.subtitle')}
            </p>

            <Link to="/contacts">
              <Button size="lg" className="bg-gradient-luxury text-luxury-foreground hover:shadow-luxury hover:scale-105">
                {t('owners.cta.button')}
              </Button>
            </Link>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Owners;
