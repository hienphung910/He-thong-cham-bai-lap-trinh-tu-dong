import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Row, SimNote, TEXTAREA, errorText, useLoad } from "../../shared/kit";

function Lines({ lines }: { lines: string[] }) {
  return (
    <ul style={{ margin: "10px 0 0", paddingLeft: 18, fontSize: 13.5, color: "#424242", lineHeight: 1.7 }}>
      {lines.map((l, i) => <li key={i}>{l}</li>)}
    </ul>
  );
}

/**
 * Module 27 (tro ly AI cho giang vien) - ban mo phong theo luat: tom tat tinh hinh lop,
 * soan nhan xet cho tung hoc sinh. Kem form gui thong bao cho lop (Module 29).
 */
export default function TeachingAssistant() {
  const classes = useLoad(() => papi.classrooms());
  const managed = (classes.data ?? []).filter((c) => c.canManage);
  const [classroomId, setClassroomId] = useState("");
  const [summary, setSummary] = useState<string[] | null>(null);
  const [username, setUsername] = useState("");
  const [comment, setComment] = useState<string[] | null>(null);
  const [notice, setNotice] = useState({ title: "", body: "" });
  const [sent, setSent] = useState("");
  const [error, setError] = useState("");

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Trợ lý giảng dạy" subtitle="Tóm tắt tình hình lớp và soạn nhận xét cho học sinh.">
      <SimNote>Các nhận xét được ghép từ số liệu nộp bài theo luật viết sẵn, không gọi mô hình AI nào.</SimNote>
      <ErrorBox text={error || classes.error} />

      <Card title="Tóm tắt một lớp">
        {managed.length === 0 && <Muted>Bạn chưa phụ trách lớp nào.</Muted>}
        <Row>
          <Field label="Lớp">
            <select style={INPUT} value={classroomId} onChange={(e) => { setClassroomId(e.target.value); setSummary(null); }}>
              <option value="">(chọn lớp)</option>
              {managed.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Btn disabled={!classroomId} onClick={() => void act(async () => setSummary((await papi.classSummary(classroomId)).lines))}>Tóm tắt</Btn>
        </Row>
        {summary && <Lines lines={summary} />}
      </Card>

      <Card title="Nhận xét một học sinh">
        <Row>
          <Field label="Tên đăng nhập của học sinh"><input style={INPUT} value={username} onChange={(e) => setUsername(e.target.value)} placeholder="ví dụ hiennm" /></Field>
          <Btn disabled={!username.trim()} onClick={() => void act(async () => setComment((await papi.studentComment(username.trim())).lines))}>Soạn nhận xét</Btn>
        </Row>
        {comment && <Lines lines={comment} />}
      </Card>

      <Card title="Gửi thông báo cho lớp đang chọn">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Field label="Tiêu đề"><input style={INPUT} value={notice.title} onChange={(e) => setNotice({ ...notice, title: e.target.value })} /></Field>
          <Field label="Nội dung"><textarea style={TEXTAREA} value={notice.body} onChange={(e) => setNotice({ ...notice, body: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <Btn disabled={!classroomId} onClick={() => void act(async () => {
              const result = await papi.announce({ classroomId, ...notice });
              setSent(`Đã gửi tới ${result.sent} học sinh.`);
              setNotice({ title: "", body: "" });
            })}>Gửi thông báo</Btn>
            {!classroomId && <Muted>Chọn lớp ở mục "Tóm tắt một lớp" trước.</Muted>}
            {sent && <span style={{ fontSize: 13, color: "#2E7D32" }}>{sent}</span>}
          </div>
        </div>
      </Card>
    </Page>
  );
}
