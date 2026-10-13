import { Link } from 'react-router-dom';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { MapPin, Users, Home } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface ApartmentCardProps {
  id: string;
  image: string;
  title: string;
  address: string;
  guests: number;
  rooms: number;
  price: string;
  badges?: string[];
}

const ApartmentCard = ({ 
  id, 
  image, 
  title, 
  address, 
  guests, 
  rooms, 
  price,
  badges = []
}: ApartmentCardProps) => {
  const { t } = useTranslation();

  return (
    <Card className="overflow-hidden hover-scale shadow-card hover:shadow-floating transition-all duration-300">
      <div className="relative h-64 overflow-hidden">
        <img 
          src={image} 
          alt={title}
          className="w-full h-full object-cover transition-transform duration-500 hover:scale-110"
        />
        {badges.length > 0 && (
          <div className="absolute top-4 left-4 flex flex-wrap gap-2">
            {badges.map((badge, index) => (
              <Badge key={index} variant="default" className="bg-primary/90 backdrop-blur-sm">
                {badge}
              </Badge>
            ))}
          </div>
        )}
      </div>
      
      <CardContent className="p-6">
        <h3 className="font-heading text-xl font-semibold mb-3 text-foreground">
          {title}
        </h3>
        
        <div className="flex items-start space-x-2 text-muted-foreground mb-4">
          <MapPin size={16} className="mt-1 flex-shrink-0" />
          <span className="text-sm">{address}</span>
        </div>
        
        <div className="flex items-center space-x-6 text-sm text-muted-foreground mb-4">
          <div className="flex items-center space-x-2">
            <Users size={16} />
            <span>{guests} {t('apartment.guests')}</span>
          </div>
          <div className="flex items-center space-x-2">
            <Home size={16} />
            <span>{rooms} {t('apartment.rooms')}</span>
          </div>
        </div>
        
        <div className="flex items-baseline space-x-2">
          <span className="text-2xl font-bold text-primary">{price}</span>
          <span className="text-sm text-muted-foreground">/ {t('apartment.night')}</span>
        </div>
      </CardContent>
      
      <CardFooter className="p-6 pt-0">
        <Link to={`/apartment/${id}/`} className="w-full">
          <Button className="w-full" size="lg">
            {t('apartment.viewDetails')}
          </Button>
        </Link>
      </CardFooter>
    </Card>
  );
};

export default ApartmentCard;
