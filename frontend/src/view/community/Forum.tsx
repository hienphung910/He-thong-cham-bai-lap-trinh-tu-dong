import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Row, TEXTAREA, Tag, errorText, fmtTime, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";

/** Module 31 (dien dan thao luan): dang chu de, tra loi, xoa chu de cua minh. */
export default function Forum() {
  const { user } = useAuth();
  const list = useLoad(() => papi.forum());
  const [openId, setOpenId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ title: "", body: "", problemId: "" });

  async function create() {
    setError("");
    try {
      await papi.createPost(form);
      setForm({ title: "", body: "", problemId: "" });
      list.reload();
    } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <PostDetail id={openId} onBack={() => { setOpenId(null); list.reload(); }} />;

  return (
    <Page title="Diễn đàn" subtitle="Hỏi đáp về bài tập và trao đổi kinh nghiệm.">
      <ErrorBox text={error || list.error} />
      <Card title="Đăng chủ đề mới">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Row>
            <Field label="Tiêu đề"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
            <div style={{ width: 180 }}><Field label="Về bài (không bắt buộc)"><input style={INPUT} value={form.problemId} onChange={(e) => setForm({ ...form, problemId: e.target.value })} placeholder="K001" /></Field></div>
          </Row>
          <Field label="Nội dung"><textarea style={TEXTAREA} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} /></Field>
          <div><Btn onClick={() => void create()}>Đăng</Btn></div>
        </div>
      </Card>

      {list.data?.length === 0 && <Muted>Chưa có chủ đề nào.</Muted>}
      {(list.data ?? []).map((p) => (
        <Card key={p.id}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <button onClick={() => setOpenId(p.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", fontSize: 15, fontWeight: 700, color: "#212121", textAlign: "left" }}>
              {p.title}
            </button>
            {p.problemId && <ProblemChip id={p.problemId} />}
            <Tag color="blue">{p.replyCount} trả lời</Tag>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "#9E9E9E" }}>{p.authorName} · {fmtTime(p.createdAt)}</span>
            {(user?.role !== "STUDENT" || user?.id === p.authorId) && (
              <Btn small kind="danger" onClick={async () => {
                if (!window.confirm(`Xoá chủ đề "${p.title}"?`)) return;
                try { await papi.deletePost(p.id); list.reload(); } catch (e) { setError(errorText(e)); }
              }}>Xoá</Btn>
            )}
          </div>
        </Card>
      ))}
    </Page>
  );
}

function PostDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const post = useLoad(() => papi.forumPost(id), [id]);
  const [reply, setReply] = useState("");
  const [error, setError] = useState("");
  const p = post.data;

  async function send() {
    setError("");
    try { await papi.replyPost(id, reply); setReply(""); post.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title={p ? p.title : "Chủ đề"} subtitle={p ? `${p.authorName} · ${fmtTime(p.createdAt)}` : ""}
      actions={<Btn kind="ghost" onClick={onBack}>← Diễn đàn</Btn>}>
      <ErrorBox text={error || post.error} />
      {p && (
        <>
          <Card>
            {p.problemId && <div style={{ marginBottom: 8 }}><ProblemChip id={p.problemId} /></div>}
            <div style={{ fontSize: 14, color: "#424242", whiteSpace: "pre-wrap", lineHeight: 1.65 }}>{p.body}</div>
          </Card>
          <Card title={`Trả lời (${p.replies?.length ?? 0})`}>
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {(p.replies ?? []).map((r, i) => (
                <div key={i} style={{ borderLeft: "3px solid #FDECEA", paddingLeft: 12 }}>
                  <div style={{ fontSize: 12, color: "#9E9E9E" }}><b style={{ color: "#212121" }}>{r.authorName}</b> · {fmtTime(r.createdAt)}</div>
                  <div style={{ fontSize: 13.5, color: "#424242", whiteSpace: "pre-wrap" }}>{r.body}</div>
                </div>
              ))}
              <textarea style={TEXTAREA} value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Viết câu trả lời..." />
              <div><Btn disabled={!reply.trim()} onClick={() => void send()}>Gửi trả lời</Btn></div>
            </div>
          </Card>
        </>
      )}
    </Page>
  );
}
