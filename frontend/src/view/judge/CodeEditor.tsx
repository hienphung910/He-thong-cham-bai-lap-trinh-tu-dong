const BOX: React.CSSProperties = {
  width: "100%", boxSizing: "border-box", margin: 0, padding: "10px 12px",
  background: "#FAFAFA", border: "1px solid #E0E0E0", borderRadius: 6,
  color: "#212121", fontFamily: "var(--font-mono)", fontSize: 13, lineHeight: 1.6,
  tabSize: 4, whiteSpace: "pre", overflow: "auto", outline: "none",
};

/**
 * O soan ma nguon: mot textarea voi phong chu deu. Phim Tab chen 4 dau cach thay vi
 * nhay sang o khac. Che do readOnly chi hien ma nguon da nop (khong sua duoc).
 */
export default function CodeEditor({
  value,
  onChange,
  placeholder = "Dán mã nguồn của bạn vào đây...",
  minHeight = 360,
  maxHeight,
  readOnly = false,
}: {
  value: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  minHeight?: number;
  /** Co thi khung chi cao toi day, ben trong tu cuon - dung cho che do xem lai. */
  maxHeight?: number;
  readOnly?: boolean;
}) {
  if (readOnly) {
    return <pre style={{ ...BOX, maxHeight }}>{value}</pre>;
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== "Tab") return;
    e.preventDefault();
    const box = e.currentTarget;
    const start = box.selectionStart;
    onChange?.(value.slice(0, start) + "    " + value.slice(box.selectionEnd));
    // Doi React ve lai noi dung moi roi moi dat con tro ra sau 4 dau cach vua chen.
    requestAnimationFrame(() => { box.selectionStart = box.selectionEnd = start + 4; });
  }

  return (
    <textarea
      value={value}
      placeholder={placeholder}
      spellCheck={false}
      onChange={(e) => onChange?.(e.target.value)}
      onKeyDown={handleKeyDown}
      style={{ ...BOX, minHeight, maxHeight, resize: "vertical" }}
    />
  );
}
