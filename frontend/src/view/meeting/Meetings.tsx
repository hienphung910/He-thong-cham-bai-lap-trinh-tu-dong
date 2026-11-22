import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Modal, Muted, Page, Row, SimNote, Table, Tag, errorText, fmtTime, localDateTime, useLoad } from "../../shared/kit";
import CodeEditor from "../judge/CodeEditor";
import Room from "./Room";
import Whiteboard from "./Whiteboard";
import type { LanguageInfo } from "../../shared/types";
import type { AttendanceSheet, Meeting, Recording } from "../../shared/platformTypes";

const STATUS: Record<Meeting["status"], { label: string; color: string }> = {
  SCHEDULED: { label: "Đã lên lịch", color: "blue" },
  LIVE: { label: "Đang diễn ra", color: "green" },
  ENDED: { label: "Đã kết thúc", color: "gray" },
};
const ATTENDANCE: Record<string, string> = { PRESENT: "Có mặt", LATE: "Đi muộn", ABSENT: "Vắng", "": "Chưa điểm danh" };

/**
 * Module 15 (quan ly buoi hoc), 20 (diem danh), 37 (ban ghi): len lich, bat dau,
 * ket thuc buoi hoc; vao phong hoc; xem va sua diem danh; xem lai ban ghi noi dung.
 */
