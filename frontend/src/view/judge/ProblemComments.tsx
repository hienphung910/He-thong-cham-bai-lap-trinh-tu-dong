import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, Muted, TEXTAREA, errorText, fmtTime, useLoad } from "../../shared/kit";

const MAX_LENGTH = 1000;

/**
 * Binh luan cua nguoi dung duoi mot bai tap. Khong qua nguoi duyet: may chu loc tu bay va ma nguon
 * loi giai luc gui. Qua duoc thi hien ngay; khong qua thi may chu tra ve ly do va noi dung dang go
 * duoc giu nguyen de sua.
 */
export default function ProblemComments({ problemId }: { problemId: string }) {
  const list = useLoad(() => papi.comments(problemId), [problemId]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const comments = list.data ?? [];

  async function send() {
    setBusy(true);
    setError("");
    try {
      await papi.addComment(problemId, text.trim());
      setText("");
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
      <div style={{ fontSize: 15, fontWeight: 700, color: "#212121" }}>Bình luận ({comments.length})</div>
      <ErrorBox text={list.error} />

      {comments.length === 0 && !list.error && <Muted>Chưa có bình luận nào cho bài này.</Muted>}
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {comments.map((c) => (
          <div key={c.id} style={{ borderLeft: "3px solid var(--color-accent)", paddingLeft: 12 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 12.5 }}>
              <b style={{ color: "#212121" }}>{c.authorName}</b>
              <span style={{ color: "#9E9E9E" }}>{c.authorRole} · {fmtTime(c.createdAt)}</span>
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
        {/* Ly do bo loc tu choi hien ngay duoi o nhap, noi dung dang go van con de sua. */}
        <ErrorBox text={error} />
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <Btn disabled={busy || !text.trim()} onClick={() => void send()}>{busy ? "Đang gửi..." : "Gửi bình luận"}</Btn>
          <span style={{ fontSize: 12, color: "#9E9E9E" }}>{text.length}/{MAX_LENGTH}</span>
        </div>
      </div>
    </div>
  );
}
