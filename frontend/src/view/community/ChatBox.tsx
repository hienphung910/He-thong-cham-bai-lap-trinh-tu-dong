import { useCallback, useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, INPUT, errorText } from "../../shared/kit";
import { confirmDialog } from "../../shared/dialog";
import type { ChatMessage } from "../../shared/platformTypes";
import "./chat.css";

/**
 * Khung chat cua mot kenh (Module 30). Khong dung WebSocket: cu 2 giay hoi may chu
 * mot lan, gui kem so tin da co de may chu chi tra ve cac tin moi.
 *
 * Xoa tin: nguoi gui xoa tin cua minh; quan tri vien / giang vien cua lop xoa tin bat ky va xoa ca lich
 * su kenh. May chu bao "revision" cua kenh; so nay doi (co tin bi xoa, ke ca o may nguoi khac) thi tai lai
 * ca kenh de tin da xoa bien mat o moi trinh duyet.
 */
export default function ChatBox({ channel, height = 300 }: { channel: string; height?: number }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [canModerate, setCanModerate] = useState(false);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  // Loi cua lan GUI / XOA gan nhat (bi bo loc chan, gui qua nhanh...). Tach khoi `error` vi lan hoi tin moi
  // 2 giay mot lan se xoa `error`, nguoi dung chua kip doc vi sao tin khong gui duoc.
  const [sendError, setSendError] = useState("");
  const countRef = useRef(0);        // so thu tu (seq) cua tin cuoi cung da nhan
  const revisionRef = useRef<number | null>(null);   // revision cua kenh luc tai; null = chua tai
  const busyRef = useRef(false);     // dang co mot lan hoi chua xong -> bo qua lan nay
  const bottomRef = useRef<HTMLDivElement>(null);
  const channelRef = useRef(channel);   // kenh dang hien; tra loi cua kenh cu se bi bo qua

  const fetchNew = useCallback(async (force = false) => {
    if (busyRef.current && !force) return;
    busyRef.current = true;
    try {
      let page = await papi.chat(channel, countRef.current);
      if (channelRef.current !== channel) return;
      // Co tin bi xoa tu lan truoc: tai lai tu dau.
      const reload = revisionRef.current !== null && page.revision !== revisionRef.current;
      if (reload && countRef.current > 0) {
        page = await papi.chat(channel, 0);
        if (channelRef.current !== channel) return;
      }
      revisionRef.current = page.revision;
      setCanModerate(page.canModerate);
      if (reload) {
        countRef.current = page.items.length > 0 ? page.items[page.items.length - 1].seq : countRef.current;
        setMessages(page.items);
      } else if (page.items.length > 0) {
        countRef.current = Math.max(countRef.current, page.items[page.items.length - 1].seq);
        // Hai lan hoi chay chong nhau (vua gui tin vua den lan hoi dinh ky) co the tra ve cung mot tin.
        setMessages((old) => {
          const known = new Set(old.map((x) => x.id));
          return [...old, ...page.items.filter((x) => !known.has(x.id))];
        });
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
    revisionRef.current = null;
    setMessages([]);
    setCanModerate(false);
    void fetchNew();
    const timer = setInterval(() => void fetchNew(), 2000);
    return () => clearInterval(timer);
  }, [channel, fetchNew]);

  // Co tin moi thi cuon xuong cuoi.
  useEffect(() => { bottomRef.current?.scrollIntoView({ block: "nearest" }); }, [messages.length]);

  async function send() {
    if (!text.trim()) return;
    setSendError("");
    try {
      await papi.sendChat(channel, text);
      setText("");
      await fetchNew(true);
    } catch (e) {
      setSendError(errorText(e));
    }
  }

  async function remove(m: ChatMessage) {
    setSendError("");
    // An ngay tren man hinh; may chu doi revision nen lan hoi sau cung khop.
    setMessages((old) => old.filter((x) => x.id !== m.id));
    try {
      await papi.deleteChatMessage(channel, m.id);
    } catch (e) {
      setSendError(errorText(e));
    }
    await fetchNew(true);
  }

  async function clearAll() {
    const ok = await confirmDialog({
      title: "Xoá toàn bộ lịch sử kênh này?",
      message: "Mọi tin nhắn trong kênh sẽ bị xoá với tất cả thành viên và không khôi phục được.",
      confirmLabel: "Xoá lịch sử",
    });
    if (!ok) return;
    setSendError("");
    try {
      await papi.clearChat(channel);
      setMessages([]);
    } catch (e) {
      setSendError(errorText(e));
    }
    await fetchNew(true);
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {canModerate && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: -4 }}>
          <button className="chat-clear" disabled={messages.length === 0} onClick={() => void clearAll()}>
            <TrashIcon />Xoá lịch sử
          </button>
        </div>
      )}
      <div className="chat-list" style={{ height }}>
        {messages.length === 0 && <span style={{ fontSize: 12.5, color: "#9E9E9E", padding: "2px 6px" }}>Chưa có tin nhắn nào.</span>}
        {messages.map((m) => (
          <div key={m.id} className="chat-msg">
            <div className="chat-msg-body">
              <b style={{ color: m.senderRole === "Sinh viên" ? "#1565C0" : "var(--color-red)" }}>{m.senderName}</b>
              <span className="chat-time">{m.sentAt.slice(11, 16)}</span>
              <div className="chat-text">{m.text}</div>
            </div>
            {m.canDelete && (
              <button className="chat-del" aria-label="Xoá tin nhắn" title={m.mine ? "Xoá tin nhắn của bạn" : "Xoá tin nhắn"}
                onClick={() => void remove(m)}>
                <TrashIcon />
              </button>
            )}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <ErrorBox text={sendError || error} />
      <div style={{ display: "flex", gap: 8 }}>
        <input value={text} onChange={(e) => { setText(e.target.value); setSendError(""); }} placeholder="Nhập tin nhắn rồi nhấn Enter..."
          onKeyDown={(e) => { if (e.key === "Enter") void send(); }} style={INPUT} />
        <Btn onClick={() => void send()}>Gửi</Btn>
      </div>
    </div>
  );
}

function TrashIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2.5 4.5h11M6.5 4.5V3h3v1.5M4 4.5l.7 8.5h6.6l.7-8.5M6.8 7v4M9.2 7v4" />
    </svg>
  );
}
