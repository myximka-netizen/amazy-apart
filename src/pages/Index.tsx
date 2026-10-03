import { PhoneNumber } from '@/components/PhoneNumber';
import { MaxContact } from '@/components/MaxContact';
import { DirectBooking } from '@/components/DirectBooking';
import { SITE, trackGoal } from '@/lib/site';
import { Link } from "react-router-dom";
import { ArrowRight, Shield, Clock, Star, MapPin, Users, Award, Trophy, Wifi, Monitor, Sparkles, X, Phone, MessageCircle } from "lucide-react";
import guestsChoiceAward from "@/assets/guests-choice-award.png";
import superhostCertificate from "@/assets/superhost-yandex-travel.png";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogTrigger, DialogTitle } from "@/components/ui/dialog";
import { Carousel, CarouselContent, CarouselItem, CarouselNext, CarouselPrevious } from "@/components/ui/carousel";
import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { SearchWidget } from "@/components/SearchWidget";
import { ReviewsCarousel } from "@/components/ReviewsCarousel";
import { ContactButton } from "@/components/ContactButton";
import { StructuredData, generateOrganizationData, generateWebSiteData, generateLocalBusinessData, generateApartmentData, generateBreadcrumbData } from "@/components/StructuredData";
import { SEO } from "@/components/SEO";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import heroImage from "@/assets/hero-new-2.avifs";
import apartment1 from "@/assets/apartment-1.jpg";
import apartment2 from "@/assets/apartment-2.jpg";
import apartment3 from "@/assets/apartment-3.jpg";
import certificate202297 from "@/assets/certificate-2022-97.jpg";
import certificate202296 from "@/assets/certificate-2022-96.jpg";
import certificate202310 from "@/assets/certificate-2023-10.jpg";
import certificate202399a from "@/assets/certificate-2023-99a.jpg";
import certificate202399b from "@/assets/certificate-2023-99b.jpg";
import certificate202398_1 from "@/assets/certificate-2023-98-1.jpg";
import certificate202397_2 from "@/assets/certificate-2023-97-2.jpg";
import certificate202498_1 from "@/assets/certificate-2024-98-1.jpg";
import certificate202498_2 from "@/assets/certificate-2024-98-2.jpg";
import certificate202498_3 from "@/assets/certificate-2024-98-3.jpg";
import certificate202499 from "@/assets/certificate-2024-99.jpg";
import certificate202498_4 from "@/assets/certificate-2024-98-4.jpg";
import certificate202498_5 from "@/assets/certificate-2024-98-5.jpg";
import certificateSilver2024 from "@/assets/certificate-silver-2024.jpg";

