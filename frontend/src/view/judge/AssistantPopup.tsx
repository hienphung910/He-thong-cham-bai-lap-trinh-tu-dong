import { useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, INPUT, Tag, errorText } from "../../shared/kit";
import type { Problem } from "../../shared/types";

/** Mot luot trong khung chat: cau hoi cua hoc sinh hoac cau tra loi cua tro ly. */
interface Turn { from: "me" | "bot"; lines: string[] }

const QUICK = ["Gợi ý ý tưởng", "Vì sao bị TLE?", "Làm sao tự test?"];

/**
 * Tro ly AI dang pop-up o goc duoi ben phai trang lam bai (Module 28 - mo phong theo luat).
 * Tu biet dang o bai nao va lay luon ma nguon trong o soan, hoc sinh khong phai roi trang.
 * Moi lan nop bai xong (autoAskKey tang len) tro ly tu nhan xet mot lan.
 */
export default function AssistantPopup({ problem, code, autoAskKey }: {
  problem: Problem;
  /** Ma nguon dang co trong o soan cua trang lam bai. */
  code: string;
  /** So lan da nop trong trang nay; moi lan tang la mot lan tro ly tu nhan xet. */
  autoAskKey: number;
}) {
  const [open, setOpen] = useState(false);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [unread, setUnread] = useState(false);     // co cau tra loi moi trong luc khung dang dong
  const bottomRef = useRef<HTMLDivElement>(null);

  /** Gui mot cau hoi; shown la dong hien trong khung chat thay cho cau hoi (khi tro ly tu nhan xet). */
  async function ask(text: string, shown: string) {
    setBusy(true);
    setTurns((old) => [...old, { from: "me", lines: [shown] }]);
    try {
      const reply = await papi.hint({ problemId: problem.id, code, question: text });
      setTurns((old) => [...old, { from: "bot", lines: reply.lines }]);
    } catch (e) {
      setTurns((old) => [...old, { from: "bot", lines: ["Lỗi: " + errorText(e)] }]);
    } finally {
      setBusy(false);
    }
  }

  // Vua nop bai xong: tro ly tu xem ket qua va ma nguon roi nhan xet.
  useEffect(() => {
    if (autoAskKey === 0) return;
    void ask("", "(vừa nộp bài — nhờ trợ lý xem giúp)").then(() => setUnread(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoAskKey]);

  useEffect(() => { bottomRef.current?.scrollIntoView({ block: "nearest" }); }, [turns.length, open]);

  function send(text: string) {
    if (!text.trim() || busy) return;
    setQuestion("");
    void ask(text, text);
  }

  return (
    <>
      {/* Nut tron luon nam o goc duoi ben phai */}
      <button onClick={() => { setOpen(!open); setUnread(false); }} title={open ? "Đóng trợ lý" : "Hỏi trợ lý về bài này"}
        aria-label={open ? "Đóng trợ lý" : "Hỏi trợ lý về bài này"} style={{
          position: "fixed", right: 24, bottom: 24, zIndex: 250, width: 52, height: 52, borderRadius: "50%",
          border: "none", background: "#111111", color: "var(--color-accent)", cursor: "pointer",
          display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 6px 20px rgba(0,0,0,0.25)",
        }}>
        {open ? (
          // dau X: dang mo thi bam de dong
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><line x1="5" y1="5" x2="15" y2="15" /><line x1="15" y1="5" x2="5" y2="15" /></svg>
        ) : (
          // bong tin nhan
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 11.5a8.5 8.5 0 0 1-12.6 7.4L3 20.5l1.6-5.1A8.5 8.5 0 1 1 21 11.5z" />
            <line x1="8.5" y1="10" x2="15.5" y2="10" /><line x1="8.5" y1="13.5" x2="13" y2="13.5" />
          </svg>
        )}
        {/* Cham vang: tro ly vua co nhan xet moi trong luc khung dang dong */}
        {unread && !open && <span style={{ position: "absolute", top: 2, right: 2, width: 14, height: 14, borderRadius: "50%", background: "#FFEB3B", border: "2px solid #fff" }} />}
      </button>

      {open && (
        <div style={{
          position: "fixed", right: 24, bottom: 84, zIndex: 250, width: 380, maxWidth: "calc(100vw - 48px)",
          height: 480, maxHeight: "calc(100vh - 150px)", display: "flex", flexDirection: "column",
          background: "#fff", border: "1px solid #E0E0E0", borderRadius: 12, boxShadow: "0 12px 40px rgba(0,0,0,0.25)",
        }}>
          <div style={{ padding: "10px 14px", borderBottom: "1px solid #F0F0F0", display: "flex", gap: 8, alignItems: "center" }}>
            <b style={{ fontSize: 14, color: "#212121" }}>Trợ lý · {problem.id}</b>
            <Tag color="orange">MÔ PHỎNG</Tag>
          </div>

          <div style={{ flex: 1, overflow: "auto", padding: 12, display: "flex", flexDirection: "column", gap: 10, background: "#FAFAFA" }}>
            {turns.length === 0 && (
              <div style={{ fontSize: 12.5, color: "#757575", lineHeight: 1.6 }}>
                Hỏi về bài <b>{problem.title}</b>. Trợ lý đọc kết quả lần nộp gần nhất và mã nguồn đang có trong ô soạn,
                chỉ gợi ý hướng làm chứ không đưa lời giải. Câu trả lời sinh theo luật viết sẵn, không phải mô hình AI.
              </div>
            )}
            {turns.map((t, i) => (
              <div key={i} style={{
                alignSelf: t.from === "me" ? "flex-end" : "flex-start", maxWidth: "88%",
                background: t.from === "me" ? "var(--color-red-light)" : "#fff",
                border: "1px solid #E0E0E0", borderRadius: 10, padding: "7px 11px", fontSize: 13, lineHeight: 1.55, color: "#424242",
              }}>
                {t.lines.map((line, j) => <div key={j} style={{ marginTop: j > 0 ? 6 : 0 }}>{line}</div>)}
              </div>
            ))}
            {busy && <div style={{ fontSize: 12, color: "#9E9E9E" }}>Trợ lý đang trả lời...</div>}
            <div ref={bottomRef} />
          </div>

          <div style={{ padding: 10, borderTop: "1px solid #F0F0F0", display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {QUICK.map((q) => <Btn key={q} small kind="ghost" disabled={busy} onClick={() => send(q)}>{q}</Btn>)}
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <input style={INPUT} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="Nhập câu hỏi rồi nhấn Enter..."
                onKeyDown={(e) => { if (e.key === "Enter") send(question); }} />
              <Btn disabled={busy || !question.trim()} onClick={() => send(question)}>Gửi</Btn>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
