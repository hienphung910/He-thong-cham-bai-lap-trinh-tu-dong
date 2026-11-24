import { useEffect, useMemo, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Table, Tag, errorText, useLoad } from "../../shared/kit";
import Logo from "../../shared/Logo";
import ProblemDetail from "../judge/ProblemDetail";
import ContestProctor from "./Proctor";
import type { AuthUser, LanguageInfo, Problem } from "../../shared/types";
import type { Contest } from "../../shared/platformTypes";

/** Muc "Bang xep hang" trong danh sach ben trai. */
const STANDINGS = "__standings";

/**
 * Man hinh thi: giao dien DUY NHAT cua hoc sinh tu luc bam "Vao thi" cho toi khi nop bai som
 * hoac het gio. Chi co de cua ky thi, o nop bai, bang xep hang va dong ho - khong co menu, thong
 * bao, binh luan hay goi y sau khi nop. App.tsx dung man hinh nay thay cho toan bo giao dien binh
 * thuong khi may chu bao hoc sinh dang trong bai thi (contest.sitting).
 */
export default function ExamView({ contest, user, problems, languages, onChanged, onSubmitted, onLogout }: {
  contest: Contest;
  user: AuthUser;
  /** Moi bai nguoi dung mo duoc; de cua ky thi duoc tim trong day theo ma bai. */
  problems: Problem[];
  languages: LanguageInfo[];
  /** Trang thai bai thi vua doi (nop bai som, het gio): App.tsx tai lai de thoat man hinh thi. */
  onChanged: () => void;
  onSubmitted: () => void;
  onLogout: () => void;
}) {
  const examProblems = useMemo(() => {
    const byId = new Map(problems.map((p) => [p.id, p]));
    return contest.problems.map((ref) => byId.get(ref.id)).filter((p): p is Problem => p !== undefined);
  }, [contest.problems, problems]);

  const [selectedId, setSelectedId] = useState("");
  const selected = selectedId === STANDINGS ? null : examProblems.find((p) => p.id === selectedId) ?? examProblems[0] ?? null;
  const [error, setError] = useState("");
  const [finishing, setFinishing] = useState(false);

  // Bang xep hang va ket qua cua minh: tai lai sau moi lan nop va moi 20 giay.
  const detail = useLoad(() => papi.contest(contest.id), [contest.id]);
  const reloadDetail = detail.reload;
  useEffect(() => {
    const timer = setInterval(reloadDetail, 20_000);
    return () => clearInterval(timer);
  }, [reloadDetail]);
  const myRow = detail.data?.standings?.find((r) => r.username === user.username);
  const myCells = new Map((myRow?.cells ?? []).map((cell) => [cell.problemId, cell]));

  // Dong ho dem nguoc; het gio thi bao App.tsx tai lai de thoat man hinh thi.
  const [now, setNow] = useState(() => Date.now());
  const expired = useRef(false);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const left = Math.max(0, Math.floor((Date.parse(contest.endAt) - now) / 1000));
  useEffect(() => {
    if (left > 0 || expired.current) return;
    expired.current = true;
    onChanged();
  }, [left, onChanged]);
  const clock = `${String(Math.floor(left / 3600)).padStart(2, "0")}:${String(Math.floor((left % 3600) / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`;

  async function finishEarly() {
    if (!window.confirm("Nộp bài và kết thúc kỳ thi ngay bây giờ?\n\nSau khi kết thúc, bạn không vào lại kỳ thi này được và các bài nộp thêm sẽ không được tính.")) return;
    setFinishing(true);
    setError("");
    try {
      await papi.finishContest(contest.id);
      onChanged();
    } catch (e) {
      setError(errorText(e));
      setFinishing(false);
    }
  }

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", background: "var(--color-page-bg)" }}>
      {/* ═══ THANH TREN CUNG CUA BAI THI ═══ */}
      <header style={{ display: "flex", alignItems: "center", gap: 14, height: 48, padding: "0 20px", background: "var(--color-header)", flexShrink: 0 }}>
        <Logo size={30} />
        <span style={{ color: "#fff", fontSize: 14, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", minWidth: 0 }}>
          Kỳ thi: {contest.title}
        </span>
        <span style={{ flex: 1 }} />
        <span title="Thời gian còn lại" style={{
          fontFamily: "var(--font-mono)", fontSize: 15, fontWeight: 700, padding: "3px 10px", borderRadius: 6,
          color: left <= 300 ? "#fff" : "var(--color-accent)", background: left <= 300 ? "#C62828" : "rgba(255,255,255,0.08)",
        }}>Còn {clock}</span>
        <span style={{ color: "rgba(255,255,255,0.85)", fontSize: 13, whiteSpace: "nowrap" }}>{user.fullName || user.username}</span>
        <button onClick={() => void finishEarly()} disabled={finishing} style={{
          height: 32, padding: "0 14px", borderRadius: 6, border: "none", cursor: finishing ? "not-allowed" : "pointer",
          background: "var(--color-accent)", color: "var(--color-on-accent)", fontSize: 13, fontWeight: 700, whiteSpace: "nowrap",
          opacity: finishing ? 0.6 : 1,
        }}>{finishing ? "Đang nộp..." : "Nộp bài sớm"}</button>
        <button onClick={onLogout} style={{
          height: 32, padding: "0 10px", borderRadius: 6, border: "1px solid rgba(255,255,255,0.3)", cursor: "pointer",
          background: "transparent", color: "rgba(255,255,255,0.85)", fontSize: 12.5, whiteSpace: "nowrap",
        }}>Đăng xuất</button>
      </header>

      {contest.proctored && <ContestProctor contest={contest} problemId={selected?.id ?? null} />}

      {error && (
        <div style={{ padding: "8px 20px", background: "#FFEBEE", borderBottom: "1px solid #FFCDD2", fontSize: 13, color: "#C62828", flexShrink: 0 }}>{error}</div>
      )}

      <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>
        {/* ═══ DANH SACH BAI CUA KY THI ═══ */}
        <aside style={{ width: 270, flexShrink: 0, background: "#fff", borderRight: "1px solid #E0E0E0", display: "flex", flexDirection: "column", overflow: "hidden" }}>
          <div style={{ padding: "14px 16px 10px", borderBottom: "1px solid #F0F0F0" }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: "#212121" }}>Đề thi ({examProblems.length} bài)</div>
            <div style={{ fontSize: 12, color: "#757575", marginTop: 2 }}>
              Đã giải <b style={{ color: "var(--color-green)" }}>{myRow?.solved ?? 0}</b>/{examProblems.length} bài
            </div>
          </div>
          <nav style={{ flex: 1, overflowY: "auto", padding: "6px 0" }}>
            {examProblems.map((p, index) => {
              const cell = myCells.get(p.id);
              const solved = cell !== undefined && cell.solvedAt !== null;
              const active = selected?.id === p.id;
              return (
                <button key={p.id} onClick={() => setSelectedId(p.id)} style={{
                  width: "100%", display: "flex", alignItems: "center", gap: 10, padding: "9px 16px 9px 13px", border: "none",
                  cursor: "pointer", textAlign: "left",
                  background: active ? "var(--color-red-light)" : "transparent",
                  borderLeft: `3px solid ${active ? "var(--color-accent)" : "transparent"}`,
                }}>
                  <span style={{
                    width: 26, height: 26, borderRadius: "50%", flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center",
                    fontSize: 12, fontWeight: 700,
                    background: solved ? "var(--color-green)" : "#fff", color: solved ? "#fff" : "#757575",
                    border: `2px solid ${solved ? "var(--color-green)" : cell && cell.wrong > 0 ? "#E65100" : "#E0E0E0"}`,
                  }}>{solved ? "✓" : String.fromCharCode(65 + index)}</span>
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <span style={{ display: "block", fontSize: 13, fontWeight: active ? 700 : 500, color: "#212121", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.title}</span>
                    <span style={{ fontSize: 11, color: "#9E9E9E", fontFamily: "var(--font-mono)" }}>
                      {p.id}{solved ? " · đã giải" : cell && cell.wrong > 0 ? " · chưa đúng" : ""}
                    </span>
                  </span>
                </button>
              );
            })}
            {examProblems.length === 0 && <div style={{ padding: "12px 16px", fontSize: 13, color: "#9E9E9E" }}>Đang tải đề thi...</div>}
          </nav>
          <button onClick={() => setSelectedId(STANDINGS)} style={{
            margin: 12, height: 36, borderRadius: 6, cursor: "pointer", fontSize: 13, fontWeight: 600,
            border: `1px solid ${selectedId === STANDINGS ? "var(--color-accent)" : "#E0E0E0"}`,
            background: selectedId === STANDINGS ? "var(--color-red-light)" : "#fff", color: "#424242",
          }}>Bảng xếp hạng</button>
        </aside>

        {/* ═══ BAI DANG LAM HOAC BANG XEP HANG ═══ */}
        {selectedId === STANDINGS || !selected ? (
          <div style={{ flex: 1, overflow: "auto", padding: "20px 24px" }}>
            <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "18px 20px" }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#212121", marginBottom: 12 }}>Bảng xếp hạng</div>
              <Table head={["#", "Thí sinh", "Số bài", ...contest.problems.map((p) => p.id)]}
                empty="Chưa có ai nộp bài."
                rows={(detail.data?.standings ?? []).map((r) => [
                  r.rank,
                  <span><b>{r.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{r.username}</span>{r.username === user.username && <Tag color="blue">Bạn</Tag>}</span>,
                  <b>{r.solved}</b>,
                  ...r.cells.map((cell) => cell.solvedAt !== null
                    ? <Tag color="green">✓ Đã giải</Tag>
                    : cell.wrong > 0 ? <Tag color="red">Chưa đúng</Tag> : "·"),
                ])} />
            </div>
          </div>
        ) : (
          <ProblemDetail
            key={selected.id}
            exam
            problem={selected}
            languages={languages}
            onBack={() => undefined}
            onOpen={() => undefined}
            onSubmitted={() => { onSubmitted(); reloadDetail(); }}
          />
        )}
      </div>
    </div>
  );
}
