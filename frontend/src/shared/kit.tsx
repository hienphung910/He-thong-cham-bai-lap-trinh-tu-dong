import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

/**
 * Bo thanh phan giao dien dung chung cho cac trang cua module mo rong:
 * khung trang, the, nut, o nhap, bang, nhan, bieu do cot va hook tai du lieu.
 * Nho bo nay moi trang chi con phan rieng cua no (goi API nao, hien cot gi).
 */

// --------------------------------------------------------------- tai du lieu

/**
 * Goi mot ham API khi trang mo ra (va moi khi deps doi), giu lai ket qua / loi.
 * reload() de tai lai sau khi them, sua, xoa.
 */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [tick, setTick] = useState(0);
  const prevDeps = useRef<unknown[] | null>(null);

  useEffect(() => {
    let alive = true;
    // deps doi nghia la dang xem mot doi tuong khac (hoc sinh khac, lop khac...): bo du lieu cu de
    // khong hien tiep thong tin cua doi tuong truoc trong luc cho, hoac khi lan tai moi bi loi.
    // reload() (chi tick doi) thi giu du lieu cu de trang khong nhay.
    const before = prevDeps.current;
    const changed = before !== null
      && (before.length !== deps.length || deps.some((d, i) => !Object.is(d, before[i])));
    prevDeps.current = deps;
    if (changed) setData(null);
    load()
      .then((d) => { if (alive) { setData(d); setError(""); } })
      .catch((e) => { if (alive) setError(errorText(e)); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, reload };
}

export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** "2026-11-22T14:30:00" -> "22/11/2026 14:30" */
export function fmtTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [date, time = ""] = iso.split("T");
  const [y, m, d] = date.split("-");
  return `${d}/${m}/${y} ${time.slice(0, 5)}`;
}

/** Gia tri mac dinh cho o datetime-local: thoi diem hien tai cong them so phut. */
export function localDateTime(plusMinutes = 0): string {
  const d = new Date(Date.now() + plusMinutes * 60_000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ------------------------------------------------------------------- bo cuc

/** Khung mot trang: tieu de, mo ta, cac nut ben phai, phan than cuon duoc. */
export function Page({ title, subtitle, actions, children }: {
  title: string; subtitle?: string; actions?: ReactNode; children: ReactNode;
}) {
  return (
    <div style={{ flex: 1, overflow: "auto", padding: "22px 28px 28px" }}>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 12, flexWrap: "wrap", marginBottom: 18 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "#212121" }}>{title}</h1>
          {subtitle && <p style={{ margin: "4px 0 0", fontSize: 13, color: "#757575" }}>{subtitle}</p>}
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>{actions}</div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>{children}</div>
    </div>
  );
}

export function Card({ title, right, children, style }: {
  title?: string; right?: ReactNode; children: ReactNode; style?: CSSProperties;
}) {
  return (
    <section style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "16px 20px", ...style }}>
      {(title || right) && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10, marginBottom: 12 }}>
          <h2 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "#212121" }}>{title}</h2>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>{right}</div>
        </div>
      )}
      {children}
    </section>
  );
}

/** Luoi cac the nho bang nhau, tu xuong dong khi het cho. */
export function Grid({ min = 260, children }: { min?: number; children: ReactNode }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: `repeat(auto-fill, minmax(${min}px, 1fr))`, gap: 14 }}>
      {children}
    </div>
  );
}

/** O so lieu lon: nhan nho o tren, con so o duoi. */
export function Stat({ label, value, color = "var(--color-red)" }: { label: string; value: ReactNode; color?: string }) {
  return (
    <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "12px 16px", minWidth: 120 }}>
      <div style={{ fontSize: 11, color: "#9E9E9E", textTransform: "uppercase", letterSpacing: "0.06em" }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 800, color, lineHeight: 1.3 }}>{value}</div>
    </div>
  );
}

/** Hop thoai phu len tren trang; bam ra ngoai hoac nut Dong de tat. */
export function Modal({ title, onClose, width = 560, children }: {
  title: string; onClose: () => void; width?: number; children: ReactNode;
}) {
  return (
    <div onMouseDown={onClose} style={{
      position: "fixed", inset: 0, background: "rgba(0,0,0,0.35)", zIndex: 300,
      display: "flex", alignItems: "center", justifyContent: "center", padding: 20,
    }}>
      <div onMouseDown={(e) => e.stopPropagation()} style={{
        background: "#fff", borderRadius: 10, width, maxWidth: "100%", maxHeight: "90vh",
        display: "flex", flexDirection: "column", boxShadow: "0 12px 40px rgba(0,0,0,0.25)",
      }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 20px", borderBottom: "1px solid #F0F0F0" }}>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#212121" }}>{title}</h2>
          <Btn kind="ghost" small onClick={onClose}>Đóng</Btn>
        </div>
        <div style={{ padding: 20, overflow: "auto", display: "flex", flexDirection: "column", gap: 12 }}>{children}</div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------- nut va o nhap

export function Btn({ children, onClick, kind = "primary", small, disabled, title }: {
  children: ReactNode; onClick?: () => void; kind?: "primary" | "ghost" | "danger";
  small?: boolean; disabled?: boolean; title?: string;
}) {
  const colors: Record<string, CSSProperties> = {
    primary: { background: "var(--color-accent)", color: "var(--color-on-accent)", border: "1px solid var(--color-accent-dark)" },
    ghost: { background: "#fff", color: "#616161", border: "1px solid #E0E0E0" },
    danger: { background: "#fff", color: "#C62828", border: "1px solid #FFCDD2" },
  };
  return (
    <button onClick={onClick} disabled={disabled} title={title} style={{
      height: small ? 28 : 34, padding: small ? "0 10px" : "0 16px", borderRadius: 6,
      fontSize: small ? 12 : 13, fontWeight: 600, whiteSpace: "nowrap",
      cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.5 : 1, ...colors[kind],
    }}>{children}</button>
  );
}

export const INPUT: CSSProperties = {
  height: 34, padding: "0 10px", border: "1px solid #E0E0E0", borderRadius: 6,
  fontSize: 13, color: "#212121", background: "#fff", outline: "none", width: "100%", boxSizing: "border-box",
};

export const TEXTAREA: CSSProperties = {
  ...INPUT, height: "auto", minHeight: 90, padding: "8px 10px", resize: "vertical",
  fontFamily: "var(--font-sans)", lineHeight: 1.5,
};

/** Nhan + o nhap xep doc. */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 12, fontWeight: 600, color: "#616161", flex: 1, minWidth: 140 }}>
      {label}
      {children}
    </label>
  );
}

