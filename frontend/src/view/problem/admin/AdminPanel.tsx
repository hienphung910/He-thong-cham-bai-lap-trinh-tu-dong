import { useMemo, useState } from "react";
import { api } from "../../../shared/api";
import { DIFFICULTY_META, OTHER_TOPIC, orderByPath, topicKey } from "../learningPath";
import type { Problem, Topic } from "../../../shared/types";
import ProblemEditor from "./ProblemEditor";
import ProblemMetaEditor from "./ProblemMetaEditor";
import TestcaseManager from "./TestcaseManager";

/**
 * Trang quan ly bai tap: tao bai moi, sua bo test, doi chu de va do kho cua bai da co.
 * Dung cho hai noi: quan tri vien voi KHO CHUNG, va giang vien voi KHO RIENG (mine = true).
 * Trang chi nhan dung danh sach bai cua kho do; may chu tu gan chu bai khi tao.
 */
export default function AdminPanel({ problems, topics, onProblemsChanged, mine = false }: {
  problems: Problem[]; topics: Topic[]; onProblemsChanged: () => Promise<void>;
  /** true = kho rieng cua giang vien dang dang nhap. */
  mine?: boolean;
}) {
  const [showProblemEditor, setShowProblemEditor] = useState(false);
  const [editProblem, setEditProblem] = useState<Problem | null>(null);
  const [testProblem, setTestProblem] = useState<Problem | null>(null);
  /** "" = moi chu de, ma chu de, hoac OTHER_TOPIC. */
  const [topicFilter, setTopicFilter] = useState("");

  /** Xoa han mot bai. May chu tu choi neu bai da co nguoi nop hoac dang nam trong ky thi / bai giao / lop. */
  async function removeProblem(p: Problem) {
    if (!window.confirm(`Xoá bài ${p.id} — ${p.title}?\n\nĐề bài và toàn bộ test sẽ bị xoá, không khôi phục được.`)) return;
    try {
      await api.deleteProblem(p.id);
      await onProblemsChanged();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : String(e));
    }
  }

  const known = useMemo(() => new Set(topics.map((t) => t.id)), [topics]);
  const ordered = useMemo(() => orderByPath(problems, topics), [problems, topics]);
  const visibleProblems = topicFilter ? ordered.filter((p) => topicKey(p, known) === topicFilter) : ordered;
  const topicLabel = (p: Problem) => {
    const t = topics.find((x) => x.id === p.topic);
    return t ? `${t.order}. ${t.name}` : p.topic ? `${p.topic} (không còn trong lộ trình)` : "Bài khác";
  };

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", padding: "0 32px 24px" }}>
      <div style={{ paddingTop: 28, paddingBottom: 20, borderBottom: "1px solid var(--color-border)", marginBottom: 24, display: "flex", alignItems: "flex-end", justifyContent: "space-between", flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
            <div style={{ width: 30, height: 30, borderRadius: 7, background: "var(--color-maroon)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="#FAF7F2" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="3" width="12" height="10" rx="1.5" /><path d="M5 7h6M5 10h4" /></svg>
            </div>
            <h1 style={{ fontFamily: "var(--font-serif)", fontSize: 26, fontWeight: 700, color: "var(--color-maroon)", margin: 0, letterSpacing: "-0.02em" }}>{mine ? "Kho bài của tôi" : "Kho bài chung"}</h1>
          </div>
        </div>

        <button
          onClick={() => setShowProblemEditor(true)}
          style={{ display: "flex", alignItems: "center", gap: 7, height: 38, padding: "0 20px", background: "var(--color-maroon)", border: "none", borderRadius: 8, color: "#FAF7F2", fontSize: 13, fontWeight: 700, cursor: "pointer", fontFamily: "var(--font-sans)" }}
        >
          <svg width="13" height="13" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="6" y1="1" x2="6" y2="11" /><line x1="1" y1="6" x2="11" y2="6" /></svg>
          Add Problem
        </button>
      </div>

      <div style={{ display: "flex", gap: 12, marginBottom: 24, flexWrap: "wrap" }}>
        {[
          { label: "Total Problems", value: problems.length, color: "var(--color-maroon)" },
          { label: "Total Topics", value: topics.length, color: "var(--color-blue)" },
        ].map(({ label, value, color }) => (
          <div key={label} style={{ background: "var(--color-bg-panel)", border: "1px solid var(--color-border)", borderRadius: 9, padding: "12px 18px", display: "flex", flexDirection: "column", gap: 3, minWidth: 130 }}>
            <span style={{ fontFamily: "var(--font-mono)", fontSize: 9, color: "var(--color-text-muted)", textTransform: "uppercase", letterSpacing: "0.1em" }}>{label}</span>
            <span style={{ fontFamily: "var(--font-serif)", fontSize: 24, fontWeight: 700, color, lineHeight: 1 }}>{value}</span>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", gap: 0, borderBottom: "1px solid var(--color-border)", marginBottom: 16 }}>
        <div style={{ height: 38, padding: "0 20px", display: "flex", alignItems: "center", borderBottom: "2px solid var(--color-maroon)", color: "var(--color-maroon)", fontSize: 13, fontWeight: 600, fontFamily: "var(--font-sans)", textTransform: "capitalize" }}>
          problems ({problems.length})
        </div>
      </div>

      <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 10, overflow: "hidden" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          <label style={{ fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 600, color: "var(--color-text-muted)", textTransform: "uppercase", letterSpacing: "0.09em" }}>Chủ đề</label>
          <select value={topicFilter} onChange={(e) => setTopicFilter(e.target.value)} style={FILTER}>
            <option value="">Tất cả ({problems.length})</option>
            {topics.map((t) => <option key={t.id} value={t.id}>{t.order}. {t.name} ({problems.filter((p) => p.topic === t.id).length})</option>)}
            {ordered.some((p) => topicKey(p, known) === OTHER_TOPIC) && <option value={OTHER_TOPIC}>Bài khác</option>}
          </select>
        </div>

        <div style={{ flex: 1, overflow: "auto", border: "1px solid var(--color-border)", borderRadius: 10, background: "var(--color-bg-panel)" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                {["Mã", "Tên bài", "Chủ đề", "Độ khó", "Giới hạn", "Điểm", ""].map((h, i) => (
                  <th key={i} style={{ padding: "9px 14px", textAlign: i === 6 ? "right" : "left", fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 600, color: "var(--color-text-muted)", textTransform: "uppercase", letterSpacing: "0.09em", borderBottom: "1px solid var(--color-border)", background: "var(--color-bg-raised)", whiteSpace: "nowrap", position: "sticky", top: 0 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visibleProblems.length === 0
                ? <tr><td colSpan={7} style={{ padding: 52, textAlign: "center", color: "var(--color-text-muted)", fontFamily: "var(--font-mono)", fontSize: 13 }}>{problems.length === 0 ? "Chưa có bài tập nào. Bấm \"Add Problem\"." : "Chủ đề này chưa có bài nào."}</td></tr>
                : visibleProblems.map((p, idx) => {
                  const diff = DIFFICULTY_META[p.difficulty];
                  return (
                    <tr key={p.id} style={{ borderBottom: idx < visibleProblems.length - 1 ? "1px solid var(--color-border-subtle)" : "none" }}>
                      <td style={{ padding: "8px 14px" }}><span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--color-maroon)", fontWeight: 700 }}>{p.id}</span></td>
                      <td style={{ padding: "8px 14px" }}><span style={{ fontSize: 13, fontWeight: 500, color: "var(--color-text-primary)" }}>{p.title}</span></td>
                      <td style={{ padding: "8px 14px", whiteSpace: "nowrap" }}><span style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>{topicLabel(p)}</span></td>
                      <td style={{ padding: "8px 14px" }}>
                        {diff
                          ? <span style={{ fontSize: 11, fontWeight: 600, color: diff.color, background: diff.bg, borderRadius: 5, padding: "2px 8px", whiteSpace: "nowrap" }}>{diff.label}</span>
                          : <span style={{ fontSize: 12, color: "var(--color-text-muted)" }}>—</span>}
                      </td>
                      <td style={{ padding: "8px 14px", whiteSpace: "nowrap" }}><span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--color-text-secondary)" }}>{p.timeLimitMs} ms / {p.memoryLimitMb} MB</span></td>
                      <td style={{ padding: "8px 14px" }}><span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--color-text-secondary)" }}>{p.maxPoints.toFixed(0)}</span></td>
                      <td style={{ padding: "8px 14px" }}>
                        <div style={{ display: "flex", justifyContent: "flex-end", gap: 6 }}>
                          <button onClick={() => setTestProblem(p)} style={ROW_BTN}>Test</button>
                          <button onClick={() => setEditProblem(p)} style={ROW_BTN}>Sửa</button>
                          <button onClick={() => void removeProblem(p)} style={{ ...ROW_BTN, color: "#C62828", borderColor: "#FFCDD2" }}>Xoá</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
      </div>

      {showProblemEditor && (
        <ProblemEditor topics={topics} onClose={() => setShowProblemEditor(false)} onCreated={() => void onProblemsChanged()} />
      )}
      {testProblem && (
        <TestcaseManager problem={testProblem} onClose={() => { setTestProblem(null); void onProblemsChanged(); }} />
      )}
      {editProblem && (
        <ProblemMetaEditor problem={editProblem} topics={topics} onClose={() => setEditProblem(null)} onSaved={onProblemsChanged} />
      )}
    </div>
  );
}

const ROW_BTN: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 5, height: 28, padding: "0 11px", background: "none",
  border: "1px solid var(--color-border)", borderRadius: 6, color: "var(--color-text-muted)", fontSize: 11,
  cursor: "pointer", fontFamily: "var(--font-sans)",
};

const FILTER: React.CSSProperties = {
  height: 32, padding: "0 10px", background: "var(--color-bg-panel)", border: "1px solid var(--color-border)",
  borderRadius: 7, color: "var(--color-text-primary)", fontFamily: "var(--font-sans)", fontSize: 12.5, outline: "none",
};
