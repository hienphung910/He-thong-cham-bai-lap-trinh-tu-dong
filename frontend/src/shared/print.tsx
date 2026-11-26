import { useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import "./print.css";

/**
 * Noi dung chi dung de IN: ve vao mot khoi rieng ngay duoi <body>, an tren man hinh. Khi in, ca ung dung
 * (#root) bi an va chi khoi nay hien ra - nen khong bi cat boi khung cuon cua trang, va giao dien quanh
 * (thanh menu, nut bam) khong lot vao ban in.
 */
export function PrintArea({ children }: { children: ReactNode }) {
  const [host, setHost] = useState<HTMLElement | null>(null);
  useEffect(() => {
    const el = document.createElement("div");
    el.className = "print-root";
    document.body.appendChild(el);
    setHost(el);
    return () => { el.remove(); };
  }, []);
  return host ? createPortal(children, host) : null;
}
