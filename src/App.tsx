import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import i18n from './i18n';
import Stay from './pages/Stay';
import Location from './pages/Location';
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { useYandexMetrika } from "./hooks/useYandexMetrika";
import Index from "./pages/Index";
import Apartments from "./pages/Apartments";
import ApartmentDetail from "./pages/ApartmentDetail";
import About from "./pages/About";
import Contacts from "./pages/Contacts";
import Owners from "./pages/Owners";
import Offers from "./pages/Offers";
import NotFound from "./pages/NotFound";

export const AppContent = () => {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  useYandexMetrika();
  
  return (
    <Routes>
      <Route path="/" element={<Index />} />
      <Route path="/apartments" element={<Apartments />} />
      <Route path="/apartments/:slug" element={<Location />} />
      <Route path="/business-travel" element={<Stay business />} />
      <Route path="/long-stay" element={<Stay />} />
      <Route path="/apartment/:id" element={<ApartmentDetail />} />
      <Route path="/about" element={<About />} />
      <Route path="/contacts" element={<Contacts />} />
      <Route path="/owners" element={<Owners />} />
      <Route path="/offers" element={<Offers />} />
      {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

const App = () => (
  <TooltipProvider>
    <Toaster />
    <Sonner />
    <BrowserRouter basename={i18n.language === 'ru' ? '/' : `/${i18n.language}`}>
      <AppContent />
    </BrowserRouter>
  </TooltipProvider>
);

export default App;
