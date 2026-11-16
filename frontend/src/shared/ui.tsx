import { useEffect, useRef, useState } from "react";

function ChevDown({ size = 11 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="2 4 6 8 10 4" /></svg>;
}

function XIco({ size = 10 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="1" y1="1" x2="9" y2="9" /><line x1="9" y1="1" x2="1" y2="9" /></svg>;
}

/** Hop chon mot gia tri trong danh sach; bam lai gia tri dang chon (hoac dau x) de bo chon. */
export function Select({
  value, options, onChange, placeholder, disabledOptions = [],
}: {
  value: string; options: string[]; onChange: (v: string) => void; placeholder: string; disabledOptions?: string[];
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function h(e: MouseEvent) { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); }
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  return (
    <div ref={ref} style={{ position: "relative" }}>
      <button onClick={() => setOpen((v) => !v)} style={{
        display: "flex", alignItems: "center", gap: 6, height: 34, padding: "0 12px",
        background: value ? "#FDECEA" : "#fff", border: `1px solid ${value ? "var(--color-red)" : "#E0E0E0"}`,
        borderRadius: 6, color: value ? "var(--color-red)" : "#9E9E9E",
        fontSize: 13, cursor: "pointer", fontWeight: value ? 600 : 400, whiteSpace: "nowrap",
      }}>
        {value || placeholder}
        {value ? <span onClick={(e) => { e.stopPropagation(); onChange(""); }} style={{ display: "flex", color: "var(--color-red)", marginLeft: 2 }}><XIco size={9} /></span> : <ChevDown size={10} />}
      </button>
      {open && (
        <div style={{
          position: "absolute", top: "calc(100% + 4px)", left: 0, zIndex: 200,
          background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8,
          boxShadow: "0 8px 24px rgba(0,0,0,0.1)", minWidth: 160, maxHeight: 240,
          overflowY: "auto", padding: 4, animation: "slideIn 0.1s ease",
        }}>
          {options.map((opt) => {
            const disabled = disabledOptions.includes(opt);
            const selected = opt === value;
            return (
              <button key={opt} disabled={disabled} onClick={() => { onChange(selected ? "" : opt); setOpen(false); }}
                style={{
                  width: "100%", display: "block", textAlign: "left", padding: "7px 12px",
                  background: selected ? "#FDECEA" : "none", border: "none",
                  color: disabled ? "#BDBDBD" : selected ? "var(--color-red)" : "#616161",
                  fontSize: 13, cursor: disabled ? "not-allowed" : "pointer", borderRadius: 4,
                  fontWeight: selected ? 600 : 400, textDecoration: disabled ? "line-through" : "none",
                }}
                onMouseEnter={(e) => { if (!disabled && !selected) e.currentTarget.style.background = "#F5F5F5"; }}
                onMouseLeave={(e) => { if (!disabled && !selected) e.currentTarget.style.background = "none"; }}
              >{selected ? `✓ ${opt}` : opt}</button>
            );
          })}
        </div>
      )}
    </div>
  );
}