const Index = () => {
  const { t } = useTranslation();
  const reviews = [
    {
      name: "Анна Петрова",
      rating: 5,
      comment: "Потрясающие апартаменты! Все было именно как на фотографиях. Чистота, комфорт и отличное расположение. Обязательно вернемся!",
      date: "2 недели назад"
    },
    {
      name: "Ксения",
      rating: 5,
      comment: "Очень понравилась квартира! Все продумано до мелочей, чистенько, уютно, просторнее чем номера отелей! Не хотели уезжать!",
      date: "1 месяц назад"
    },
    {
      name: "Elena K.",
      rating: 4,
      comment: "Wonderful experience! The apartment was clean, modern and in a great location. Host was very responsive and helpful.",
      date: "3 недели назад"
    },
    {
      name: "李明 (Li Ming)",
      rating: 5,
      comment: "非常棒的公寓！位置极佳，装修现代，设施齐全。房东非常友善，服务周到。强烈推荐给来莫斯科的朋友们！",
      date: "1周前"
    }
  ];

  return (
    <div className="min-h-screen bg-background">
      <SEO
        title={t('upgrade.seo.homeTitle')}
        description={t('upgrade.seo.homeDescription')}
        keywords="аренда квартир москва, апартаменты посуточно, снять квартиру на сутки москва, краткосрочная аренда, жилье в центре москвы"
        structuredData={[
          generateOrganizationData(),
          generateWebSiteData(),
          generateLocalBusinessData(),
          generateBreadcrumbData([
            { name: "Главная", url: "https://amazy-apart.ru/" }
          ])
        ]}
      />
      <Header />

      {/* Hero Section with Booking */}
      <motion.section
        id="main-content" className="relative min-h-[80vh] py-8 md:py-12 flex items-center justify-center overflow-hidden"
        initial={false}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        <img
          src={heroImage}
          alt="Современные премиальные апартаменты для краткосрочной аренды в центре Москвы"
          className="absolute inset-0 w-full h-full object-cover"
          loading="eager"
        />
        <div className="absolute inset-0 bg-black/40" />

        <div className="relative z-10 container mx-auto px-4">
          <div className="max-w-6xl mx-auto text-center">
            <motion.div
              className="text-white mb-6 md:mb-8"
              initial={false}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.2, ease: "easeOut" }}
            >
              <h1 className="text-4xl sm:text-5xl md:text-7xl font-bold leading-tight mb-6">
                {t('hero.title')}
                <span className="block text-luxury">{t('hero.subtitle')}</span>
              </h1>
              <p className="text-xl md:text-2xl text-white/90 max-w-3xl mx-auto leading-relaxed">
                {t('hero.description')}{' '}
                <strong className="font-semibold text-luxury">{t('hero.directBooking')}</strong>
              </p>
            </motion.div>

            {/* Integrated Search Widget */}
            <motion.div
              initial={false}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.5, ease: "easeOut" }}
            >
              <SearchWidget className="bg-transparent rounded-2xl px-0 py-4 sm:p-8 max-w-4xl mx-auto" />
            </motion.div>

            {/* Quick Contact Section */}
            <motion.div
              className="mt-12 flex flex-col sm:flex-row justify-center items-center gap-6 text-white"
              initial={false}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.7, ease: "easeOut" }}
            >
              <div className="flex items-center space-x-2">
                <Phone className="h-5 w-5 text-luxury" />
                <a
                  href={`tel:${SITE.phone}`} onClick={() => trackGoal('phone_click')}
                  className="text-lg font-medium hover:text-luxury transition-colors"
                >
                  <PhoneNumber />
                </a>
              </div>

              <div className="hidden sm:block w-px h-8 bg-white/30"></div>

              <div className="flex flex-wrap justify-center items-center gap-4">
                <a
                  href={SITE.whatsapp} onClick={() => trackGoal('whatsapp_click')}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center space-x-2 hover:text-luxury transition-colors"
                >
                  <MessageCircle className="h-5 w-5 text-green-400" />
                  <span>WhatsApp</span>
                </a>

                <a
                  href={SITE.telegram} onClick={() => trackGoal('telegram_click')}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center space-x-2 hover:text-luxury transition-colors"
                >
                  <MessageCircle className="h-5 w-5 text-blue-400" />
                  <span>Telegram</span>
                </a>
                <MaxContact className="hover:text-luxury transition-colors" />
              </div>
            </motion.div>
          </div>
        </div>
      </motion.section>

      <section className="container mx-auto px-4 pt-10"><DirectBooking /></section>
      <section className="container mx-auto px-4 py-12">
        <h2 className="mb-6 text-3xl font-bold">{t('upgrade.plansTitle')}</h2>
        <div className="grid gap-5 md:grid-cols-2">
          {[{ path: '/business-travel/', title: 'businessCard', text: 'businessCardText' }, { path: '/long-stay/', title: 'longCard', text: 'longCardText' }].map(item => <Link key={item.path} to={item.path} className="rounded-2xl border bg-surface p-6 md:p-8 hover:border-primary"><h3 className="text-2xl font-semibold">{t(`upgrade.${item.title}`)}</h3><p className="my-3 text-muted-foreground">{t(`upgrade.${item.text}`)}</p><span className="inline-flex items-center gap-2 font-semibold text-primary">{t('upgrade.details')}<ArrowRight size={18} /></span></Link>)}
        </div>
      </section>
      {/* Ratings & Awards Section */}
      <motion.section
        className="py-12 bg-gradient-to-r from-primary/5 to-luxury/5"
        initial={false}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        <div className="container mx-auto px-4">
          <div className="max-w-6xl mx-auto">
            <motion.div
              className="text-center mb-8"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, ease: "easeOut" }}
            >
              <h2 className="text-2xl md:text-3xl font-bold text-foreground mb-4">
                {t('homepage.achievements.title')}
              </h2>
              <p className="text-muted-foreground">
                {t('homepage.achievements.subtitle')}
              </p>
            </motion.div>

            {/* Awards and Certificates Carousel */}
            <motion.div
              className="flex justify-center px-8"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: 0.2, ease: "easeOut" }}
            >
              <Carousel className="w-full max-w-5xl">
                <CarouselContent>
                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer hover-scale">
                          <img
                            src={certificateSilver2024}
                            alt="Серебряный партнёр Суточно.ру 2024"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Серебряный партнёр Суточно.ру 2024</DialogTitle>
                        <img
                          src={certificateSilver2024}
                          alt="Серебряный партнёр Суточно.ру 2024"
                          className="w-full h-auto rounded-lg"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer hover-scale">
                          <img
                            src={guestsChoiceAward}
                            alt="Guests' Choice Award 2024"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Guests' Choice Award 2024</DialogTitle>
                        <img
                          src={guestsChoiceAward}
                          alt="Guests' Choice Award 2024"
                          className="w-full h-auto rounded-lg"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer hover-scale">
                          <img
                            src={superhostCertificate}
                            alt="Суперхозяин Яндекс Путешествия"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Суперхозяин Яндекс Путешествия</DialogTitle>
                        <img
                          src={superhostCertificate}
                          alt="Суперхозяин Яндекс Путешествия"
                          className="w-full h-auto rounded-lg"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202310}
                            alt="Сертификат Суточно.ру 2023 - 10/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <img
                          src={certificate202310}
                          alt="Сертификат Суточно.ру 2023 - 10/10"
                           className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202399a}
                            alt="Сертификат Суточно.ру 2023 - 9.9/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <img
                          src={certificate202399a}
                          alt="Сертификат Суточно.ру 2023 - 9.9/10"
                           className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202399b}
                            alt="Сертификат Суточно.ру 2023 - 9.9/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <img
                          src={certificate202399b}
                          alt="Сертификат Суточно.ру 2023 - 9.9/10"
                          className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202297}
                            alt="Сертификат Суточно.ру 2022 - 9.7/10"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <img
                          src={certificate202297}
                          alt="Сертификат Суточно.ру 2022 - 9.7/10"
                          className="w-full h-auto rounded-lg"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202296}
                            alt="Сертификат Суточно.ру 2022 - 9.6/10"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-2xl">
                        <DialogTitle className="sr-only">Сертификат Суточно.ру 2022</DialogTitle>
                        <img
                          src={certificate202296}
                          alt="Сертификат Суточно.ру 2022 - 9.6/10"
                          className="w-full h-auto"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202398_1}
                            alt="Сертификат Суточно.ру 2023 - 9.8/10"
                            className="w-32 h-48 mx-auto rounded-lg object-cover"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-2xl">
                        <DialogTitle className="sr-only">Сертификат Суточно.ру 2023</DialogTitle>
                        <img
                          src={certificate202398_1}
                          alt="Сертификат Суточно.ру 2023 - 9.8/10"
                          className="w-full h-auto"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202499}
                            alt="Сертификат Суточно.ру 2024 - 9.9/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Сертификат Суточно.ру 2024</DialogTitle>
                        <img
                          src={certificate202499}
                          alt="Сертификат Суточно.ру 2024 - 9.9/10"
                           className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202498_4}
                            alt="Сертификат Суточно.ру 2024 - 9.8/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Сертификат Суточно.ру 2024</DialogTitle>
                        <img
                          src={certificate202498_4}
                          alt="Сертификат Суточно.ру 2024 - 9.8/10"
                           className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>

                  <CarouselItem className="md:basis-1/2 lg:basis-1/3">
                    <Dialog>
                      <DialogTrigger asChild>
                        <div className="bg-white rounded-xl p-4 text-center shadow-card hover:shadow-lg transition-all hover:scale-105 cursor-pointer">
                          <img
                            src={certificate202498_5}
                            alt="Сертификат Суточно.ру 2024 - 9.8/10"
                             className="w-32 h-48 mx-auto rounded-lg object-cover rotate-180"
                          />
                        </div>
                      </DialogTrigger>
                      <DialogContent className="max-w-3xl w-full">
                        <DialogTitle className="sr-only">Сертификат Суточно.ру 2024</DialogTitle>
                        <img
                          src={certificate202498_5}
                          alt="Сертификат Суточно.ру 2024 - 9.8/10"
                           className="w-full h-auto rounded-lg rotate-180"
                        />
                      </DialogContent>
                    </Dialog>
                  </CarouselItem>
                </CarouselContent>
                <CarouselPrevious />
                <CarouselNext />
              </Carousel>
            </motion.div>
          </div>
        </div>
      </motion.section>

      {/* Key Benefits */}
      <section className="py-16 bg-background">
        <div className="container mx-auto px-4">
          <div className="text-center space-y-4 mb-12">
            <h2 className="text-3xl md:text-4xl font-bold text-foreground">
              {t('benefits.title')}
            </h2>
            <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
              {t('benefits.description')}
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.1 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <Shield className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.honestPhotos.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.honestPhotos.description')}
              </p>
            </motion.div>

            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.2 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <Clock className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.support24.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.support24.description')}
              </p>
            </motion.div>

            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.3 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <MapPin className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.bestLocations.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.bestLocations.description')}
              </p>
            </motion.div>

            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.4 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <Wifi className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.highSpeedInternet.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.highSpeedInternet.description')}
              </p>
            </motion.div>

            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.5 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <Monitor className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.smartTv.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.smartTv.description')}
              </p>
            </motion.div>

            <motion.div
              className="text-center space-y-4 group"
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-100px" }}
              transition={{ duration: 0.5, delay: 0.6 }}
            >
              <div className="w-16 h-16 mx-auto bg-gradient-luxury rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                <Sparkles className="h-8 w-8 text-luxury-foreground" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{t('benefits.perfectCleanliness.title')}</h3>
              <p className="text-muted-foreground">
                {t('benefits.perfectCleanliness.description')}
              </p>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Our Services Section */}
      <motion.section
        className="py-20 bg-background"
        initial={false}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        <div className="container mx-auto px-4">
          <motion.div
            className="text-center mb-16"
            initial={false}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          >
            <h2 className="text-3xl md:text-4xl font-bold text-foreground mb-6">
              {t('services.title')}
            </h2>
            <p className="text-xl text-muted-foreground max-w-3xl mx-auto">
              {t('services.subtitle')}
            </p>
          </motion.div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 max-w-6xl mx-auto">
            {/* Corporate Services */}
            <motion.div
              className="bg-gradient-to-br from-surface to-surface/50 rounded-3xl p-8 shadow-card hover:shadow-floating transition-all duration-300 border border-border/50"
              initial={false}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, ease: "easeOut" }}
              whileHover={{ scale: 1.02 }}
            >
              <div className="flex flex-col items-start gap-6 sm:flex-row">
                <div className="w-20 h-20 bg-gradient-luxury rounded-2xl flex items-center justify-center flex-shrink-0">
                  <Users className="h-10 w-10 text-luxury-foreground" />
                </div>
                <div className="flex-1">
                  <h3 className="text-2xl font-bold text-foreground mb-4">
                    {t('services.corporate.title')}
                  </h3>
                  <p className="text-muted-foreground text-lg leading-relaxed mb-6">
                    {t('services.corporate.description')}
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.corporate.features.documents')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.corporate.features.manager')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.corporate.features.payment')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.corporate.features.discounts')}</span>
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* Long-term Cleaning */}
            <motion.div
              className="bg-gradient-to-br from-surface to-surface/50 rounded-3xl p-8 shadow-card hover:shadow-floating transition-all duration-300 border border-border/50"
              initial={false}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, ease: "easeOut" }}
              whileHover={{ scale: 1.02 }}
            >
              <div className="flex flex-col items-start gap-6 sm:flex-row">
                <div className="w-20 h-20 bg-gradient-luxury rounded-2xl flex items-center justify-center flex-shrink-0">
                  <Sparkles className="h-10 w-10 text-luxury-foreground" />
                </div>
                <div className="flex-1">
                  <h3 className="text-2xl font-bold text-foreground mb-4">
                    {t('services.cleaning.title')}
                  </h3>
                  <p className="text-muted-foreground text-lg leading-relaxed mb-6">
                    {t('services.cleaning.description')}
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.cleaning.features.weekly')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.cleaning.features.linen')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.cleaning.features.supplies')}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-luxury rounded-full"></div>
                      <span className="text-sm text-muted-foreground">{t('services.cleaning.features.schedule')}</span>
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </motion.section>

      {/* Reviews */}
      <motion.section
        className="py-16"
        initial={false}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        <div className="container mx-auto px-4">
          <motion.div
            className="text-center space-y-4 mb-12"
            initial={false}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          >
            <h2 className="text-3xl md:text-4xl font-bold text-foreground">
              {t('reviews.title')}
            </h2>
            <p className="text-lg text-muted-foreground">
              {t('reviews.description')}
            </p>
          </motion.div>

          <motion.div
            initial={false}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, delay: 0.2, ease: "easeOut" }}
          >
            <ReviewsCarousel reviews={reviews} />
          </motion.div>
        </div>
      </motion.section>

      <Footer />
      <ContactButton />
    </div>
  );
};

export default Index;
