import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, Grid, INPUT, Muted, Page, Row, Table, Tag, errorText, useLoad } from "../../shared/kit";
import { useAuth } from "../account/auth";
import ChatBox from "../community/ChatBox";
import type { Classroom } from "../../shared/platformTypes";

/**
 * Module 3 (lop hoc) va 35 (tro giang): danh sach lop cua toi, tao lop, tham gia lop
 * bang ma, xem thanh vien, them / xoa hoc sinh va tro giang, chat rieng cua lop.
 */
export default function Classrooms() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const list = useLoad(() => papi.classrooms());
  const orgs = useLoad(() => papi.organizations());
  const courses = useLoad(() => papi.courses());
  const [openId, setOpenId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [form, setForm] = useState({ name: "", organizationId: "", courseId: "" });

  /** Chay mot thao tac ghi, bao loi neu co, xong thi tai lai danh sach. */
  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <ClassroomDetail id={openId} onBack={() => { setOpenId(null); list.reload(); }} />;

  return (
    <Page title="Lớp của tôi" subtitle="Lớp học do giảng viên tạo. Học sinh tham gia bằng mã tham gia do giảng viên cung cấp.">
      <ErrorBox text={error || list.error} />
      {isStaff ? (
        <Card title="Tạo lớp mới">
          <Row>
            <Field label="Tên lớp"><input style={INPUT} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Tổ chức">
              <select style={INPUT} value={form.organizationId} onChange={(e) => setForm({ ...form, organizationId: e.target.value })}>
                <option value="">(không thuộc tổ chức)</option>
                {(orgs.data ?? []).map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select>
            </Field>
            <Field label="Khóa học">
              <select style={INPUT} value={form.courseId} onChange={(e) => setForm({ ...form, courseId: e.target.value })}>
                <option value="">(chưa gắn khóa học)</option>
                {(courses.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
              </select>
            </Field>
            <Btn onClick={() => void act(async () => { await papi.createClassroom(form); setForm({ name: "", organizationId: "", courseId: "" }); })}>Tạo lớp</Btn>
          </Row>
        </Card>
      ) : (
        <Card title="Tham gia lớp">
          <Row>
            <Field label="Mã tham gia (giảng viên cung cấp, gồm 8 ký tự)">
              <input style={INPUT} value={joinCode} onChange={(e) => setJoinCode(e.target.value)} />
            </Field>
            <Btn onClick={() => void act(async () => { await papi.joinClassroom(joinCode.trim()); setJoinCode(""); })}>Tham gia</Btn>
          </Row>
        </Card>
      )}

      {list.data?.length === 0 && <Muted>Bạn chưa thuộc lớp nào.</Muted>}
      <Grid min={280}>
        {(list.data ?? []).map((c) => (
          <Card key={c.id} title={c.name} right={<Tag color="blue">{c.id}</Tag>}>
            <div style={{ fontSize: 13, color: "#616161", lineHeight: 1.7 }}>
              <div>Giảng viên: <b>{c.teacherName}</b></div>
              <div>Tổ chức: {c.organizationName || "—"}</div>
              <div>Khóa học: {c.courseTitle || "—"}</div>
              <div>Sĩ số: {c.studentCount} học sinh</div>
            </div>
            <div style={{ marginTop: 10 }}><Btn small onClick={() => setOpenId(c.id)}>Vào lớp</Btn></div>
          </Card>
        ))}
      </Grid>
    </Page>
  );
}

function ClassroomDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const detail = useLoad(() => papi.classroom(id), [id]);
  const [error, setError] = useState("");
  const [student, setStudent] = useState("");
  const [assistant, setAssistant] = useState("");
  const c: Classroom | null = detail.data;

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); detail.reload(); } catch (e) { setError(errorText(e)); }
  }

  async function removeClass() {
    if (!window.confirm("Xoá lớp này? Bài tập giao và buổi học của lớp sẽ không còn hiện nữa.")) return;
    setError("");
    try { await papi.deleteClassroom(id); onBack(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title={c ? c.name : "Lớp học"} subtitle={c ? `Mã lớp ${c.id}${c.joinCode ? ` · Mã tham gia ${c.joinCode}` : ""} · Giảng viên ${c.teacherName}` : ""}
      actions={<>
        {c?.canManage && <Btn kind="danger" onClick={() => void removeClass()}>Xoá lớp</Btn>}
        <Btn kind="ghost" onClick={onBack}>← Danh sách lớp</Btn>
      </>}>
      <ErrorBox text={error || detail.error} />
      {c && (
        <>
          <Card title={`Học sinh (${c.students?.length ?? 0})`}>
            {c.canManage && (
              <div style={{ marginBottom: 12 }}>
                <Row>
                  <Field label="Thêm học sinh theo tên đăng nhập">
                    <input style={INPUT} value={student} onChange={(e) => setStudent(e.target.value)} placeholder="ví dụ hiennm" />
                  </Field>
                  <Btn onClick={() => void act(async () => { await papi.addStudent(id, student.trim()); setStudent(""); })}>Thêm</Btn>
                </Row>
              </div>
            )}
            <Table head={["Tên đăng nhập", "Họ tên", "Số bài đã giải", ""]} empty="Lớp chưa có học sinh."
              rows={(c.students ?? []).map((s) => [
                <b>{s.username}</b>, s.fullName, s.solved,
                c.canManage ? <Btn small kind="danger" onClick={() => void act(() => papi.removeStudent(id, s.id))}>Xoá khỏi lớp</Btn> : "",
              ])} />
          </Card>

          <Card title="Trợ giảng">
            {c.canManage && (
              <div style={{ marginBottom: 12 }}>
                <Row>
                  <Field label="Thêm trợ giảng (tên đăng nhập của một tài khoản giảng viên)">
                    <input style={INPUT} value={assistant} onChange={(e) => setAssistant(e.target.value)} />
                  </Field>
                  <Btn onClick={() => void act(async () => { await papi.addAssistant(id, assistant.trim()); setAssistant(""); })}>Thêm</Btn>
                </Row>
              </div>
            )}
            {(c.assistants ?? []).length === 0 && <Muted>Lớp chưa có trợ giảng.</Muted>}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {(c.assistants ?? []).map((a) => (
                <span key={a.id} style={{ display: "inline-flex", gap: 8, alignItems: "center", fontSize: 13 }}>
                  <Tag color="purple">{a.fullName} (@{a.username})</Tag>
                  {c.canManage && <Btn small kind="danger" onClick={() => void act(() => papi.removeAssistant(id, a.id))}>Bỏ</Btn>}
                </span>
              ))}
            </div>
          </Card>

          <Card title="Chat của lớp">
            <ChatBox channel={`class-${c.id}`} />
          </Card>
        </>
      )}
    </Page>
  );
}
