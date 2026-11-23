// Logo CodeHub tren thanh tieu de: chu "Code" va khoi cam chua chu "hub" den, nen trong suot.
//   size      : chieu cao logo.
//   codeColor : mau chu "Code", mac dinh trang cho nen do cua thanh tieu de.
// Favicon (public/favicon.svg) dung cung bo mau, rut gon thanh "CH" tren nen den.
const ORANGE = "#F7931E";
const FONT = "'Arial Black', 'Helvetica Neue', Arial, sans-serif";

export default function Logo({ size, codeColor = "#fff" }: { size: number; codeColor?: string }) {
  return (
    <span
      aria-label="CodeHub"
      style={{
        display: "inline-flex", alignItems: "center", gap: Math.round(size * 0.14), height: size,
        fontFamily: FONT, fontWeight: 900, fontSize: Math.round(size * 0.6), lineHeight: 1, letterSpacing: "-0.02em",
      }}
    >
      <span style={{ color: codeColor }}>Code</span>
      <span style={{
        background: ORANGE, color: "#000", borderRadius: Math.round(size * 0.12),
        padding: `${Math.round(size * 0.08)}px ${Math.round(size * 0.14)}px`,
      }}>hub</span>
    </span>
  );
}
