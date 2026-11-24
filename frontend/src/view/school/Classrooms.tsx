import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { parseIds, useBackState } from "../../shared/nav";
import { Btn, Card, ErrorBox, Field, Grid, INPUT, Muted, Page, Row, Table, Tag, errorText, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";
import ChatBox from "../community/ChatBox";
import Analytics from "../profile/Analytics";
import { DIFFICULTY_META } from "../problem/learningPath";
import type { Classroom } from "../../shared/platformTypes";

/** Khung noi bat hien ma tham gia cua lop, de giang vien doc / chep cho hoc sinh. */
function JoinCode({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      // Trinh duyet chan clipboard: nguoi dung van doc va go lai ma duoc.
    }
  }
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
      <span style={{
        fontFamily: "var(--font-mono)", fontSize: 15, fontWeight: 700, letterSpacing: "0.12em",
        padding: "3px 10px", borderRadius: 6, background: "#111111", color: "var(--color-accent)",
      }}>{code}</span>
      <Btn small kind="ghost" onClick={() => void copy()}>{copied ? "Đã chép" : "Chép mã"}</Btn>
    </span>
  );
}

/**
 * Module 3 (lop hoc) va 35 (tro giang): danh sach lop cua toi. Giang vien tao lop - moi lop
 * co mot ma tham gia de dua cho hoc sinh; hoc sinh nhap ma do de vao lop. Trong lop co bai
 * luyen tap giang vien chon rieng cho lop, danh sach thanh vien va chat rieng cua lop.
 */
