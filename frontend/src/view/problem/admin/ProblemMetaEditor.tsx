import { useMemo, useState } from "react";
import { api } from "../../../shared/api";
import { DIFFICULTY_META } from "../learningPath";
import type { Problem, Topic } from "../../../shared/types";

const LABEL: React.CSSProperties = {
  fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 600, color: "var(--color-text-muted)",
  textTransform: "uppercase", letterSpacing: "0.09em", marginBottom: 4, display: "block",
};
const INPUT: React.CSSProperties = {
  width: "100%", height: 34, padding: "0 10px", background: "var(--color-bg-base)",
  border: "1px solid var(--color-border)", borderRadius: 7, color: "var(--color-text-primary)",
  fontFamily: "var(--font-sans)", fontSize: 12.5, outline: "none", boxSizing: "border-box",
};

/**
 * Modal "Sua bai trong lo trinh": doi chu de va do kho cua mot bai da co
 * (PUT /api/admin/problems/{id}). De bai va bo test khong sua o day.
 */
export default function ProblemMetaEditor({ problem, topics, onClose, onSaved }: {
  problem: Problem;
  topics: Topic[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const known = useMemo(() => new Set(topics.map((t) => t.id)), [topics]);
  // Bai gan chu de khong con trong lo trinh: chon san "Bai khac", vi backend khong nhan ma la.
  const startTopic = known.has(problem.topic) ? problem.topic : "";

  const [topic, setTopic] = useState(startTopic);
  const [difficulty, setDifficulty] = useState(problem.difficulty);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const topicLabel = (id: string) => {
    const t = topics.find((x) => x.id === id);
    return t ? `${t.order}. ${t.name}` : id ? `${id} (không còn trong lộ trình)` : "Bài khác";
  };

  async function save() {
    setError("");
    setBusy(true);
    try {
      await api.updateProblem(problem.id, { topic, difficulty });
      await onSaved();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  const unchanged = topic === problem.topic && difficulty === problem.difficulty;

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(28,20,16,0.5)", zIndex: 999, display: "flex", alignItems: "flex-start", justifyContent: "center", overflowY: "auto", padding: "48px 16px" }}>
      <div style={{ background: "var(--color-bg-panel)", border: "1px solid var(--color-border)", borderRadius: 14, width: "100%", maxWidth: 560, boxShadow: "0 24px 64px rgba(28,20,16,0.18)" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "20px 28px", borderBottom: "1px solid var(--color-border)" }}>
          <div>
            <h2 style={{ fontFamily: "var(--font-serif)", fontSize: 20, fontWeight: 700, color: "var(--color-maroon)", margin: 0, letterSpacing: "-0.02em" }}>Sửa bài trong lộ trình</h2>
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--color-text-muted)", marginTop: 4 }}>
              <b style={{ color: "var(--color-maroon)" }}>{problem.id}</b> · {problem.title}
            </div>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--color-text-muted)", padding: 6, borderRadius: 5, display: "flex" }}>
            <svg width="15" height="15" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="1" y1="1" x2="9" y2="9" /><line x1="9" y1="1" x2="1" y2="9" /></svg>
          </button>
        </div>

        <div style={{ padding: "22px 28px", display: "flex", flexDirection: "column", gap: 14 }}>
          {error && (
            <div style={{ padding: "10px 14px", borderRadius: 7, fontSize: 12.5, background: "var(--color-red-bg)", border: "1px solid var(--color-red-border)", color: "var(--color-red)", fontFamily: "var(--font-mono)", whiteSpace: "pre-wrap" }}>{error}</div>
          )}

          <div style={{ fontFamily: "var(--font-sans)", fontSize: 12.5, color: "var(--color-text-secondary)" }}>
            Hiện tại: <b>{topicLabel(problem.topic)}</b>, độ khó{" "}
            <b>{DIFFICULTY_META[problem.difficulty]?.label ?? "chưa đánh giá"}</b>.
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 150px", gap: 10 }}>
            <div>
              <label style={LABEL}>Chủ đề</label>
              <select value={topic} onChange={(e) => setTopic(e.target.value)} style={INPUT}>
                {topics.map((t) => <option key={t.id} value={t.id}>{t.order}. {t.name}</option>)}
                <option value="">Bài khác (chưa phân loại)</option>
              </select>
            </div>
            <div>
              <label style={LABEL}>Độ khó</label>
              <select value={difficulty} onChange={(e) => setDifficulty(Number(e.target.value))} style={INPUT}>
                <option value={0}>Chưa đánh giá</option>
                {[1, 2, 3].map((d) => <option key={d} value={d}>{DIFFICULTY_META[d].label}</option>)}
              </select>
            </div>
          </div>

          <div style={{ fontFamily: "var(--font-mono)", fontSize: 10.5, color: "var(--color-text-muted)", lineHeight: 1.7, borderTop: "1px solid var(--color-border-subtle)", paddingTop: 10 }}>
            Lưu vào problem.properties của bài (topic, difficulty).
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 10, padding: "16px 28px", borderTop: "1px solid var(--color-border)" }}>
          <button onClick={onClose} style={{ height: 36, padding: "0 20px", background: "none", border: "1px solid var(--color-border)", borderRadius: 7, color: "var(--color-text-muted)", fontSize: 13, cursor: "pointer", fontFamily: "var(--font-sans)" }}>Huỷ</button>
          <button onClick={() => void save()} disabled={busy || unchanged} style={{ height: 36, padding: "0 24px", background: "var(--color-maroon)", border: "none", borderRadius: 7, color: "#FAF7F2", fontSize: 13, fontWeight: 700, cursor: busy || unchanged ? "not-allowed" : "pointer", opacity: busy || unchanged ? 0.5 : 1, fontFamily: "var(--font-sans)" }}>
            {busy ? "Đang lưu..." : "Lưu thay đổi"}
          </button>
        </div>
      </div>
    </div>
  );
}
