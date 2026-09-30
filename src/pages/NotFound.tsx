import { SEO } from '@/components/SEO';
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Home } from "lucide-react";

const NotFound = () => {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <SEO title="404" description={t('notFound.title')} noindex /><div className="text-center animate-fade-in">
        <h1 className="mb-4 text-6xl font-heading font-bold text-primary">404</h1>
        <p className="mb-8 text-2xl text-foreground">{t('notFound.title') || 'Страница не найдена'}</p>
        <p className="mb-8 text-muted-foreground max-w-md mx-auto">
          {t('notFound.description') || 'К сожалению, страница, которую вы ищете, не существует или была перемещена.'}
        </p>
        <Link to="/">
          <Button size="lg" className="gap-2">
            <Home size={20} />
            {t('notFound.backHome') || 'Вернуться на главную'}
          </Button>
        </Link>
      </div>
    </div>
  );
};

export default NotFound;
