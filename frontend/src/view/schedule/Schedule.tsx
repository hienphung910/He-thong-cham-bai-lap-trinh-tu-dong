import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Modal, Muted, Page, Row, Table, Tag, errorText, fmtTime, localDateTime, useLoad } from "../../shared/kit";
import type { Meeting } from "../../shared/platformTypes";

const PHASE: Record<Meeting["phase"], { label: string; color: string; order: number }> = {
  OPEN: { label: "Đang diễn ra", color: "green", order: 0 },
  UPCOMING: { label: "Sắp diễn ra", color: "blue", order: 1 },
  ENDED: { label: "Đã kết thúc", color: "gray", order: 2 },
};

/** Buoi dang dien ra len dau, roi cac buoi sap toi (gan nhat truoc), cuoi cung la buoi da qua (moi nhat truoc). */
function byPhaseThenTime(a: Meeting, b: Meeting): number {
  if (a.phase !== b.phase) return PHASE[a.phase].order - PHASE[b.phase].order;
  return a.phase === "ENDED" ? b.startAt.localeCompare(a.startAt) : a.startAt.localeCompare(b.startAt);
}

/**
 * Lich hoc: giang vien len lich buoi hoc cho lop minh va dan link phong hoc (Google Meet, Zoom...)
 * vao buoi do - dan ngay luc len lich hoac de den hom hoc moi dan. Hoc sinh thay gio hoc, lop,
 * giang vien; toi gio thi nut "Tham gia phong hoc" mo link giang vien da dan.
 */
export default function Schedule() {
  const list = useLoad(() => papi.meetings());
  const classes = useLoad(() => papi.classrooms());
  const [error, setError] = useState("");
  const [form, setForm] = useState({ classroomId: "", title: "", startAt: localDateTime(), durationMin: 90, link: "" });
  /** Buoi hoc dang duoc dan / sua link. */
  const [editing, setEditing] = useState<{ meeting: Meeting; link: string } | null>(null);
  const managed = (classes.data ?? []).filter((c) => c.canManage);
  const meetings = [...(list.data ?? [])].sort(byPhaseThenTime);

  // Tai lai moi 30 giay de nut "Tham gia" tu bat khi toi gio va khi giang vien vua dan link.
  const reload = list.reload;
  useEffect(() => {
    const timer = setInterval(reload, 30_000);
    return () => clearInterval(timer);
  }, [reload]);

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  function join(link: string) {
    window.open(link, "_blank", "noopener,noreferrer");
  }

  function roomCell(m: Meeting) {
    if (m.phase === "ENDED") return <Muted>Buổi học đã kết thúc</Muted>;
    if (m.phase === "OPEN") {
      return m.link
        ? <Btn small onClick={() => join(m.link)}>Tham gia phòng học</Btn>
        : <Muted>Giảng viên chưa đăng link phòng học</Muted>;
    }
    // Chua toi gio: hoc sinh chi biet da co link hay chua; nguoi quan ly lop mo thu duoc ngay.
    if (m.canManage && m.link) return <Btn small kind="ghost" onClick={() => join(m.link)}>Mở thử link</Btn>;
    return <Muted>{m.hasLink ? "Đã có link, nút tham gia sẽ mở khi tới giờ" : "Chưa có link phòng học"}</Muted>;
  }

  return (
    <Page title="Lịch học" subtitle="Buổi học của các lớp bạn tham gia. Tới giờ học, bấm Tham gia phòng học để mở link giảng viên đã đăng.">
      <ErrorBox text={error || list.error} />

      {managed.length > 0 && (
        <Card title="Thêm lịch học">
          <Row>
            <Field label="Lớp">
              <select style={INPUT} value={form.classroomId} onChange={(e) => setForm({ ...form, classroomId: e.target.value })}>
                <option value="">(chọn lớp)</option>
                {managed.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Tên buổi học"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
            <Field label="Bắt đầu"><input type="datetime-local" style={INPUT} value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} /></Field>
            <Field label="Thời lượng (phút)"><input type="number" style={{ ...INPUT, width: 110 }} value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: Number(e.target.value) })} /></Field>
            <Field label="Link phòng học (có thể để trống, dán sau)">
              <input style={{ ...INPUT, width: 280 }} value={form.link} onChange={(e) => setForm({ ...form, link: e.target.value })} placeholder="https://meet.google.com/..." />
            </Field>
            <Btn disabled={!form.classroomId || !form.title.trim()} onClick={() => void act(async () => {
              await papi.createMeeting({ ...form, link: form.link.trim() });
              setForm({ ...form, title: "", link: "" });
            })}>Thêm lịch</Btn>
          </Row>
        </Card>
      )}

      <Table head={["Buổi học", "Lớp", "Giảng viên", "Thời gian", "Trạng thái", "Phòng học", ""]} empty="Chưa có lịch học nào."
        rows={meetings.map((m) => [
          <b>{m.title}</b>,
          m.classroomName,
          m.hostName,
          <span>{fmtTime(m.startAt)}<div style={{ fontSize: 12, color: "#9E9E9E" }}>{m.durationMin} phút</div></span>,
          <Tag color={PHASE[m.phase].color}>{PHASE[m.phase].label}</Tag>,
          roomCell(m),
          m.canManage ? (
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {m.phase !== "ENDED" && (
                <Btn small kind="ghost" onClick={() => setEditing({ meeting: m, link: m.link })}>{m.link ? "Sửa link" : "Dán link"}</Btn>
              )}
              <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá lịch học "${m.title}"?`)) void act(() => papi.deleteMeeting(m.id)); }}>Xoá</Btn>
            </div>
          ) : "",
        ])} />

      {editing && (
        <Modal title={`Link phòng học — ${editing.meeting.title}`} onClose={() => setEditing(null)} width={560}>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <Muted>Dán link phòng học (Google Meet, Zoom, Microsoft Teams...). Học sinh bấm Tham gia phòng học sẽ được mở thẳng link này. Để trống rồi lưu để gỡ link.</Muted>
            <input autoFocus style={{ ...INPUT, width: "100%", boxSizing: "border-box" }} value={editing.link}
              onChange={(e) => setEditing({ ...editing, link: e.target.value })} placeholder="https://meet.google.com/..." />
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <Btn kind="ghost" onClick={() => setEditing(null)}>Huỷ</Btn>
              <Btn onClick={() => void act(async () => {
                await papi.setMeetingLink(editing.meeting.id, editing.link.trim());
                setEditing(null);
              })}>Lưu link</Btn>
            </div>
          </div>
        </Modal>
      )}
    </Page>
  );
}
