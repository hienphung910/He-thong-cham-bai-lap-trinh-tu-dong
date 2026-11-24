import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, Muted, Tag, TEXTAREA, errorText, fmtTime, useLoad } from "../../shared/kit";
import { useAuth } from "../account/auth";

const MAX_LENGTH = 1000;

/**
 * Binh luan cua nguoi dung duoi mot bai tap. Binh luan moi phai duoc quan tri vien duyet thi
 * nguoi khac moi thay; trong luc cho, chi tac gia thay binh luan cua minh kem nhan "Cho duyet".
 * Binh luan cua quan tri vien hien ngay.
 */
export default function ProblemComments({ problemId }: { problemId: string }) {
  const { user } = useAuth();
  const list = useLoad(() => papi.comments(problemId), [problemId]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const comments = list.data ?? [];
  const approved = comments.filter((c) => c.status === "APPROVED").length;

  async function send() {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const created = await papi.addComment(problemId, text.trim());
      setText("");
      setNotice(created.status === "APPROVED"
        ? "Đã đăng bình luận."
        : "Đã gửi bình luận. Bình luận sẽ hiện cho mọi người sau khi quản trị viên duyệt.");
      list.reload();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!window.confirm("Xoá bình luận này?")) return;
    setError("");
    try { await papi.deleteComment(id); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "20px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
      <div style={{ fontSize: 15, fontWeight: 700, color: "#212121" }}>Bình luận ({approved})</div>
      <ErrorBox text={error || list.error} />

      {comments.length === 0 && !list.error && <Muted>Chưa có bình luận nào cho bài này.</Muted>}
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {comments.map((c) => (
          <div key={c.id} style={{ borderLeft: "3px solid var(--color-accent)", paddingLeft: 12, opacity: c.status === "PENDING" ? 0.75 : 1 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 12.5 }}>
              <b style={{ color: "#212121" }}>{c.authorName}</b>
              <span style={{ color: "#9E9E9E" }}>{c.authorRole} · {fmtTime(c.createdAt)}</span>
              {c.status === "PENDING" && <Tag color="orange">Chờ duyệt</Tag>}
              {c.canDelete && (
                <button onClick={() => void remove(c.id)} style={{
                  background: "none", border: "none", padding: 0, cursor: "pointer", fontSize: 12, color: "#C62828",
                }}>Xoá</button>
              )}
            </div>
            <div style={{ marginTop: 4, fontSize: 13.5, color: "#424242", lineHeight: 1.6, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{c.text}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        <textarea style={{ ...TEXTAREA, minHeight: 70 }} value={text} maxLength={MAX_LENGTH}
          onChange={(e) => setText(e.target.value)} placeholder="Viết bình luận về bài này (hỏi đề, chia sẻ hướng làm...)" />
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <Btn disabled={busy || !text.trim()} onClick={() => void send()}>{busy ? "Đang gửi..." : "Gửi bình luận"}</Btn>
          <span style={{ fontSize: 12, color: "#9E9E9E" }}>{text.length}/{MAX_LENGTH}</span>
          {notice
            ? <span style={{ fontSize: 13, color: "#2E7D32" }}>{notice}</span>
            : user?.role !== "ADMIN" && <span style={{ fontSize: 12, color: "#9E9E9E" }}>Bình luận cần được quản trị viên duyệt trước khi hiện cho mọi người.</span>}
        </div>
      </div>
    </div>
  );
}
