import { SITE, siteUrl } from '@/lib/site';
import { SEO } from '@/components/SEO';
import NotFound from './NotFound';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import ImageGallery from '@/components/ImageGallery';
import BookingForm from '@/components/BookingForm';
import { MapPin, Users, Home, Wifi, Tv, Coffee, Bath, Wind, Car } from 'lucide-react';
import apartment1 from '@/assets/apartment-1.jpg';
import apartment2 from '@/assets/apartment-2.jpg';
import apartment3 from '@/assets/apartment-3.jpg';

const ApartmentDetail = () => {
  const { id } = useParams();
  const { t, i18n } = useTranslation();

  const apartmentData: any = {
    '1': {
      images: [apartment1, apartment2, apartment3],
      title: t('apartments.apt1.title'),
      address: t('apartments.apt1.address'),
      price: '8 500 ₽',
      guests: 4,
      rooms: 2,
      bathrooms: 1
    },
    '2': {
      images: [apartment2, apartment1, apartment3],
      title: t('apartments.apt2.title'),
      address: t('apartments.apt2.address'),
      price: '12 000 ₽',
      guests: 6,
      rooms: 3,
      bathrooms: 2
    },
    '3': {
      images: [apartment3, apartment1, apartment2],
      title: t('apartments.apt3.title'),
      address: t('apartments.apt3.address'),
      price: '6 500 ₽',
      guests: 3,
      rooms: 1,
      bathrooms: 1
    }
  };

  const apartment = apartmentData[id || '1'];

  if (!apartment) return <NotFound />;

  const amenities = [
    { icon: Wifi, label: 'Wi-Fi' },
    { icon: Tv, label: t('amenities.tv') },
    { icon: Coffee, label: t('amenities.kitchen') },
    { icon: Bath, label: t('amenities.bathroom') },
    { icon: Wind, label: t('amenities.ac') },
    { icon: Car, label: t('amenities.parking') }
  ];

  return (
    <div className="min-h-screen bg-background">
      <SEO title={apartment.title} description={`${apartment.title}. ${apartment.address}. ${t('upgrade.directNote')}`} ogImage={new URL(apartment.images[0], SITE.origin).href} ogImageAlt={apartment.title} structuredData={[{ '@context': 'https://schema.org', '@type': 'Apartment', name: apartment.title, image: new URL(apartment.images[0], SITE.origin).href, url: siteUrl(`/apartment/${id}/`, i18n.language), numberOfRooms: apartment.rooms }]} />
      <Header />

      <div id="main-content" className="container mx-auto px-4 py-8">
        <div className="mb-8 animate-fade-in">
          <h1 className="font-heading text-3xl md:text-4xl font-bold text-foreground mb-4">
            {apartment.title}
          </h1>
          <div className="flex items-center space-x-2 text-muted-foreground">
            <MapPin size={20} />
            <span className="text-lg">{apartment.address}</span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 space-y-8">
            <div className="animate-slide-up">
              <ImageGallery images={apartment.images} />
            </div>

            <div className="bg-card rounded-lg p-6 shadow-card animate-slide-up animation-delay-100">
              <h2 className="font-heading text-2xl font-semibold mb-4 text-foreground">
                {t('details.about')}
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                <div className="flex items-center space-x-2 text-muted-foreground">
                  <Users size={20} />
                  <span>{apartment.guests} {t('apartment.guests')}</span>
                </div>
                <div className="flex items-center space-x-2 text-muted-foreground">
                  <Home size={20} />
                  <span>{apartment.rooms} {t('apartment.rooms')}</span>
                </div>
                <div className="flex items-center space-x-2 text-muted-foreground">
                  <Bath size={20} />
                  <span>{apartment.bathrooms} {t('details.bathrooms')}</span>
                </div>
              </div>
              <p className="text-muted-foreground leading-relaxed">
                {t('details.description')}
              </p>
            </div>

            <div className="bg-card rounded-lg p-6 shadow-card animate-slide-up animation-delay-200">
              <h2 className="font-heading text-2xl font-semibold mb-4 text-foreground">
                {t('details.amenities')}
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                {amenities.map((amenity, index) => (
                  <div key={index} className="flex items-center space-x-3">
                    <amenity.icon size={20} className="text-primary" />
                    <span className="text-muted-foreground">{amenity.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="lg:col-span-1 animate-slide-up animation-delay-300">
            <BookingForm />
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default ApartmentDetail;
