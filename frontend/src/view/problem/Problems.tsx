import { useEffect, useMemo, useState } from "react";
import { api } from "../../shared/api";
import { papi } from "../../shared/platformApi";
import { errorText } from "../../shared/kit";
import { useAuth } from "../account/auth";
import { DIFFICULTY_META, OTHER_TOPIC, orderByPath, topicKey } from "./learningPath";
import type { LanguageInfo, Problem, Submission, Topic } from "../../shared/types";
import type { Classroom } from "../../shared/platformTypes";
import ProblemDetail from "../judge/ProblemDetail";

type Status = "solved" | "attempted" | "unsolved";
interface Progress { total: number; solved: number }

/** Nguon bai dang xem duoc nho lai trong tab, de roi trang roi quay lai van o dung lop do. */
const SOURCE_KEY = "oj-problem-source";
const TRACK = "track:";
const CLASS = "class:";

/**
 * Trang danh sach bai. O chon o goc tren ben phai quyet dinh dang xem bai cua dau:
 *   - mot LO TRINH chung (cot thu 4 trong topics.txt: Nhap mon, Lo trinh nang cao...), hoac
 *   - mot LOP RIENG ma nguoi dung thuoc ve: hien cac bai luyen tap giang vien them cho lop do.
 * Ben duoi la o tim kiem, o loc theo chu de va do kho, roi bang bai.
 */
