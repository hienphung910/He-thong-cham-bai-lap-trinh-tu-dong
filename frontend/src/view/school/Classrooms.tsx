import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { useBackState } from "../../shared/nav";
import { Btn, Card, ErrorBox, Field, Grid, INPUT, Muted, Page, Row, Table, Tag, errorText, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";
import ChatBox from "../community/ChatBox";
import Analytics from "../profile/Analytics";
import { DIFFICULTY_META } from "../problem/learningPath";
import ProblemPicker from "../problem/ProblemPicker";
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
 * Module 3 (lop hoc): danh sach lop cua toi. Giang vien tao lop - moi lop
 * co mot ma tham gia de dua cho hoc sinh; hoc sinh nhap ma do de GUI YEU CAU vao lop, giang vien
 * duyet thi moi thanh thanh vien. Trong lop co bai
 * luyen tap giang vien chon rieng cho lop, danh sach thanh vien va chat rieng cua lop.
 */
export default function Classrooms() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const list = useLoad(() => papi.classrooms());
  const orgs = useLoad(() => papi.organizations());
  // Loi moi vao lop dang cho minh dong y (giang vien moi bang ten dang nhap).
  const invites = useLoad(() => papi.classroomInvites());
  // Yeu cau vao lop minh da gui (nhap ma tham gia) va giang vien chua duyet.
  const requests = useLoad(() => papi.classroomRequests());
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
    try { await work(); list.reload(); invites.reload(); requests.reload(); } catch (e) { setError(errorText(e)); }
  }

  if (openId) return <ClassroomDetail id={openId} onBack={() => { setOpenId(null); list.reload(); }} />;

  return (
    <Page title="Lớp của tôi">
      <ErrorBox text={error || list.error} />
      {(invites.data ?? []).length > 0 && (
        <Card title="Lời mời vào lớp">
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {(invites.data ?? []).map((inv) => (
              <div key={inv.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", fontSize: 13.5 }}>
                <span style={{ flex: 1, minWidth: 220 }}><b>{inv.name}</b> · giảng viên {inv.teacherName}</span>
                <Btn small onClick={() => void act(() => papi.acceptInvite(inv.id))}>Đồng ý</Btn>
                <Btn small kind="ghost" onClick={() => void act(() => papi.declineInvite(inv.id))}>Từ chối</Btn>
              </div>
            ))}
          </div>
        </Card>
      )}
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
            <Btn disabled={!joinCode.trim()} onClick={() => void act(async () => { await papi.joinClassroom(joinCode.trim()); setJoinCode(""); })}>Gửi yêu cầu</Btn>
          </Row>
        </Card>
      )}
      {(requests.data ?? []).length > 0 && (
        <Card title="Yêu cầu vào lớp đang chờ duyệt">
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {(requests.data ?? []).map((r) => (
              <div key={r.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", fontSize: 13.5 }}>
                <span style={{ flex: 1, minWidth: 220 }}><b>{r.name}</b> · giảng viên {r.teacherName}</span>
                <Tag color="orange">Chờ duyệt</Tag>
                <Btn small kind="ghost" onClick={() => void act(() => papi.cancelJoinRequest(r.id))}>Huỷ yêu cầu</Btn>
              </div>
            ))}
          </div>
        </Card>
      )}

      {list.data?.length === 0 && <Muted>Bạn chưa thuộc lớp nào.</Muted>}
      <Grid min={280}>
        {(list.data ?? []).map((c) => (
          <Card key={c.id} title={c.name} right={<span style={{ display: "inline-flex", gap: 6 }}>
            {c.requestCount > 0 && <Tag color="orange">{c.requestCount} yêu cầu chờ duyệt</Tag>}
            <Tag color="gray">{c.id}</Tag>
          </span>}>
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
  /** Dang mo bang chon bai luyen tap (bai chi duoc chon tu bang, khong go ma bai). */
  const [picking, setPicking] = useState(false);
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
        {c?.isOwner && <Btn kind="danger" onClick={() => void removeClass()}>Xoá lớp</Btn>}
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

          {c.canManage && (c.requests ?? []).length > 0 && (
            <Card title={`Yêu cầu vào lớp (${(c.requests ?? []).length})`}>
              <Table head={["Tên đăng nhập", "Họ tên", "Lớp", ""]}
                rows={(c.requests ?? []).map((u) => [
                  <b>{u.username}</b>, u.fullName, u.detail || "—",
                  <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                    <Btn small onClick={() => void act(() => papi.approveJoinRequest(id, u.id))}>Duyệt</Btn>
                    <Btn small kind="danger" onClick={() => void act(() => papi.rejectJoinRequest(id, u.id))}>Từ chối</Btn>
                  </div>,
                ])} />
            </Card>
          )}

          <Card title={`Bài tập luyện tập (${practice.length})`}
            right={practice.length > 0 && !c.canManage ? <Tag color={solved === practice.length ? "green" : "blue"}>Đã giải {solved}/{practice.length}</Tag> : undefined}>
            {c.canManage && (
              <div style={{ marginBottom: 12 }}>
                <Btn small kind="ghost" onClick={() => setPicking(true)}>Thêm bài từ danh sách</Btn>
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

          {picking && (
            <ProblemPicker title={`Chọn bài luyện tập — ${c.name}`} excluded={practice.map((p) => p.id)}
              onClose={() => setPicking(false)}
              onPick={(ids) => { setPicking(false); void act(() => papi.addPracticeProblems(id, ids)); }} />
          )}

          <Card title={`Học sinh (${c.students?.length ?? 0})`}>
            {c.canManage && (
              <div style={{ marginBottom: 12 }}>
                <Row>
                  <Field label="Mời học sinh theo tên đăng nhập">
                    <input style={INPUT} value={student} onChange={(e) => setStudent(e.target.value)} placeholder="ví dụ hiennm" />
                  </Field>
                  <Btn disabled={!student.trim()} onClick={() => void act(async () => { await papi.addStudent(id, student.trim()); setStudent(""); })}>Mời</Btn>
                </Row>
                {(c.invited ?? []).length > 0 && (
                  <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 10, fontSize: 13 }}>
                    <span style={{ color: "#757575" }}>Đã mời, chờ đồng ý:</span>
                    {(c.invited ?? []).map((u) => (
                      <span key={u.id} style={{ display: "inline-flex", gap: 4, alignItems: "center" }}>
                        <Tag color="orange">{u.fullName} (@{u.username})</Tag>
                        {c.isOwner && (
                          <button title="Rút lời mời" onClick={() => void act(() => papi.removeStudent(id, u.id))}
                            style={{ border: "none", background: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 15, padding: "0 2px" }}>×</button>
                        )}
                      </span>
                    ))}
                  </div>
                )}
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
                    {c.isOwner && <Btn small kind="danger" onClick={() => void act(() => papi.removeStudent(id, s.id))}>Xoá khỏi lớp</Btn>}
                  </div>
                ) : "",
              ])} />
          </Card>

          <Card title="Chat của lớp">
            <ChatBox channel={`class-${c.id}`} />
          </Card>
        </>
      )}
    </Page>
  );
}
