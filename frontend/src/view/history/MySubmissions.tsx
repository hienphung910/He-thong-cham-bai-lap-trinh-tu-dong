import { useEffect, useState } from "react";
import { api } from "../../shared/api";
import { useAuth } from "../account/auth";
import type { Submission } from "../../shared/types";
import { SubmissionDetail, TD, TH, VBadge, fmtTime, rowStyle, stripe } from "./Submissions";

/** Trang "Bài nộp": lịch sử nộp bài của chính người đang đăng nhập. */
export default function MySubmissions() {
  const { user } = useAuth();
  const [subs, setSubs] = useState<Submission[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<Submission | null>(null);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    api.submissions()
      .then((all) => setSubs(all.filter((s) => s.author === user.username)))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [user]);

  async function openDetail(id: string) {
    try { setDetail(await api.submission(id)); } catch {}
  }

  const accepted = subs.filter((s) => s.verdict === "AC").length;

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
      {/* Header */}
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "16px 24px", background: "#fff", borderBottom: "1px solid #E0E0E0", flexShrink: 0,
      }}>
        <div style={{ fontSize: 16, fontWeight: 700, color: "#212121" }}>Bài nộp của tôi</div>
        <div style={{ display: "flex", gap: 16, fontSize: 13, color: "#757575" }}>
          <span>Tổng: <b style={{ color: "#212121" }}>{subs.length}</b></span>
          <span>AC: <b style={{ color: "var(--color-green)" }}>{accepted}</b></span>
        </div>
      </div>

      {/* Table */}
      <div style={{ flex: 1, overflow: "auto", margin: "0 24px 16px" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 700 }}>
          <thead>
            <tr style={{ background: "var(--color-table-header-bg)" }}>
              <th style={TH}>Thời gian</th>
              <th style={{ ...TH, textAlign: "left" }}>Bài tập</th>
              <th style={TH}>Ngôn ngữ</th>
              <th style={TH}>Kết quả</th>
              <th style={TH}>Điểm</th>
              <th style={TH}>Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {subs.length === 0 ? (
              <tr><td colSpan={6} style={{ padding: 40, textAlign: "center", color: "#9E9E9E", fontSize: 13 }}>
                {loading ? "Đang tải..." : "Bạn chưa nộp bài nào."}
              </td></tr>
            ) : subs.map((s, idx) => (
              <tr
                key={s.id}
                onClick={() => void openDetail(s.id)}
                style={rowStyle(idx, detail?.id === s.id)}
                onMouseEnter={(e) => { if (detail?.id !== s.id) e.currentTarget.style.background = "var(--color-table-hover)"; }}
                onMouseLeave={(e) => { if (detail?.id !== s.id) e.currentTarget.style.background = stripe(idx); }}
              >
                <td style={{ ...TD, fontFamily: "var(--font-mono)", fontSize: 12, color: "#757575", whiteSpace: "nowrap" }}>{fmtTime(s.submittedAt)}</td>
                <td style={{ ...TD, textAlign: "left" }}>
                  <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, fontWeight: 600, color: "var(--color-red)" }}>{s.problem.id}</span>
                  {" · "}
                  <span style={{ color: s.verdict === "AC" ? "var(--color-green)" : "#212121", fontWeight: 500 }}>{s.problem.title}</span>
                </td>
                <td style={{ ...TD, textAlign: "center", fontSize: 13 }}>{s.language}</td>
                <td style={{ ...TD, textAlign: "center" }}><VBadge verdict={s.verdict} /></td>
                <td style={{ ...TD, textAlign: "center", fontFamily: "var(--font-mono)", fontSize: 13, fontWeight: 600, color: s.verdict === "AC" ? "var(--color-green)" : "#616161" }}>
                  {(s.score ?? 0).toFixed(0)}/{s.maxPoints.toFixed(0)}
                </td>
                <td style={{ ...TD, fontFamily: "var(--font-mono)", fontSize: 12, color: "#757575", textAlign: "center" }}>
                  {s.execTimeMs !== null ? `${(s.execTimeMs / 1000).toFixed(2)}s` : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {detail && <SubmissionDetail detail={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}