/** Mot hang cac o nhap nam ngang. */
export function Row({ children }: { children: ReactNode }) {
  return <div style={{ display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap" }}>{children}</div>;
}

// ------------------------------------------------------------ hien thi du lieu

export function Table({ head, rows, empty = "Chưa có dữ liệu." }: {
  head: ReactNode[]; rows: ReactNode[][]; empty?: string;
}) {
  const cell: CSSProperties = { padding: "8px 12px", textAlign: "left", borderBottom: "1px solid #F0F0F0", fontSize: 13, color: "#424242", verticalAlign: "top" };
  return (
    <div style={{ overflow: "auto", border: "1px solid #E0E0E0", borderRadius: 8 }}>
      <table style={{ width: "100%", borderCollapse: "collapse", background: "#fff" }}>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} style={{ ...cell, fontSize: 11, fontWeight: 700, color: "#757575", background: "var(--color-table-header-bg)", whiteSpace: "nowrap" }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0
            ? <tr><td colSpan={head.length} style={{ ...cell, textAlign: "center", color: "#9E9E9E", padding: 28 }}>{empty}</td></tr>
            : rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j} style={cell}>{c}</td>)}</tr>)}
        </tbody>
      </table>
    </div>
  );
}

const TAG_COLORS: Record<string, [string, string]> = {
  green: ["#E8F5E9", "#2E7D32"], red: ["#FFEBEE", "#C62828"], orange: ["#FFF3E0", "#E65100"],
  blue: ["#E3F2FD", "#1565C0"], gray: ["#F5F5F5", "#757575"], purple: ["#F3E5F5", "#6A1B9A"],
};

export function Tag({ children, color = "gray" }: { children: ReactNode; color?: keyof typeof TAG_COLORS | string }) {
  const [bg, fg] = TAG_COLORS[color] ?? TAG_COLORS.gray;
  return (
    <span style={{ display: "inline-block", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 10, background: bg, color: fg, whiteSpace: "nowrap" }}>
      {children}
    </span>
  );
}

export function ErrorBox({ text }: { text: string }) {
  if (!text) return null;
  return (
    <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 13, color: "#C62828", whiteSpace: "pre-wrap" }}>
      {text}
    </div>
  );
}

export function Muted({ children }: { children: ReactNode }) {
  return <div style={{ fontSize: 13, color: "#9E9E9E" }}>{children}</div>;
}

/** Dai canh bao cho cac module chi la ban mo phong. */
export function SimNote({ children }: { children: ReactNode }) {
  return (
    <div style={{ display: "flex", gap: 8, alignItems: "flex-start", padding: "8px 12px", background: "#FFF8E1", border: "1px solid #FFE082", borderRadius: 6, fontSize: 12.5, color: "#8D6E00" }}>
      <Tag color="orange">MÔ PHỎNG</Tag>
      <span>{children}</span>
    </div>
  );
}

/** Thanh tien do ngang, value tinh theo phan tram 0-100. */
export function Progress({ value, color = "var(--color-green)" }: { value: number; color?: string }) {
  return (
    <div style={{ height: 8, background: "#F0F0F0", borderRadius: 4, overflow: "hidden", minWidth: 80 }}>
      <div style={{ width: `${Math.max(0, Math.min(100, value))}%`, height: "100%", background: color }} />
    </div>
  );
}

/** Bieu do cot don gian: moi phan tu mot cot, cao theo ti le voi gia tri lon nhat. */
export function Bars({ items, color = "var(--color-accent)", height = 110 }: {
  items: { label: string; value: number }[]; color?: string; height?: number;
}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 6, height: height + 34 }}>
      {items.map((it) => (
        <div key={it.label} title={`${it.label}: ${it.value}`} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 3, minWidth: 0 }}>
          <span style={{ fontSize: 10, color: "#757575" }}>{it.value}</span>
          <div style={{ width: "100%", maxWidth: 34, height: Math.max(2, (it.value / max) * height), background: color, borderRadius: "3px 3px 0 0" }} />
          <span style={{ fontSize: 10, color: "#9E9E9E", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: "100%" }}>{it.label}</span>
        </div>
      ))}
    </div>
  );
}
