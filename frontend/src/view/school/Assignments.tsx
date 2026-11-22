import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Modal, Muted, Page, Progress, Row, Table, Tag, errorText, fmtTime, localDateTime, useLoad } from "../../shared/kit";
import { parseIds } from "../../shared/nav";
import ProblemChip from "../../shared/ProblemChip";
import type { AssignmentProgress } from "../../shared/platformTypes";

/**
 * Module 13 (bai tap duoc giao): hoc sinh thay bai tap cua cac lop minh hoc kem tien do;
 * giang vien giao bai cho lop va xem bang tien do ca lop.
 */
export default function Assignments() {
  const list = useLoad(() => papi.assignments());
  const classes = useLoad(() => papi.classrooms());
  const [error, setError] = useState("");
  const [progress, setProgress] = useState<AssignmentProgress | null>(null);
  const [form, setForm] = useState({ classroomId: "", title: "", description: "", problems: "", dueAt: localDateTime(7 * 24 * 60) });
  const managed = (classes.data ?? []).filter((c) => c.canManage);

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Bài tập được giao" subtitle="Tiến độ tính từ lịch sử nộp bài: bài nào đã AC thì được đánh dấu xong.">
      <ErrorBox text={error || list.error} />

      {managed.length > 0 && (
        <Card title="Giao bài cho lớp">
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <Row>
              <Field label="Lớp">
                <select style={INPUT} value={form.classroomId} onChange={(e) => setForm({ ...form, classroomId: e.target.value })}>
                  <option value="">(chọn lớp)</option>
                  {managed.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Field>
              <Field label="Tên bài tập"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
              <Field label="Hạn nộp"><input type="datetime-local" style={INPUT} value={form.dueAt} onChange={(e) => setForm({ ...form, dueAt: e.target.value })} /></Field>
            </Row>
            <Row>
              <Field label="Các bài cần làm (mã bài, cách nhau dấu phẩy)"><input style={INPUT} value={form.problems} onChange={(e) => setForm({ ...form, problems: e.target.value })} placeholder="K001, K002, K003" /></Field>
              <Field label="Ghi chú"><input style={INPUT} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
              <Btn disabled={!form.classroomId} onClick={() => void act(async () => {
                await papi.createAssignment({ classroomId: form.classroomId, title: form.title, description: form.description, problemIds: parseIds(form.problems), dueAt: form.dueAt });
                setForm({ ...form, title: "", description: "", problems: "" });
              })}>Giao bài</Btn>
            </Row>
          </div>
        </Card>
      )}

      {list.data?.length === 0 && <Muted>Chưa có bài tập nào được giao.</Muted>}
      {(list.data ?? []).map((a) => (
        <Card key={a.id} title={a.title} right={<>
          <Tag color="blue">{a.classroomName}</Tag>
          <Tag color={a.overdue ? "red" : "green"}>{a.overdue ? "Đã hết hạn" : "Còn hạn"}</Tag>
          {a.canManage && <Btn small kind="ghost" onClick={() => void act(async () => setProgress(await papi.assignmentProgress(a.id)))}>Tiến độ lớp</Btn>}
          {a.canManage && <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá bài tập "${a.title}"?`)) void act(() => papi.deleteAssignment(a.id)); }}>Xoá</Btn>}
        </>}>
          <div style={{ fontSize: 13, color: "#616161", marginBottom: 8 }}>
            Hạn nộp <b>{fmtTime(a.dueAt)}</b> · giao bởi {a.createdBy}{a.description ? ` · ${a.description}` : ""}
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
            <div style={{ flex: 1, maxWidth: 260 }}><Progress value={a.total === 0 ? 0 : (a.solvedCount * 100) / a.total} /></div>
            <span style={{ fontSize: 12.5, color: "#616161" }}>Bạn đã làm {a.solvedCount}/{a.total} bài</span>
          </div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            {a.problems.map((p) => <ProblemChip key={p.id} id={p.id} title={p.title} done={p.solved} />)}
          </div>
        </Card>
      ))}

      {progress && (
        <Modal title={`Tiến độ lớp — ${progress.title}`} onClose={() => setProgress(null)} width={760}>
          <Table head={["Học sinh", ...progress.problemIds, "Tổng"]} empty="Lớp chưa có học sinh."
            rows={progress.rows.map((r) => [
              <span><b>{r.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{r.username}</span></span>,
              ...r.solved.map((ok) => ok ? <Tag color="green">✓</Tag> : <Tag>—</Tag>),
              `${r.solvedCount}/${progress.problemIds.length}`,
            ])} />
        </Modal>
      )}
    </Page>
  );
}