export default function Classrooms() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const list = useLoad(() => papi.classrooms());
  const orgs = useLoad(() => papi.organizations());
  // Lop dang mo nam trong lich su trinh duyet: nut Back quay ve danh sach lop.
  const [openId, setOpenId] = useBackState<string | null>("classroom", null);
  const [error, setError] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [form, setForm] = useState({ name: "", organizationId: "" });
  /** Lop vua tao xong, de hien ngay ma tham gia cho giang vien. */
  const [created, setCreated] = useState<Classroom | null>(null);

  /** Chay mot thao tac ghi, bao loi neu co, xong thi tai lai danh sach. */
  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <ClassroomDetail id={openId} onBack={() => { setOpenId(null); list.reload(); }} />;

  return (
    <Page title="Lớp của tôi">
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
            <Btn disabled={!form.name.trim()} onClick={() => void act(async () => {
              setCreated(await papi.createClassroom({ ...form, courseId: "" }));
              setForm({ name: "", organizationId: "" });
            })}>Tạo lớp</Btn>
          </Row>
          {created && (
            <div style={{ marginTop: 12, display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", fontSize: 13.5 }}>
              Đã tạo lớp <b>{created.name}</b>. Mã tham gia để gửi cho học sinh: <JoinCode code={created.joinCode} />
            </div>
          )}
        </Card>
      ) : (
        <Card title="Tham gia lớp">
          <Row>
            <Field label="Mã tham gia">
              <input style={{ ...INPUT, fontFamily: "var(--font-mono)", letterSpacing: "0.1em", textTransform: "uppercase" }}
                value={joinCode} maxLength={8} onChange={(e) => setJoinCode(e.target.value)} placeholder="VD: 7W4DCU9G" />
            </Field>
            <Btn disabled={!joinCode.trim()} onClick={() => void act(async () => { await papi.joinClassroom(joinCode.trim()); setJoinCode(""); })}>Tham gia</Btn>
          </Row>
        </Card>
      )}

      {list.data?.length === 0 && <Muted>Bạn chưa thuộc lớp nào.</Muted>}
      <Grid min={280}>
        {(list.data ?? []).map((c) => (
          <Card key={c.id} title={c.name} right={<Tag color="gray">{c.id}</Tag>}>
            <div style={{ fontSize: 13, color: "#616161", lineHeight: 1.7 }}>
              <div>Giảng viên: <b>{c.teacherName}</b></div>
              <div>Tổ chức: {c.organizationName || "—"}</div>
              <div>Sĩ số: {c.studentCount} học sinh · {c.practiceCount} bài luyện tập</div>
              {c.joinCode && <div style={{ marginTop: 6 }}>Mã tham gia: <JoinCode code={c.joinCode} /></div>}
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
  const [problemIds, setProblemIds] = useState("");
  /** Hoc sinh dang duoc xem phan tich hoc tap (ten dang nhap); null = dang o trang lop. */
  const [viewing, setViewing] = useBackState<string | null>("classStudent", null);
  const c: Classroom | null = detail.data;

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); detail.reload(); } catch (e) { setError(errorText(e)); }
  }

  async function removeClass() {
    if (!window.confirm("Xoá lớp này? Bài tập giao, bài luyện tập và lịch học của lớp cũng bị xoá.")) return;
    setError("");
    try { await papi.deleteClassroom(id); onBack(); } catch (e) { setError(errorText(e)); }
  }

  if (viewing) return <Analytics username={viewing} onBack={() => setViewing(null)} />;

  const practice = c?.practice ?? [];
  const solved = practice.filter((p) => p.solved).length;

  return (
    <Page title={c ? c.name : "Lớp học"} subtitle={c ? `Mã lớp ${c.id} · Giảng viên ${c.teacherName}` : ""}
      actions={<>
        {c?.canManage && <Btn kind="danger" onClick={() => void removeClass()}>Xoá lớp</Btn>}
        <Btn kind="ghost" onClick={onBack}>← Danh sách lớp</Btn>
      </>}>
      <ErrorBox text={error || detail.error} />
      {c && (
        <>
          {c.joinCode && (
            <Card title="Mã tham gia lớp">
              <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap", fontSize: 13.5 }}>
                <JoinCode code={c.joinCode} />
              </div>
            </Card>
          )}

          <Card title={`Bài tập luyện tập (${practice.length})`}
            right={practice.length > 0 && !c.canManage ? <Tag color={solved === practice.length ? "green" : "blue"}>Đã giải {solved}/{practice.length}</Tag> : undefined}>
            {c.canManage && (
              <div style={{ marginBottom: 12 }}>
                <Row>
                  <Field label="Thêm bài cho lớp (mã bài, cách nhau dấu phẩy)">
                    <input style={{ ...INPUT, width: 360 }} value={problemIds} onChange={(e) => setProblemIds(e.target.value)} placeholder="K001, K002, V010" />
                  </Field>
                  <Btn disabled={!problemIds.trim()} onClick={() => void act(async () => {
                    await papi.addPracticeProblems(id, parseIds(problemIds));
                    setProblemIds("");
                  })}>Thêm bài</Btn>
                </Row>
              </div>
            )}
            {practice.length === 0 && <Muted>Lớp chưa có bài luyện tập.</Muted>}
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {practice.map((p) => {
                const diff = DIFFICULTY_META[p.difficulty];
                return (
                  <div key={p.id} style={{ display: "flex", gap: 10, alignItems: "center", fontSize: 13.5, flexWrap: "wrap" }}>
                    <ProblemChip id={p.id} title={p.title} done={p.solved} />
                    <b>{p.title}</b>
                    {diff && <span style={{ fontSize: 11, fontWeight: 700, padding: "1px 8px", borderRadius: 10, color: diff.color, background: diff.bg }}>{diff.label}</span>}
                    {c.canManage && (
                      <Btn small kind="danger" onClick={() => void act(() => papi.removePracticeProblem(id, p.id))}>Bỏ khỏi lớp</Btn>
                    )}
                  </div>
                );
              })}
            </div>
          </Card>

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
                c.canManage
                  ? <button onClick={() => setViewing(s.username)} title="Xem phân tích học tập" style={{
                      background: "none", border: "none", padding: 0, cursor: "pointer", font: "inherit",
                      fontWeight: 700, color: "var(--color-red)", textDecoration: "underline",
                    }}>{s.username}</button>
                  : <b>{s.username}</b>,
                s.fullName, s.solved,
                c.canManage ? (
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    <Btn small kind="ghost" onClick={() => setViewing(s.username)}>Xem phân tích</Btn>
                    <Btn small kind="danger" onClick={() => void act(() => papi.removeStudent(id, s.id))}>Xoá khỏi lớp</Btn>
                  </div>
                ) : "",
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
