import { useCallback, useEffect, useState } from "react";
import { api } from "../../shared/api";
import { papi } from "../../shared/platformApi";
import type { Classroom } from "../../shared/platformTypes";
import type { Problem, ScoreRow } from "../../shared/types";

/** Bang dang xem duoc nho trong trinh duyet: "" = lo trinh chung, con lai la ma lop. */
const SCOPE_KEY = "oj.leaderboard.scope";

function readScope(): string {
  try { return localStorage.getItem(SCOPE_KEY) ?? ""; } catch { return ""; }
}

function saveScope(scope: string) {
  try { localStorage.setItem(SCOPE_KEY, scope); } catch { /* trinh duyet chan bo nho trang: bo qua */ }
}

/**
 * Bang xep hang: chon "Lo trinh chung" (moi nguoi, bai kho chung) hoac mot lop minh thuoc ve (hoc sinh
 * cua lop, tren bai luyen tap va bai tap giao cua lop).
 */
export default function Leaderboard({ reloadKey, problems }: { reloadKey: number; problems: Problem[] }) {
  const [rows, setRows] = useState<ScoreRow[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [classes, setClasses] = useState<Classroom[]>([]);
  const [scope, setScope] = useState<string>(readScope);
  /** So bai tinh diem cua bang dang xem (lop); null = lo trinh chung. */
  const [classProblemCount, setClassProblemCount] = useState<number | null>(null);

  useEffect(() => {
    papi.classrooms().then(setClasses).catch(() => setClasses([]));
  }, []);

  // Lop da nho khong con (bi xoa, roi lop): quay ve lo trinh chung.
  const activeScope = scope && classes.some((c) => c.id === scope) ? scope : "";

  const load = useCallback(async () => {
    setLoading(true);
    try {
      if (activeScope) {
        const board = await papi.classScoreboard(activeScope);
        setRows(board.rows);
        setClassProblemCount(board.problemCount);
      } else {
        setRows(await api.scoreboard());
        setClassProblemCount(null);
      }
      setError("");
    }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }, [activeScope]);

  useEffect(() => { void load(); }, [load, reloadKey]);

  function changeScope(next: string) {
    setScope(next);
    saveScope(next);
  }

  const top3 = rows.slice(0, 3);
  // Reorder for podium display: [#2, #1, #3]
  const podiumOrder = top3.length >= 3 ? [top3[1], top3[0], top3[2]] : top3;
  const PODIUM_COLORS = [
    { border: "#CE93D8", bg: "#F3E5F5", badge: "#9C27B0", medal: "🥈", height: 110 }, // #2
    { border: "#FFD54F", bg: "#FFFDE7", badge: "#F9A825", medal: "🥇", height: 140 }, // #1
    { border: "#FFAB91", bg: "#FBE9E7", badge: "#E65100", medal: "🥉", height: 95 },  // #3
  ];

  return (
    <div aria-busy={loading} style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "auto" }}>
      {/* Header */}
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
        padding: "16px 24px", background: "#fff", borderBottom: "1px solid #E0E0E0", flexShrink: 0,
      }}>
        <div style={{ fontSize: 16, fontWeight: 700, color: "#212121", whiteSpace: "nowrap" }}>Bảng xếp hạng</div>
        {classes.length > 0 && (
          <select aria-label="Chọn bảng xếp hạng" value={activeScope} onChange={(e) => changeScope(e.target.value)} style={{
            height: 34, width: 260, minWidth: 0, maxWidth: "100%", textOverflow: "ellipsis", padding: "0 34px 0 12px", fontSize: 13, fontWeight: 600, color: "#212121",
            border: "1px solid #E0E0E0", borderRadius: 8, outline: "none", cursor: "pointer", appearance: "none",
            background: "#fff url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12' fill='none' stroke='%23757575' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M3 4.5 6 7.5 9 4.5'/%3E%3C/svg%3E\") no-repeat right 12px center",
          }}>
            <option value="">Lộ trình chung</option>
            <optgroup label="Lớp của tôi">
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </optgroup>
          </select>
        )}
      </div>

      <div style={{ fontSize: 13, color: "#9E9E9E", padding: "12px 24px 0" }}>
        {classProblemCount === null ? `Tổng ${problems.length} bài luyện tập` : `Tổng ${classProblemCount} bài của lớp`}
      </div>

      {error && (
        <div style={{ margin: "12px 24px", padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 13, color: "#C62828" }}>{error}</div>
      )}

      {/* ═══ PODIUM TOP 3 ═══ */}
      {top3.length >= 3 && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "flex-end", gap: 24, padding: "32px 24px 24px" }}>
          {podiumOrder.map((r, i) => {
            const cfg = PODIUM_COLORS[i];
            return (
              <div key={r.username} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
                {/* Medal */}
                <div style={{ fontSize: 20 }}>{cfg.medal}</div>
                {/* Avatar circle */}
                <div style={{
                  width: i === 1 ? 72 : 60, height: i === 1 ? 72 : 60, borderRadius: "50%",
                  background: "#F5F5F5", border: `3px solid ${cfg.border}`,
                  display: "flex", alignItems: "center", justifyContent: "center",
                  color: "#9E9E9E", position: "relative",
                }}>
                  <svg width="28" height="28" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.2">
                    <circle cx="8" cy="5" r="3" /><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" />
                  </svg>
                  {/* Rank badge */}
                  <div style={{
                    position: "absolute", top: -4, right: -4,
                    width: 20, height: 20, borderRadius: "50%", background: cfg.badge,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    fontSize: 10, fontWeight: 700, color: "#fff",
                  }}>{r.rank}</div>
                </div>
                {/* Name */}
                <div style={{ fontSize: 14, fontWeight: 700, color: "#212121", textAlign: "center" }}>{r.fullName || r.username}</div>
                <div style={{ fontSize: 11, color: "#9E9E9E", fontFamily: "var(--font-mono)" }}>{r.username}</div>
                {/* Score card */}
                <div style={{
                  width: i === 1 ? 140 : 120, height: cfg.height,
                  background: cfg.bg, border: `2px solid ${cfg.border}`, borderRadius: 10,
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                }}>
                  <div style={{ fontSize: i === 1 ? 36 : 30, fontWeight: 800, color: "#212121" }}>{r.solvedCount}</div>
                  <div style={{ fontSize: 12, color: "#757575" }}>bài - {Math.round(r.total)} điểm</div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: cfg.badge }}>#{r.rank}</div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ═══ RANKING TABLE ═══ */}
      <div style={{ margin: "0 24px 16px", background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8 }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ background: "var(--color-table-header-bg)" }}>
              <th style={TH}>Hạng</th>
              <th style={{ ...TH, textAlign: "left" }}>Sinh viên</th>
              <th style={TH}>Mã SV</th>
              <th style={TH}>Đã giải</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, idx) => (
              <tr key={r.username} style={{
                background: idx % 2 === 1 ? "var(--color-table-row-alt)" : "#fff",
                borderBottom: "1px solid #F5F5F5",
              }}>
                <td style={{ ...TD, textAlign: "center", fontWeight: 700, color: r.rank <= 3 ? "var(--color-red)" : "#757575" }}>{r.rank}</td>
                <td style={{ ...TD, textAlign: "left" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{
                      width: 28, height: 28, borderRadius: "50%",
                      background: r.rank <= 3 ? "#FFCDD2" : "#F5F5F5",
                      display: "flex", alignItems: "center", justifyContent: "center",
                    }}>
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke={r.rank <= 3 ? "#9A5B00" : "#9E9E9E"} strokeWidth="1.2">
                        <circle cx="8" cy="5" r="3" /><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" />
                      </svg>
                    </div>
                    <span style={{ fontSize: 13, fontWeight: 500, color: "#212121" }}>{r.fullName || r.username}</span>
                  </div>
                </td>
                <td style={{ ...TD, textAlign: "center", fontFamily: "var(--font-mono)", fontSize: 12, color: "#616161", fontWeight: 600 }}>{r.username}</td>
                <td style={{ ...TD, textAlign: "center" }}>
                  <span style={{ fontFamily: "var(--font-mono)", fontSize: 13, fontWeight: 700, color: "var(--color-red)" }}>{r.solvedCount}</span>
                  {/* Thu hang xep theo tong diem (tinh ca diem tung phan), nen hien kem diem de thu tu khong kho hieu. */}
                  <span style={{ marginLeft: 6, fontSize: 12, color: "#757575" }}>- {Math.round(r.total)} điểm</span>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={4} style={{ padding: 40, textAlign: "center", color: "#9E9E9E", fontSize: 13 }}>Chưa có dữ liệu.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const TH: React.CSSProperties = {
  padding: "10px 14px", fontSize: 12, fontWeight: 700, color: "#757575",
  textAlign: "center", whiteSpace: "nowrap", borderBottom: "1px solid var(--color-table-border)",
  background: "var(--color-table-header-bg)",
};

const TD: React.CSSProperties = {
  padding: "10px 14px", fontSize: 13, verticalAlign: "middle",
};
