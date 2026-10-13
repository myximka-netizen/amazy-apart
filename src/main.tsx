import { createRoot } from "react-dom/client";
import { HelmetProvider } from "react-helmet-async";
import App from "./App.tsx";
import "./index.css";
import "./i18n";

// Keep legacy language-root links usable while the hosting redirect is enabled.
// This is a client-side fallback, not a replacement for the HTTP 301/308.
if (/^\/(en|zh)$/.test(window.location.pathname)) {
  window.history.replaceState(window.history.state, '', `${window.location.pathname}/${window.location.search}${window.location.hash}`);
}

createRoot(document.getElementById("root")!).render(
  <HelmetProvider>
    <App />
  </HelmetProvider>
);
