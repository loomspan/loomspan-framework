import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { browserRouter } from "./app/routes";
import { BrowserSessionProvider } from "./security/BrowserSessionProvider";
import { consumePairingFragment } from "./security/pairingFragment";
import "./styles/index.css";

const root = document.getElementById("root");
if (!root) throw new Error("loomspan Console root element is missing");
let initialPairingSecret = consumePairingFragment();

createRoot(root).render(
  <StrictMode>
    <BrowserSessionProvider initialPairingSecret={initialPairingSecret}>
      <RouterProvider router={browserRouter()} />
    </BrowserSessionProvider>
  </StrictMode>,
);
initialPairingSecret = undefined;
