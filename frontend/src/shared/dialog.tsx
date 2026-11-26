import { useEffect, useRef, useState } from "react";
import "./dialog.css";

/**
 * Hop thoai xac nhan / thong bao ve ngay trong trang, thay cho window.confirm / window.alert cua
 * trinh duyet (hop xam "ptitcodehub.site cho biet", khong doi giao dien duoc).
 *
 *   if (!(await confirmDialog({ title: "Xoá lớp?", message: "...", confirmLabel: "Xoá lớp" }))) return;
 *   await alertDialog({ title: "Không xoá được", message: errorText(e) });
 *
 * DialogHost duoc gan mot lan o goc ung dung (main.tsx); cac ham tren goi duoc tu bat ky dau, khong
 * can hook. Moi luc chi mo mot hop; goi them khi dang mo thi hop sau xep hang cho hop truoc dong.
 */

export interface DialogOptions {
  title: string;
  /** Noi dung; "\n" xuong dong. */
  message?: string;
  /** Chu tren nut dong y, mac dinh "Đồng ý" (confirm) / "Đã hiểu" (alert). */
  confirmLabel?: string;
  cancelLabel?: string;
  /** danger: nut do va bieu tuong canh bao - dung cho xoa, khoa, ket thuc... khong hoan tac duoc. */
  tone?: "danger" | "primary";
}

interface Request extends DialogOptions {
  kind: "confirm" | "alert";
  resolve: (ok: boolean) => void;
}

const queue: Request[] = [];
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

function enqueue(kind: Request["kind"], options: DialogOptions): Promise<boolean> {
  return new Promise((resolve) => {
    queue.push({ ...options, kind, resolve });
    notify();
  });
}

/** Hoi nguoi dung; tra ve true neu bam nut dong y, false neu huy (nut Huy, Esc, bam ra ngoai). */
export function confirmDialog(options: DialogOptions): Promise<boolean> {
  return enqueue("confirm", { tone: "danger", ...options });
}

/** Bao mot thong diep, cho nguoi dung bam "Đã hiểu". */
export function alertDialog(options: DialogOptions): Promise<void> {
  return enqueue("alert", { tone: "primary", ...options }).then(() => undefined);
}

export function DialogHost() {
  const [, setVersion] = useState(0);
  useEffect(() => {
    const listener = () => setVersion((v) => v + 1);
    listeners.add(listener);
    return () => { listeners.delete(listener); };
  }, []);
  const current = queue[0];
  if (!current) return null;
  return <Dialog key={queue.length + current.title} request={current} onDone={(ok) => {
    if (queue[0] !== current) return;   // da dong roi (vd. vua nhan Esc vua bam nut)
    queue.shift();
    current.resolve(ok);
    notify();
  }} />;
}

function Dialog({ request, onDone }: { request: Request; onDone: (ok: boolean) => void }) {
  const { kind, title, message, tone = "danger" } = request;
  const confirmLabel = request.confirmLabel ?? (kind === "alert" ? "Đã hiểu" : "Đồng ý");
  const cancelLabel = request.cancelLabel ?? "Huỷ";
  const safeRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Focus nut an toan: Huy voi hop xac nhan (Enter vo tinh khong xoa nham), "Da hieu" voi hop thong bao.
    (kind === "confirm" ? safeRef.current : confirmRef.current)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { e.preventDefault(); onDone(false); }
      // Giu focus trong hop khi nhan Tab.
      if (e.key === "Tab" && boxRef.current) {
        const buttons = Array.from(boxRef.current.querySelectorAll<HTMLButtonElement>("button"));
        const i = buttons.indexOf(document.activeElement as HTMLButtonElement);
        const next = buttons[(i + (e.shiftKey ? -1 : 1) + buttons.length) % buttons.length];
        e.preventDefault();
        next?.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [kind, onDone]);

  return (
    <div className="dlg-overlay" onMouseDown={() => onDone(false)}>
      <div ref={boxRef} className="dlg-box" role={kind === "confirm" ? "alertdialog" : "dialog"} aria-modal="true"
        aria-labelledby="dlg-title" aria-describedby={message ? "dlg-message" : undefined}
        onMouseDown={(e) => e.stopPropagation()}>
        <div className="dlg-head">
          <div className={`dlg-icon dlg-icon-${tone}`} aria-hidden="true">
            {tone === "danger"
              ? <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M10 3 2 17h16L10 3Z" /><path d="M10 8.5v3.5M10 14.5v.01" /></svg>
              : <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"><circle cx="10" cy="10" r="7.5" /><path d="M10 9v4.5M10 6.5v.01" /></svg>}
          </div>
          <div className="dlg-content">
            <div id="dlg-title" className="dlg-title">{title}</div>
            {message && <div id="dlg-message" className="dlg-message">{message}</div>}
          </div>
        </div>
        <div className="dlg-actions">
          {kind === "confirm" && <button ref={safeRef} className="dlg-btn" onClick={() => onDone(false)}>{cancelLabel}</button>}
          <button ref={confirmRef} className={`dlg-btn dlg-btn-${tone}`} onClick={() => onDone(true)}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