export default function Meetings({ languages }: { languages: LanguageInfo[] }) {
  const list = useLoad(() => papi.meetings());
  const classes = useLoad(() => papi.classrooms());
  const recordings = useLoad(() => papi.recordings());
  const [roomId, setRoomId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [sheet, setSheet] = useState<{ meetingId: string; data: AttendanceSheet } | null>(null);
  const [recording, setRecording] = useState<Recording | null>(null);
  const [form, setForm] = useState({ classroomId: "", title: "", startAt: localDateTime(), durationMin: 90, recorded: true });
  const managed = (classes.data ?? []).filter((c) => c.canManage);

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); recordings.reload(); } catch (e) { setError(errorText(e)); }
  }

  async function openSheet(meetingId: string) {
    setSheet({ meetingId, data: await papi.attendance(meetingId) });
  }

  if (roomId) return <Room meetingId={roomId} languages={languages} onLeave={() => { setRoomId(null); list.reload(); }} />;

  return (
    <Page title="Phòng học trực tuyến" subtitle="Buổi học của các lớp bạn tham gia. Vào phòng khi buổi học đang diễn ra.">
      <ErrorBox text={error || list.error} />

      {managed.length > 0 && (
        <Card title="Lên lịch buổi học">
          <Row>
            <Field label="Lớp">
              <select style={INPUT} value={form.classroomId} onChange={(e) => setForm({ ...form, classroomId: e.target.value })}>
                <option value="">(chọn lớp)</option>
                {managed.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Tên buổi học"><input style={INPUT} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
            <Field label="Bắt đầu"><input type="datetime-local" style={INPUT} value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} /></Field>
            <Field label="Thời lượng (phút)"><input type="number" style={INPUT} value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: Number(e.target.value) })} /></Field>
            <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, height: 34 }}>
              <input type="checkbox" checked={form.recorded} onChange={(e) => setForm({ ...form, recorded: e.target.checked })} /> Ghi lại nội dung
            </label>
            <Btn disabled={!form.classroomId} onClick={() => void act(async () => { await papi.createMeeting(form); setForm({ ...form, title: "" }); })}>Lên lịch</Btn>
          </Row>
        </Card>
      )}

      <Table head={["Buổi học", "Lớp", "Bắt đầu", "Thời lượng", "Trạng thái", ""]} empty="Chưa có buổi học nào."
        rows={(list.data ?? []).map((m) => [
          <span><b>{m.title}</b><div style={{ fontSize: 12, color: "#9E9E9E" }}>Chủ phòng: {m.hostName}{m.recorded ? " · có ghi lại" : ""}</div></span>,
          m.classroomName, fmtTime(m.startAt), `${m.durationMin} phút`,
          <Tag color={STATUS[m.status].color}>{STATUS[m.status].label}</Tag>,
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            {m.status === "LIVE" && <Btn small onClick={() => setRoomId(m.id)}>Vào phòng</Btn>}
            {m.canManage && m.status === "SCHEDULED" && <Btn small onClick={() => void act(() => papi.startMeeting(m.id))}>Bắt đầu</Btn>}
            {m.canManage && m.status === "LIVE" && <Btn small kind="danger" onClick={() => void act(() => papi.endMeeting(m.id))}>Kết thúc</Btn>}
            {m.canManage && <Btn small kind="ghost" onClick={() => void act(() => openSheet(m.id))}>Điểm danh</Btn>}
            {m.canManage && m.status !== "LIVE" && <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá buổi học "${m.title}"?`)) void act(() => papi.deleteMeeting(m.id)); }}>Xoá</Btn>}
          </div>,
        ])} />

      <Card title="Bản ghi buổi học">
        <SimNote>Bản ghi lưu lại nội dung của buổi học (mã nguồn cuối buổi, bảng trắng, chat), không ghi hình hay ghi âm.</SimNote>
        <div style={{ height: 10 }} />
        {recordings.data?.length === 0 && <Muted>Chưa có bản ghi nào. Bản ghi được tạo khi một buổi học có bật "Ghi lại nội dung" kết thúc.</Muted>}
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(recordings.data ?? []).map((r) => (
            <div key={r.id} style={{ display: "flex", gap: 10, alignItems: "center", fontSize: 13, flexWrap: "wrap" }}>
              <b>{r.title}</b>
              <span style={{ color: "#9E9E9E" }}>{fmtTime(r.createdAt)} · {r.durationMin} phút · {r.strokeCount} nét vẽ · {r.chatCount} tin nhắn</span>
              <Btn small kind="ghost" onClick={() => void act(async () => setRecording(await papi.recording(r.id)))}>Xem lại</Btn>
            </div>
          ))}
        </div>
      </Card>

      {sheet && (
        <Modal title={`Điểm danh — ${sheet.data.title}`} onClose={() => setSheet(null)} width={640}>
          <Muted>Học sinh vào phòng sẽ được điểm danh tự động (sau 10 phút tính là đi muộn). Giảng viên có thể sửa tay.</Muted>
          <Table head={["Học sinh", "Giờ vào", "Kết quả", "Sửa"]} empty="Lớp chưa có học sinh."
            rows={sheet.data.rows.map((r) => [
              <span><b>{r.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{r.username}</span></span>,
              r.joinedAt ? fmtTime(r.joinedAt) : "—",
              <Tag color={r.status === "PRESENT" ? "green" : r.status === "LATE" ? "orange" : r.status === "ABSENT" ? "red" : "gray"}>{ATTENDANCE[r.status]}</Tag>,
              <select style={{ ...INPUT, width: 130, height: 28 }} value={r.status}
                onChange={(e) => void act(async () => { await papi.markAttendance(sheet.meetingId, r.id, e.target.value); await openSheet(sheet.meetingId); })}>
                {r.status === "" && <option value="">(chọn)</option>}
                <option value="PRESENT">Có mặt</option>
                <option value="LATE">Đi muộn</option>
                <option value="ABSENT">Vắng</option>
              </select>,
            ])} />
        </Modal>
      )}

      {recording && (
        <Modal title={`Bản ghi — ${recording.title}`} onClose={() => setRecording(null)} width={820}>
          <b style={{ fontSize: 13 }}>Mã nguồn cuối buổi ({recording.language})</b>
          <CodeEditor value={recording.code || "(không có mã nguồn)"} readOnly maxHeight={220} />
          <b style={{ fontSize: 13 }}>Bảng trắng</b>
          <Whiteboard strokes={recording.strokes ?? []} />
          <b style={{ fontSize: 13 }}>Nội dung chat</b>
          {(recording.chat ?? []).length === 0 && <Muted>Không có tin nhắn.</Muted>}
          {(recording.chat ?? []).map((m, i) => (
            <div key={i} style={{ fontSize: 13 }}><b>{m.senderName}</b> <span style={{ color: "#9E9E9E" }}>{m.sentAt.slice(11, 16)}</span>: {m.text}</div>
          ))}
        </Modal>
      )}
    </Page>
  );
}