export default function Problems({
  problems,
  allProblems,
  topics,
  languages,
  onSubmitted,
  reloadKey,
  selectedId,
  onSelect,
}: {
  /** Bai cua kho chung - chi nhung bai nay hien trong lo trinh. */
  problems: Problem[];
  /** Moi bai nguoi dung mo duoc, gom ca bai kho rieng nam trong ky thi / bai tap giao / bai luyen tap cua lop. */
  allProblems: Problem[];
  topics: Topic[];
  languages: LanguageInfo[];
  onSubmitted: () => void;
  reloadKey: number;
  /** Ma bai dang mo; null = hien danh sach. App.tsx giu gia tri nay va ghi no vao lich su trinh duyet. */
  selectedId: string | null;
  /** Mo mot bai (ma bai) hoac quay ve danh sach (null). */
  onSelect: (problemId: string | null) => void;
}) {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const [search, setSearch] = useState("");
  /** "" = tat ca, ma chu de, hoac OTHER_TOPIC. */
  const [topicFilter, setTopicFilter] = useState("");
  /** "track:<ten lo trinh>" hoac "class:<ma lop>"; "" = lo trinh dau tien. */
  const [source, setSource] = useState(() => {
    try { return sessionStorage.getItem(SOURCE_KEY) ?? ""; } catch { return ""; }
  });
  /** 0 = moi do kho. */
  const [diffFilter, setDiffFilter] = useState(0);
  const [hov, setHov] = useState<string | null>(null);
  const setSelectedId = onSelect;
  const [mySubs, setMySubs] = useState<Submission[]>([]);

  function pickSource(next: string) {
    setSource(next);
    setTopicFilter("");
    try { sessionStorage.setItem(SOURCE_KEY, next); } catch { /* khong luu duoc thi chi mat viec nho lai */ }
  }

  useEffect(() => {
    let alive = true;
    if (!user) return;
    api.submissions()
      .then((subs) => { if (alive) setMySubs(subs.filter((s) => s.author === user.username)); })
      .catch(() => undefined);
    return () => { alive = false; };
  }, [user, reloadKey]);

  // Cac lop rieng cua nguoi dung. Quan tri vien khong tham gia lop nen khong tai.
  const [classes, setClasses] = useState<Classroom[]>([]);
  useEffect(() => {
    if (!user || isAdmin) return;
    let alive = true;
    papi.classrooms().then((list) => { if (alive) setClasses(list); }).catch(() => undefined);
    return () => { alive = false; };
  }, [user, isAdmin]);

  const statusByProblem = useMemo(() => {
    const map = new Map<string, Status>();
    for (const s of mySubs) {
      const cur = map.get(s.problem.id);
      if (s.verdict === "AC") map.set(s.problem.id, "solved");
      else if (cur !== "solved") map.set(s.problem.id, "attempted");
    }
    return map;
  }, [mySubs]);

  const known = useMemo(() => new Set(topics.map((t) => t.id)), [topics]);
  const topicName = useMemo(() => new Map(topics.map((t) => [t.id, t.name])), [topics]);
  // Moi lo trinh (cot thu 4 trong topics.txt) co danh sach chang rieng, lo trinh xuat hien truoc dung truoc.
  const groups = useMemo(() => [...new Set(topics.map((t) => t.group))], [topics]);
  const firstGroup = groups[0] ?? "";

  const activeClass = source.startsWith(CLASS) ? classes.find((c) => c.id === source.slice(CLASS.length)) ?? null : null;
  const trackPick = source.startsWith(TRACK) ? source.slice(TRACK.length) : "";
  const group = groups.includes(trackPick) ? trackPick : firstGroup;
  const groupTopics = useMemo(() => topics.filter((t) => t.group === group), [topics, group]);

  // Bai luyen tap cua lop dang chon (theo thu tu giang vien them). null = dang tai.
  const activeClassId = activeClass?.id ?? "";
  const [practiceIds, setPracticeIds] = useState<string[] | null>(null);
  const [classError, setClassError] = useState("");
  useEffect(() => {
    setClassError("");
    if (!activeClassId) { setPracticeIds(null); return; }
    let alive = true;
    setPracticeIds(null);
    papi.classroom(activeClassId)
      .then((detail) => { if (alive) setPracticeIds((detail.practice ?? []).map((p) => p.id)); })
      .catch((e) => { if (alive) { setPracticeIds([]); setClassError(errorText(e)); } });
    return () => { alive = false; };
  }, [activeClassId, reloadKey]);

  const ordered = useMemo(() => {
    if (activeClassId) {
      // Lop rieng: dung thu tu giang vien da them; tim trong allProblems de co ca bai kho rieng.
      const byId = new Map(allProblems.map((p) => [p.id, p]));
      return (practiceIds ?? []).map((id) => byId.get(id)).filter((p): p is Problem => p !== undefined);
    }
    // Lo trinh: chi lay bai cua lo trinh dang xem; bai chua phan loai ("Bai khac") xep vao lo trinh dau tien.
    const inGroup = new Set(groupTopics.map((t) => t.id));
    const mine = problems.filter((p) => (known.has(p.topic) ? inGroup.has(p.topic) : group === firstGroup));
    return orderByPath(mine, groupTopics);
  }, [activeClassId, practiceIds, allProblems, problems, known, groupTopics, group, firstGroup]);

  const progress = useMemo(() => {
    const map = new Map<string, Progress>();
    for (const p of ordered) {
      const key = topicKey(p, known);
      const cur = map.get(key) ?? { total: 0, solved: 0 };
      cur.total++;
      if (statusByProblem.get(p.id) === "solved") cur.solved++;
      map.set(key, cur);
    }
    return map;
  }, [ordered, known, statusByProblem]);

  /** Cac chu de co bai trong danh sach dang xem, de dua vao o loc: lo trinh thi theo thu tu chang. */
  const topicOptions = useMemo(() => {
    const keys = activeClassId
      ? [...new Set(ordered.map((p) => topicKey(p, known)))]
      : [...groupTopics.map((t) => t.id), OTHER_TOPIC];
    return keys.filter((key) => progress.has(key)).map((key) => ({
      key,
      name: key === OTHER_TOPIC ? "Bài khác" : topicName.get(key) ?? key,
      ...progress.get(key)!,
    }));
  }, [activeClassId, ordered, known, groupTopics, progress, topicName]);
  // Doi nguon bai thi chu de dang loc co the khong con: coi nhu khong loc.
  const activeTopic = topicOptions.some((t) => t.key === topicFilter) ? topicFilter : "";

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return ordered.filter((p) =>
      (!activeTopic || topicKey(p, known) === activeTopic)
      && (!diffFilter || p.difficulty === diffFilter)
      && (!q || p.title.toLowerCase().includes(q) || p.id.toLowerCase().includes(q)),
    );
  }, [ordered, known, activeTopic, diffFilter, search]);

  const solvedAll = ordered.filter((p) => statusByProblem.get(p.id) === "solved").length;
  const solvedInView = filtered.filter((p) => statusByProblem.get(p.id) === "solved").length;
  // Bai dau tien chua giai theo dung thu tu -> goi y "hoc tiep".
  const nextToLearn = ordered.find((p) => statusByProblem.get(p.id) !== "solved") ?? null;
  const nameOf = (p: Problem) => topicName.get(p.topic) ?? "Bài khác";

  // Tim trong allProblems de mo duoc ca bai kho rieng (bam tu ky thi, bai tap giao).
  const selected = selectedId ? allProblems.find((p) => p.id === selectedId) ?? null : null;
  if (selected) {
    // "Bai tiep theo": trong danh sach dang loc neu bai nam trong do, khong thi theo danh sach chung.
    const list = filtered.some((p) => p.id === selected.id) ? filtered : ordered;
    // Bai mo tu noi khac (ky thi...) khong nam trong danh sach nen khong co "bai tiep theo".
    const index = list.findIndex((p) => p.id === selected.id);
    const next = index >= 0 ? list[index + 1] ?? null : null;
    return (
      <ProblemDetail
        key={selected.id}
        problem={selected}
        languages={languages}
        topicName={nameOf(selected)}
        nextProblem={next}
        onNext={() => next && setSelectedId(next.id)}
        onBack={() => setSelectedId(null)}
        onSubmitted={onSubmitted}
      />
    );
  }

  const loadingClass = activeClassId !== "" && practiceIds === null;
  const emptyText = loadingClass ? "Đang tải bài của lớp..."
    : activeClass && ordered.length === 0 ? "Giảng viên chưa thêm bài luyện tập nào cho lớp này."
    : ordered.length === 0 ? "Chưa có bài tập nào."
    : "Không tìm thấy bài phù hợp.";

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", padding: "16px 24px 12px" }}>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8 }}>

        {/* ═══ TIEU DE + O CHON LO TRINH / LOP ═══ */}
        <div style={{ padding: "14px 20px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap", borderBottom: "1px solid #EEEEEE", flexShrink: 0 }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 17, fontWeight: 700, color: "#212121" }}>Lớp học của tôi</div>
            <div style={{ fontSize: 12, color: "#757575", marginTop: 2 }}>
              {activeClass ? `Bài luyện tập của ${activeClass.name} · Giảng viên ${activeClass.teacherName}` : `Lộ trình ${group}`}
            </div>
          </div>
          <select
            value={activeClass ? CLASS + activeClass.id : TRACK + group}
            onChange={(e) => pickSource(e.target.value)}
            title="Chọn lộ trình hoặc lớp để xem bài"
            style={{
              height: 38, minWidth: 320, maxWidth: "100%", padding: "0 12px", border: "1px solid #D0D0D0", borderRadius: 6,
              fontSize: 13.5, color: "#212121", background: "#fff", cursor: "pointer", outline: "none",
            }}
          >
            <optgroup label="Lộ trình chung">
              {groups.map((g) => <option key={g} value={TRACK + g}>{g}</option>)}
            </optgroup>
            {classes.length > 0 && (
              <optgroup label="Lớp của tôi">
                {classes.map((c) => (
                  <option key={c.id} value={CLASS + c.id}>{c.id} · {c.name} — GV {c.teacherName}</option>
                ))}
              </optgroup>
            )}
          </select>
        </div>

        {/* ═══ TIM KIEM + CHU DE + DO KHO + TIEN DO ═══ */}
        <div style={{ padding: "12px 20px", flexShrink: 0, display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div style={{ position: "relative", width: 260 }}>
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1.5" style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)" }}>
              <circle cx="6.5" cy="6.5" r="4.5" /><line x1="10" y1="10" x2="14" y2="14" />
            </svg>
            <input
              type="text" placeholder="Tìm mã / tên bài" value={search} onChange={(e) => setSearch(e.target.value)}
              style={{
                width: "100%", height: 34, paddingLeft: 32, paddingRight: 10,
                background: "#fff", border: "1px solid #E0E0E0", borderRadius: 6,
                fontSize: 13, color: "#212121", outline: "none", boxSizing: "border-box",
              }}
              onFocus={(e) => { e.target.style.borderColor = "#F7931E"; }}
              onBlur={(e) => { e.target.style.borderColor = "#E0E0E0"; }}
            />
          </div>
          <select
            value={activeTopic} onChange={(e) => setTopicFilter(e.target.value)} title="Lọc theo chủ đề"
            style={{
              height: 34, maxWidth: 320, padding: "0 10px", border: "1px solid #E0E0E0", borderRadius: 6,
              fontSize: 13, color: "#212121", background: "#fff", cursor: "pointer", outline: "none",
            }}
          >
            <option value="">Mọi chủ đề ({solvedAll}/{ordered.length})</option>
            {topicOptions.map((t, i) => (
              <option key={t.key} value={t.key}>
                {activeClass || t.key === OTHER_TOPIC ? "" : `${i + 1}. `}{t.name} ({t.solved}/{t.total})
              </option>
            ))}
          </select>
          <div style={{ display: "flex", gap: 6 }}>
            {[0, 1, 2, 3].map((d) => {
              const on = diffFilter === d;
              const meta = DIFFICULTY_META[d];
              return (
                <button key={d} onClick={() => setDiffFilter(d)} style={{
                  height: 30, padding: "0 12px", borderRadius: 15, cursor: "pointer", fontSize: 12, fontWeight: on ? 700 : 500,
                  border: `1px solid ${on ? (meta?.color ?? "var(--color-red)") : "#E0E0E0"}`,
                  background: on ? (meta?.bg ?? "var(--color-red-light)") : "#fff",
                  color: on ? (meta?.color ?? "var(--color-red)") : "#616161",
                }}>
                  {meta?.label ?? "Mọi mức"}
                </button>
              );
            })}
          </div>
          <div style={{ flex: 1 }} />
          {nextToLearn && (
            <button
              onClick={() => setSelectedId(nextToLearn.id)}
              title={`${nextToLearn.id} — ${nextToLearn.title}`}
              style={{
                height: 34, padding: "0 14px", cursor: "pointer", borderRadius: 6, fontSize: 12.5, fontWeight: 700,
                background: "var(--color-accent)", color: "var(--color-on-accent)", border: "1px solid var(--color-accent-dark)",
                whiteSpace: "nowrap", maxWidth: 300, overflow: "hidden", textOverflow: "ellipsis",
              }}
            >
              {solvedAll === 0 ? "Bắt đầu học" : "Học tiếp"}: {nextToLearn.id} →
            </button>
          )}
        </div>

        {classError && (
          <div style={{ margin: "0 20px 10px", padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 13, color: "#C62828" }}>
            {classError}
          </div>
        )}

        {/* ═══ BANG BAI ═══ */}
        <div style={{ flex: 1, overflow: "auto", borderTop: "1px solid #EEEEEE" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "var(--color-table-header-bg)" }}>
                <th style={{ ...TH_S, width: 52 }}>TT</th>
                <th style={{ ...TH_S, width: 36 }} />
                <th style={{ ...TH_S, textAlign: "left" }}>Mã</th>
                <th style={{ ...TH_S, textAlign: "left", width: "auto" }}>Tên đề</th>
                <th style={{ ...TH_S, textAlign: "left" }}>Chủ đề</th>
                <th style={TH_S}>Độ khó</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr><td colSpan={6} style={{ padding: 40, textAlign: "center", color: "#9E9E9E", fontSize: 13 }}>{emptyText}</td></tr>
              ) : filtered.map((p, idx) => {
                const isHov = hov === p.id;
                const status = statusByProblem.get(p.id) ?? "unsolved";
                const solved = status === "solved";
                const rowBg = isHov ? "var(--color-table-hover)" : solved ? "#F1F8E9" : "#fff";
                const diff = DIFFICULTY_META[p.difficulty];
                return (
                  <tr
                    key={p.id}
                    onMouseEnter={() => setHov(p.id)} onMouseLeave={() => setHov(null)}
                    onClick={() => setSelectedId(p.id)}
                    style={{ background: rowBg, cursor: "pointer", borderBottom: "1px solid #EEEEEE", transition: "background 0.1s" }}
                  >
                    <td style={{ ...TD_S, textAlign: "center", color: "#757575" }}>{idx + 1}</td>
                    <td style={{ ...TD_S, textAlign: "center", padding: "10px 0" }} title={solved ? "Đã giải" : status === "attempted" ? "Đang làm" : ""}>
                      {solved && <span style={{ color: "var(--color-green)", fontWeight: 800 }}>✓</span>}
                      {status === "attempted" && <span style={{ color: "#E65100", fontWeight: 800 }}>•</span>}
                    </td>
                    <td style={{ ...TD_S, width: 110 }}>
                      <span style={{ fontFamily: "var(--font-mono)", fontSize: 12.5, fontWeight: 600, color: "var(--color-red)" }}>{p.id}</span>
                    </td>
                    <td style={TD_S}>
                      <span style={{ fontSize: 13.5, fontWeight: 500, color: isHov ? "var(--color-red)" : "#212121" }}>{p.title}</span>
                      {status === "attempted" && <span style={{ marginLeft: 8, fontSize: 11, color: "#E65100" }}>đang làm</span>}
                    </td>
                    <td style={{ ...TD_S, color: "#616161", fontSize: 12.5, whiteSpace: "nowrap" }}>{nameOf(p)}</td>
                    <td style={{ ...TD_S, textAlign: "center", width: 90 }}>
                      {diff ? (
                        <span style={{ fontSize: 11.5, fontWeight: 700, padding: "2px 10px", borderRadius: 10, color: diff.color, background: diff.bg }}>
                          {diff.label}
                        </span>
                      ) : <span style={{ color: "#BDBDBD" }}>—</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* ═══ TIEN DO CUA DANH SACH DANG XEM ═══ */}
        <div style={{ padding: "10px 20px", flexShrink: 0, display: "flex", alignItems: "center", gap: 12, borderTop: "1px solid #EEEEEE" }}>
          <div style={{ flex: 1 }}><Bar value={solvedInView} total={filtered.length} /></div>
          <span style={{ fontSize: 12, color: "#757575", fontFamily: "var(--font-mono)", whiteSpace: "nowrap" }}>
            {solvedInView}/{filtered.length} bài đã giải
          </span>
        </div>
      </div>
    </div>
  );
}

function Bar({ value, total }: { value: number; total: number }) {
  return (
    <div style={{ height: 6, background: "#EEEEEE", borderRadius: 3, overflow: "hidden" }}>
      <div style={{
        height: "100%", borderRadius: 3,
        width: total > 0 ? `${(value / total) * 100}%` : "0%",
        background: "linear-gradient(90deg, #66BB6A, #43A047)",
        transition: "width 0.5s ease",
      }} />
    </div>
  );
}

const TH_S: React.CSSProperties = {
  padding: "10px 14px", fontSize: 12, fontWeight: 700, color: "#757575",
  textAlign: "center", whiteSpace: "nowrap", textTransform: "uppercase",
  borderBottom: "1px solid var(--color-table-border)",
  position: "sticky", top: 0, zIndex: 2, background: "var(--color-table-header-bg)",
};

const TD_S: React.CSSProperties = {
  padding: "10px 14px", fontSize: 13, verticalAlign: "middle",
};
