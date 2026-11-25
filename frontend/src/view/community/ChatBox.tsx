import { useCallback, useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, INPUT, errorText } from "../../shared/kit";
import type { ChatMessage } from "../../shared/platformTypes";

/**
 * Khung chat cua mot kenh (Module 30). Khong dung WebSocket: cu 2 giay hoi may chu
 * mot lan, gui kem so tin da co de may chu chi tra ve cac tin moi.
 */
export default function ChatBox({ channel, height = 300 }: { channel: string; height?: number }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const countRef = useRef(0);        // so thu tu (seq) cua tin cuoi cung da nhan
  const busyRef = useRef(false);     // dang co mot lan hoi chua xong -> bo qua lan nay
  const bottomRef = useRef<HTMLDivElement>(null);
  const channelRef = useRef(channel);   // kenh dang hien; tra loi cua kenh cu se bi bo qua

  const fetchNew = useCallback(async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    try {
      const more = await papi.chat(channel, countRef.current);
      // Trong luc cho, nguoi dung da chuyen sang kenh khac: khong tron tin cua kenh cu vao.
      if (channelRef.current !== channel) return;
      if (more.length > 0) {
        countRef.current = more[more.length - 1].seq;
        setMessages((old) => [...old, ...more]);
      }
      setError("");
    } catch (e) {
      if (channelRef.current === channel) setError(errorText(e));
    } finally {
      if (channelRef.current === channel) busyRef.current = false;
    }
  }, [channel]);

  useEffect(() => {
    channelRef.current = channel;
    busyRef.current = false;     // lan hoi dang do cua kenh cu khong duoc chan lan hoi dau cua kenh moi
    countRef.current = 0;
    setMessages([]);
    void fetchNew();
    const timer = setInterval(() => void fetchNew(), 2000);
    return () => clearInterval(timer);
  }, [channel, fetchNew]);

  // Co tin moi thi cuon xuong cuoi.
  useEffect(() => { bottomRef.current?.scrollIntoView({ block: "nearest" }); }, [messages.length]);

  async function send() {
    if (!text.trim()) return;
    try {
      await papi.sendChat(channel, text);
      setText("");
      await fetchNew();
    } catch (e) {
      setError(errorText(e));
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ height, overflow: "auto", border: "1px solid #E0E0E0", borderRadius: 6, padding: 10, background: "#FAFAFA", display: "flex", flexDirection: "column", gap: 8 }}>
        {messages.length === 0 && <span style={{ fontSize: 12.5, color: "#9E9E9E" }}>Chưa có tin nhắn nào.</span>}
        {messages.map((m) => (
          <div key={m.id} style={{ fontSize: 13, lineHeight: 1.45 }}>
            <b style={{ color: m.senderRole === "Sinh viên" ? "#1565C0" : "var(--color-red)" }}>{m.senderName}</b>
            <span style={{ fontSize: 10.5, color: "#9E9E9E", marginLeft: 6 }}>{m.sentAt.slice(11, 16)}</span>
            <div style={{ color: "#424242", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{m.text}</div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <ErrorBox text={error} />
      <div style={{ display: "flex", gap: 8 }}>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Nhập tin nhắn rồi nhấn Enter..."
          onKeyDown={(e) => { if (e.key === "Enter") void send(); }} style={INPUT} />
        <Btn onClick={() => void send()}>Gửi</Btn>
      </div>
    </div>
  );
}
