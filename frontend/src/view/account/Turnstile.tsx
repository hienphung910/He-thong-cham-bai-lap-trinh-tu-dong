import { useEffect, useRef } from "react";

/** Phan API cua Cloudflare Turnstile ma giao dien dung (script tu dang ky vao window.turnstile). */
interface TurnstileApi {
  render: (container: HTMLElement, options: {
    sitekey: string; theme?: "light" | "dark" | "auto"; language?: string;
    callback: (token: string) => void; "expired-callback"?: () => void; "error-callback"?: () => void;
  }) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
}

declare global {
  interface Window { turnstile?: TurnstileApi }
}

const SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
let loading: Promise<TurnstileApi> | null = null;

/** Tai script cua Turnstile dung mot lan cho ca trang. */
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile);
  if (!loading) {
    loading = new Promise<TurnstileApi>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = SCRIPT_URL;
      script.async = true;
      script.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error("Turnstile không khởi động được")));
      script.onerror = () => { loading = null; reject(new Error("Không tải được CAPTCHA")); };
      document.head.appendChild(script);
    });
  }
  return loading;
}

/**
 * Widget CAPTCHA Cloudflare Turnstile. Lam xong thu thach thi goi onToken(token); token het han hoac
 * loi thi goi onToken(""). Token chi dung duoc MOT LAN: sau moi lan gui form (du thanh cong hay khong)
 * component cha tang resetKey de widget cap token moi.
 */
export default function Turnstile({ siteKey, resetKey, onToken, onError }: {
  siteKey: string; resetKey: number; onToken: (token: string) => void; onError?: (message: string) => void;
}) {
  const box = useRef<HTMLDivElement>(null);
  const widget = useRef<string | null>(null);
  // Giu ham moi nhat trong ref de widget khong phai ve lai moi lan component cha render.
  const tokenRef = useRef(onToken);
  tokenRef.current = onToken;
  const errorRef = useRef(onError);
  errorRef.current = onError;

  useEffect(() => {
    let alive = true;
    loadTurnstile().then((api) => {
      if (!alive || !box.current) return;
      widget.current = api.render(box.current, {
        sitekey: siteKey,
        theme: "light",
        language: "vi",
        callback: (token) => tokenRef.current(token),
        "expired-callback": () => tokenRef.current(""),
        "error-callback": () => tokenRef.current(""),
      });
    }).catch((e: unknown) => {
      if (alive) errorRef.current?.(e instanceof Error ? e.message : String(e));
    });
    return () => {
      alive = false;
      if (widget.current && window.turnstile) window.turnstile.remove(widget.current);
      widget.current = null;
    };
  }, [siteKey]);

  useEffect(() => {
    if (resetKey > 0 && widget.current && window.turnstile) window.turnstile.reset(widget.current);
  }, [resetKey]);

  return <div ref={box} style={{ minHeight: 65 }} />;
}
