import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Row, Switch, Table, Tag, errorText, fmtTime, localDateTime, useLoad } from "../../shared/kit";
import { useBackState } from "../../shared/nav";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";
import type { Contest } from "../../shared/platformTypes";
import { ProctorPanel } from "./Proctor";
import ProblemPicker from "../problem/ProblemPicker";
import ProblemField from "../problem/ProblemField";

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
  // Ky thi to chuc cho mot lop: giang vien chon trong cac lop minh quan ly (quan tri vien thay moi lop).
  const classes = useLoad(() => (isStaff ? papi.classrooms() : Promise.resolve([])), [isStaff]);
  const managed = (classes.data ?? []).filter((c) => c.canManage);
  // Ky thi dang mo nam trong lich su trinh duyet: nut Back quay ve danh sach ky thi.
  const [openId, setOpenId] = useBackState<string | null>("contest", null);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ title: "", description: "", startAt: localDateTime(), durationMin: 120, problems: [] as string[], proctored: true, classroomId: "" });

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <ContestDetail id={openId} onBack={() => setOpenId(null)} />;

  return (
    <Page title="Kỳ thi">
      <ErrorBox text={error || list.error} />
      {isStaff && (
        <Card title="Tạo kỳ thi">
          <Row>
            <Field label="Lớp tham gia">
              <select style={INPUT} value={form.classroomId} onChange={(e) => setForm({ ...form, classroomId: e.target.value })}>
                <option value="">(chọn lớp)</option>
                {managed.map((c) => <option key={c.id} value={c.id}>{c.name} · {c.studentCount} học sinh</option>)}
              </select>
            </Field>
            <Field label="Tên kỳ thi"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
            <Field label="Bắt đầu"><input type="datetime-local" style={INPUT} value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} /></Field>
            <Field label="Thời lượng (phút)"><input type="number" style={INPUT} value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: Number(e.target.value) })} /></Field>
            <ProblemField label="Các bài" pickerTitle="Chọn bài cho kỳ thi" ids={form.problems}
              onChange={(problems) => setForm((old) => ({ ...old, problems }))} />
            <Switch label="Giám sát thí sinh" checked={form.proctored} onChange={(proctored) => setForm({ ...form, proctored })}
              title="Ghi lại việc rời tab, chuyển cửa sổ, sao chép, dán của thí sinh trong lúc thi. Bật / tắt lại được ở trang kỳ thi." />
            <Btn disabled={!form.classroomId || form.problems.length === 0}
              title={!form.classroomId ? "Hãy chọn lớp tham gia kỳ thi" : form.problems.length === 0 ? "Hãy chọn ít nhất một bài" : undefined}
              onClick={() => void act(async () => {
                await papi.createContest({ title: form.title, description: form.description, startAt: form.startAt, durationMin: form.durationMin, problemIds: form.problems, proctored: form.proctored, classroomId: form.classroomId });
                setForm({ ...form, title: "", problems: [] });
              })}>Tạo</Btn>
          </Row>
        </Card>
      )}
      <Table head={["Kỳ thi", "Lớp", "Người tạo", "Bắt đầu", "Thời lượng", "Số bài", "Trạng thái", ""]}
        empty={isStaff ? "Chưa có kỳ thi nào." : "Các lớp của bạn chưa có kỳ thi nào."}
        rows={(list.data ?? []).map((c) => [
          <span><b>{c.title}</b><div style={{ fontSize: 12, color: "#9E9E9E" }}>{c.description}</div></span>,
          c.classroomName, c.ownerName, fmtTime(c.startAt), `${c.durationMin} phút`, c.problemCount,
          <Tag color={STATUS[c.status].color}>{STATUS[c.status].label}</Tag>,
          <div style={{ display: "flex", gap: 6 }}>
            <Btn small onClick={() => setOpenId(c.id)}>Xem</Btn>
            {c.canManage && <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá kỳ thi "${c.title}"?`)) void act(() => papi.deleteContest(c.id)); }}>Xoá</Btn>}
          </div>,
        ])} />
    </Page>
  );
}

/** Chi tiet mot ky thi: de thi, bang xep hang hien tai va danh sach cac luot nop. */
function ContestDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const contest = useLoad(() => papi.contest(id), [id]);
  const events = useLoad(() => papi.contestEvents(id), [id]);
  const c = contest.data;
  const { user } = useAuth();
  const isStudent = user?.role === "STUDENT";
  // Nguoi tao ky thi: tai lai moi 10 giay khi ky thi dang dien ra de thay ai vua vao thi / nop bai.
  const reloadContest = contest.reload;
  const watching = !!c && c.canManage && c.status === "RUNNING";
  useEffect(() => {
    if (!watching) return;
    const timer = setInterval(reloadContest, 10_000);
    return () => clearInterval(timer);
  }, [watching, reloadContest]);

  /** Hoc sinh bam "Vao thi": App.tsx nghe su kien nay de chuyen sang man hinh thi. */
  async function joinExam() {
    setEditError("");
    try {
      await papi.joinContest(id);
      window.dispatchEvent(new Event("oj-contests-changed"));
    } catch (e) { setEditError(errorText(e)); }
  }
  /** Dang mo bang chon bai de them vao ky thi nay. */
  const [pickingMore, setPickingMore] = useState(false);
  // Them / bot bai: chi nguoi quan ly ky thi (c.canManage) va chi TRUOC khi thi moi thay cac nut nay.
  // Gio thi dang sua trong form; null = chua sua, hien gia tri hien tai cua ky thi.
  const [schedule, setSchedule] = useState<{ startAt: string; durationMin: number } | null>(null);
  const [editError, setEditError] = useState("");

  async function edit(work: () => Promise<unknown>) {
    setEditError("");
    try { await work(); setSchedule(null); contest.reload(); } catch (e) { setEditError(errorText(e)); }
  }

  const canEditProblems = !!c && c.canManage && c.status === "UPCOMING";
  const shownSchedule = schedule ?? { startAt: c ? c.startAt.slice(0, 16) : "", durationMin: c ? c.durationMin : 0 };

  const pastEvents = events.data ?? [];

  return (
    <Page title={c ? c.title : "Kỳ thi"} subtitle={c ? `Lớp ${c.classroomName} · ${fmtTime(c.startAt)} → ${fmtTime(c.endAt)}${c.description ? ` · ${c.description}` : ""}` : ""}
      actions={<Btn kind="ghost" onClick={onBack}>← Danh sách kỳ thi</Btn>}>
      <ErrorBox text={editError || contest.error || events.error} />
      {c && (
        <>
          <Card title="Đề thi" right={<Tag color={STATUS[c.status].color}>{STATUS[c.status].label}</Tag>}>
            {c.problemsHidden && (
              <div style={{ fontSize: 13, color: "#757575" }}>
                Kỳ thi có {c.problemCount} bài.
              </div>
            )}
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {c.problems.map((p) => (
                <span key={p.id} style={{ display: "inline-flex", alignItems: "center", gap: 2 }}>
                  <ProblemChip id={p.id} title={p.title} />
                  {canEditProblems && (
                    <button title={`Bỏ bài ${p.id} khỏi kỳ thi`} onClick={() => void edit(() => papi.removeContestProblem(c.id, p.id))}
                      style={{ border: "none", background: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 14, padding: "0 4px" }}>×</button>
                  )}
                </span>
              ))}
            </div>
            {canEditProblems && (
              <div style={{ marginTop: 10 }}>
                <Btn small kind="ghost" onClick={() => setPickingMore(true)}>Thêm bài từ danh sách</Btn>
              </div>
            )}
            {pickingMore && (
              <ProblemPicker title={`Thêm bài — ${c.title}`} excluded={c.problems.map((p) => p.id)}
                onClose={() => setPickingMore(false)}
                onPick={(ids) => {
                  setPickingMore(false);
                  // May chu nhan tung bai mot: them lan luot, gap bai loi thi dung va bao loi.
                  void edit(async () => { for (const pid of ids) await papi.addContestProblem(c.id, pid); });
                }} />
            )}
            {isStudent && c.status === "RUNNING" && !c.joined && !c.finished && (
              <div style={{ marginTop: 10, padding: "12px 14px", borderRadius: 8, background: "var(--color-red-light)", border: "1px solid #F3D9A4", display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap" }}>
                <div style={{ flex: 1, minWidth: 260, fontSize: 13, color: "#5D4037", lineHeight: 1.6 }}>
                  <b>Kỳ thi đang diễn ra.</b> Sau khi bấm Vào thi, màn hình của bạn chỉ còn kỳ thi này cho tới khi bạn nộp bài sớm hoặc hết giờ:
                  không có menu, không có bình luận và không có gợi ý sau khi nộp.
                  {c.proctored && " Kỳ thi có giám sát: việc rời tab, chuyển cửa sổ, sao chép và dán sẽ được ghi lại và gửi cho giảng viên."}
                </div>
                <Btn onClick={() => void joinExam()}>Vào thi</Btn>
              </div>
            )}
            {isStudent && c.finished && (
              <div style={{ marginTop: 10, padding: "10px 14px", borderRadius: 8, background: "#F5F5F5", border: "1px solid #E0E0E0", fontSize: 13, color: "#424242" }}>
                Bạn đã nộp bài và kết thúc kỳ thi lúc <b>{fmtTime(c.finishedAt)}</b>. Các bài nộp sau thời điểm đó không được tính vào kỳ thi.
              </div>
            )}
          </Card>

          {c.canManage && c.status !== "ENDED" && (
            <Card title={c.status === "RUNNING" ? "Gia hạn kỳ thi" : "Giờ thi"}>
              <Row>
                <Field label="Bắt đầu">
                  <input type="datetime-local" style={INPUT} disabled={c.status === "RUNNING"} value={shownSchedule.startAt}
                    onChange={(e) => setSchedule({ ...shownSchedule, startAt: e.target.value })} />
                </Field>
                <Field label="Thời lượng (phút)">
                  <input type="number" style={INPUT} min={c.status === "RUNNING" ? c.durationMin : 5} value={shownSchedule.durationMin}
                    onChange={(e) => setSchedule({ ...shownSchedule, durationMin: Number(e.target.value) })} />
                </Field>
                <Btn disabled={schedule === null} onClick={() => void edit(() => papi.rescheduleContest(c.id,
                  // Dang thi: giu nguyen gio bat dau (ke ca giay), chi doi thoi luong.
                  c.status === "RUNNING" ? c.startAt : shownSchedule.startAt, shownSchedule.durationMin))}>
                  {c.status === "RUNNING" ? "Gia hạn" : "Lưu giờ thi"}
                </Btn>
              </Row>
            </Card>
          )}

          {c.canManage && (
            <Card title={`Thí sinh tham gia (${c.participants?.length ?? 0})`}>
              <Table head={["Thí sinh", "Vào thi lúc", "Trạng thái"]} empty="Chưa có thí sinh nào bấm Vào thi."
                rows={(c.participants ?? []).map((p) => [
                  <span><b>{p.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{p.username}</span></span>,
                  fmtTime(p.joinedAt),
                  p.finishedAt ? <Tag color="gray">Đã nộp bài lúc {fmtTime(p.finishedAt).slice(11)}</Tag>
                    : c.status === "RUNNING" ? <Tag color="green">Đang thi</Tag> : <Tag color="gray">Hết giờ</Tag>,
                ])} />
            </Card>
          )}

          {c.canManage && (
            <ProctorPanel contestId={c.id} running={c.status === "RUNNING"} enabled={c.proctored}
              toggle={<Switch label="Giám sát" checked={c.proctored}
                onChange={(on) => void edit(() => papi.setProctoring(c.id, on))}
                title={c.proctored ? "Đang ghi nhận. Bấm để tắt giám sát." : "Đang không ghi nhận. Bấm để bật giám sát."} />} />
          )}

          <Card title="Bảng xếp hạng">
            <Table head={["#", "Thí sinh", "Số bài", ...c.problems.map((p) => p.id)]}
              empty="Chưa có ai nộp bài trong khoảng thời gian này."
              rows={(c.standings ?? []).map((r) => [
                r.rank, <span><b>{r.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{r.username}</span></span>,
                <b>{r.solved}</b>,
                ...r.cells.map((cell) => cell.solvedAt !== null
                  ? <Tag color="green">✓ Đã giải</Tag>
                  : cell.wrong > 0 ? <Tag color="red">Chưa đúng</Tag> : "·"),
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
