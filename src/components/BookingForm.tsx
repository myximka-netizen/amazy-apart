import { whatsappDraft, trackGoal } from '@/lib/site';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Calendar, Users, MessageSquare } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

const BookingForm = () => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [draft, setDraft] = useState('');
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone: '',
    checkIn: '',
    checkOut: '',
    guests: '',
    message: ''
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (formData.checkOut <= formData.checkIn) return;
    const url = whatsappDraft(`${t('booking.title')}\n${window.location.href.split('?')[0]}\n${t('booking.name')}: ${formData.name}\nEmail: ${formData.email}\n${t('booking.phone')}: ${formData.phone}\n${t('booking.checkIn')}: ${formData.checkIn}\n${t('booking.checkOut')}: ${formData.checkOut}\n${t('booking.guests')}: ${formData.guests}\n${formData.message}`);
    setDraft(url);
    trackGoal('contact_draft');
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setFormData(prev => ({
      ...prev,
      [e.target.name]: e.target.value
    }));
  };

  return (
    <Card className="shadow-floating sticky top-24">
      <CardHeader>
        <CardTitle className="font-heading text-2xl">
          {t('booking.title')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="mb-4 text-sm text-muted-foreground">{t('upgrade.formIntro')}</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="name">{t('booking.name')}</Label>
            <Input
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              required
              placeholder={t('booking.namePlaceholder')}
            />
          </div>

          <div>
            <Label htmlFor="email">{t('booking.email')}</Label>
            <Input
              id="email"
              name="email"
              type="email"
              value={formData.email}
              onChange={handleChange}
              required
              placeholder={t('booking.emailPlaceholder')}
            />
          </div>

          <div>
            <Label htmlFor="phone">{t('booking.phone')}</Label>
            <Input
              id="phone"
              name="phone"
              type="tel"
              value={formData.phone}
              onChange={handleChange}
              required
              placeholder="+7 (___) ___-__-__"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="checkIn" className="flex items-center space-x-2">
                <Calendar size={16} />
                <span>{t('booking.checkIn')}</span>
              </Label>
              <Input
                id="checkIn"
                name="checkIn"
                type="date"
                value={formData.checkIn}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <Label htmlFor="checkOut" className="flex items-center space-x-2">
                <Calendar size={16} />
                <span>{t('booking.checkOut')}</span>
              </Label>
              <Input
                id="checkOut"
                min={formData.checkIn ? new Date(new Date(`${formData.checkIn}T00:00:00Z`).getTime() + 86400000).toISOString().slice(0, 10) : undefined}
                name="checkOut"
                type="date"
                value={formData.checkOut}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div>
            <Label htmlFor="guests" className="flex items-center space-x-2">
              <Users size={16} />
              <span>{t('booking.guests')}</span>
            </Label>
            <Input
              id="guests"
              name="guests"
              type="number"
              min="1"
              value={formData.guests}
              onChange={handleChange}
              required
              placeholder="2"
            />
          </div>

          <div>
            <Label htmlFor="message" className="flex items-center space-x-2">
              <MessageSquare size={16} />
              <span>{t('booking.message')}</span>
            </Label>
            <Textarea
              id="message"
              name="message"
              value={formData.message}
              onChange={handleChange}
              placeholder={t('booking.messagePlaceholder')}
              rows={4}
            />
          </div>

          <Button type="submit" className="w-full" size="lg">
            {t('upgrade.draftButton')}
          </Button>
        </form>
        {draft && <div role="status" className="mt-4 text-sm"><p>{t('upgrade.draftNotice')}</p><a href={draft} target="_blank" rel="noopener noreferrer" className="mt-2 inline-block underline">{t('upgrade.openDraft')}</a></div>}
      </CardContent>
    </Card>
  );
};

export default BookingForm;