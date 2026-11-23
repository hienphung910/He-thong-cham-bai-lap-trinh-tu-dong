import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Row, Table, Tag, errorText, fmtTime, localDateTime, useLoad } from "../../shared/kit";
import { parseIds } from "../../shared/nav";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";
import type { Contest } from "../../shared/platformTypes";

const STATUS: Record<Contest["status"], { label: string; color: string }> = {
  UPCOMING: { label: "Sắp diễn ra", color: "blue" },
  RUNNING: { label: "Đang thi", color: "green" },
  ENDED: { label: "Đã kết thúc", color: "gray" },
};

/** Module 21 (quan ly cuoc thi): danh sach cuoc thi, tao cuoc thi, mo chi tiet. */
export default function Contests() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const list = useLoad(() => papi.contests());
  const [openId, setOpenId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ title: "", description: "", startAt: localDateTime(), durationMin: 120, problems: "" });

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <ContestDetail id={openId} onBack={() => setOpenId(null)} />;

  return (
    <Page title="Kỳ thi" subtitle="Luật ICPC: xếp theo số bài giải được, bằng nhau thì ít phút phạt hơn đứng trước (mỗi lần nộp sai +20 phút).">
      <ErrorBox text={error || list.error} />
      {isStaff && (
        <Card title="Tạo kỳ thi">
          <Row>
            <Field label="Tên kỳ thi"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
            <Field label="Bắt đầu"><input type="datetime-local" style={INPUT} value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} /></Field>
            <Field label="Thời lượng (phút)"><input type="number" style={INPUT} value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: Number(e.target.value) })} /></Field>
            <Field label="Các bài (mã bài kho chung hoặc kho của bạn, cách nhau dấu phẩy)"><input style={INPUT} value={form.problems} onChange={(e) => setForm({ ...form, problems: e.target.value })} placeholder="K001, K002" /></Field>
            <Btn onClick={() => void act(async () => {
              await papi.createContest({ title: form.title, description: form.description, startAt: form.startAt, durationMin: form.durationMin, problemIds: parseIds(form.problems) });
              setForm({ ...form, title: "", problems: "" });
            })}>Tạo</Btn>
          </Row>
        </Card>
      )}
      <Table head={["Kỳ thi", "Người tạo", "Bắt đầu", "Thời lượng", "Số bài", "Trạng thái", ""]} empty="Chưa có kỳ thi nào."
        rows={(list.data ?? []).map((c) => [
          <span><b>{c.title}</b><div style={{ fontSize: 12, color: "#9E9E9E" }}>{c.description}</div></span>,
          c.ownerName, fmtTime(c.startAt), `${c.durationMin} phút`, c.problemCount,
          <Tag color={STATUS[c.status].color}>{STATUS[c.status].label}</Tag>,
          <div style={{ display: "flex", gap: 6 }}>
            <Btn small onClick={() => setOpenId(c.id)}>Xem</Btn>
            {c.canManage && <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá kỳ thi "${c.title}"?`)) void act(() => papi.deleteContest(c.id)); }}>Xoá</Btn>}
          </div>,
        ])} />
    </Page>
  );
}

/**
 * Chi tiet mot ky thi + Module 22 (xem lai): keo thanh truot de xem bang xep hang
 * tai mot phut bat ky. May chu tinh lai bang xep hang tu cac bai nop truoc phut do.
 */
function ContestDetail({ id, onBack }: { id: string; onBack: () => void }) {
  /** null = bang xep hang hien tai; so = dang xem lai tai phut do. */
  const [minute, setMinute] = useState<number | null>(null);
  const [playing, setPlaying] = useState(false);
  const contest = useLoad(() => papi.contest(id, minute ?? undefined), [id, minute]);
  const events = useLoad(() => papi.contestEvents(id), [id]);
  const c = contest.data;
  const duration = c?.durationMin ?? 0;
  // Them / bot bai: chi nguoi tao ky thi (c.canManage) moi thay cac nut nay.
  const [newProblem, setNewProblem] = useState("");
  const [editError, setEditError] = useState("");

  async function edit(work: () => Promise<unknown>) {
    setEditError("");
    try { await work(); setNewProblem(""); contest.reload(); } catch (e) { setEditError(errorText(e)); }
  }

  // Che do tu phat: moi 400 ms tien them mot doan cho den het gio thi.
  useEffect(() => {
    if (!playing) return;
    const step = Math.max(1, Math.round(duration / 40));
    const timer = setInterval(() => {
      setMinute((m) => {
        const next = Math.min(duration, (m ?? 0) + step);
        if (next >= duration) setPlaying(false);
        return next;
      });
    }, 400);
    return () => clearInterval(timer);
  }, [playing, duration]);

  const shownMinute = minute ?? duration;
  const pastEvents = (events.data ?? []).filter((e) => e.minute <= shownMinute);

  return (
    <Page title={c ? c.title : "Kỳ thi"} subtitle={c ? `${fmtTime(c.startAt)} → ${fmtTime(c.endAt)} · ${c.description}` : ""}
      actions={<Btn kind="ghost" onClick={onBack}>← Danh sách kỳ thi</Btn>}>
      <ErrorBox text={editError || contest.error || events.error} />
      {c && (
        <>
          <Card title="Đề thi" right={<Tag color={STATUS[c.status].color}>{STATUS[c.status].label}</Tag>}>
            {c.problemsHidden && (
              <div style={{ fontSize: 13, color: "#757575" }}>
                Kỳ thi có {c.problemCount} bài. Đề sẽ hiện khi kỳ thi bắt đầu.
              </div>
            )}
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {c.problems.map((p) => (
                <span key={p.id} style={{ display: "inline-flex", alignItems: "center", gap: 2 }}>
                  <ProblemChip id={p.id} title={p.title} />
                  {c.canManage && (
                    <button title={`Bỏ bài ${p.id} khỏi kỳ thi`} onClick={() => void edit(() => papi.removeContestProblem(c.id, p.id))}
                      style={{ border: "none", background: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 14, padding: "0 4px" }}>×</button>
                  )}
                </span>
              ))}
            </div>
            {c.canManage && (
              <div style={{ display: "flex", gap: 8, marginTop: 10, maxWidth: 420 }}>
                <input style={INPUT} value={newProblem} onChange={(e) => setNewProblem(e.target.value)}
                  placeholder="Mã bài (kho chung hoặc kho của bạn)"
                  onKeyDown={(e) => { if (e.key === "Enter" && newProblem.trim()) void edit(() => papi.addContestProblem(c.id, newProblem.trim())); }} />
                <Btn disabled={!newProblem.trim()} onClick={() => void edit(() => papi.addContestProblem(c.id, newProblem.trim()))}>Thêm bài</Btn>
              </div>
            )}
            <div style={{ marginTop: 8 }}><Muted>Bấm vào mã bài để làm. Bài nộp trong khung giờ thi được tính vào bảng xếp hạng.</Muted></div>
          </Card>

          <Card title="Xem lại diễn biến" right={<>
            <Btn small onClick={() => { if (minute === null || minute >= duration) setMinute(0); setPlaying(!playing); }}>{playing ? "Tạm dừng" : "▶ Phát lại"}</Btn>
            <Btn small kind="ghost" onClick={() => { setPlaying(false); setMinute(null); }}>Về hiện tại</Btn>
          </>}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <input type="range" min={0} max={duration} value={shownMinute} style={{ flex: 1 }}
                onChange={(e) => { setPlaying(false); setMinute(Number(e.target.value)); }} />
              <b style={{ fontSize: 13, minWidth: 110 }}>Phút {shownMinute}/{duration}</b>
            </div>
          </Card>

          <Card title={minute === null ? "Bảng xếp hạng" : `Bảng xếp hạng tại phút ${minute}`}>
            <Table head={["#", "Thí sinh", "Số bài", "Phút phạt", ...c.problems.map((p) => p.id)]}
              empty="Chưa có ai nộp bài trong khoảng thời gian này."
              rows={(c.standings ?? []).map((r) => [
                r.rank, <span><b>{r.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{r.username}</span></span>,
                <b>{r.solved}</b>, r.penalty,
                ...r.cells.map((cell) => cell.solvedAt !== null
                  ? <Tag color="green">+{cell.wrong > 0 ? cell.wrong : ""} ({cell.solvedAt}')</Tag>
                  : cell.wrong > 0 ? <Tag color="red">-{cell.wrong}</Tag> : "·"),
              ])} />
          </Card>

          <Card title={`Dòng thời gian (${pastEvents.length} lượt nộp)`}>
            {pastEvents.length === 0 && <Muted>Chưa có lượt nộp nào.</Muted>}
            <div style={{ display: "flex", flexDirection: "column", gap: 4, maxHeight: 240, overflow: "auto" }}>
              {[...pastEvents].reverse().map((e, i) => (
                <div key={i} style={{ fontSize: 13, color: "#424242", display: "flex", gap: 10, alignItems: "center" }}>
                  <span style={{ fontFamily: "var(--font-mono)", color: "#9E9E9E", minWidth: 60 }}>phút {e.minute}</span>
                  <b>{e.fullName}</b> nộp <span style={{ fontFamily: "var(--font-mono)" }}>{e.problemId}</span>
                  <Tag color={e.verdict === "AC" ? "green" : "red"}>{e.verdict}</Tag>
                </div>
              ))}
            </div>
          </Card>
        </>
      )}
    </Page>
  );
}
