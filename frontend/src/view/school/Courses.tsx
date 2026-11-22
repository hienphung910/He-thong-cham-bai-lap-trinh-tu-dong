import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Row, TEXTAREA, Tag, errorText, fmtTime, useLoad } from "../../shared/kit";
import { parseIds } from "../../shared/nav";
import ProblemChip from "../../shared/ProblemChip";
import { useAuth } from "../account/auth";

/**
 * Module 4 (khoa hoc, giao trinh) va 36 (tai lieu hoc tap).
 * Moi khoa hoc co giao trinh la danh sach bai giang; moi bai giang kem cac bai tap luyen.
 */
export default function Courses() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const courses = useLoad(() => papi.courses());
  const docs = useLoad(() => papi.documents());
  const [error, setError] = useState("");
  const [course, setCourse] = useState({ title: "", description: "", level: "Cơ bản" });
  const [lesson, setLesson] = useState({ courseId: "", title: "", content: "", problems: "" });
  const [doc, setDoc] = useState({ courseId: "", title: "", kind: "TEXT", content: "" });

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); courses.reload(); docs.reload(); } catch (e) { setError(errorText(e)); }
  }

  const courseOptions = (courses.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.title}</option>);

  return (
    <Page title="Khóa học và tài liệu" subtitle="Giáo trình từng khóa học và tài liệu học tập kèm theo.">
      <ErrorBox text={error || courses.error || docs.error} />

      {isStaff && (
        <Card title="Quản lý khóa học">
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <Row>
              <Field label="Tên khóa học mới"><input style={INPUT} value={course.title} onChange={(e) => setCourse({ ...course, title: e.target.value })} /></Field>
              <Field label="Trình độ"><input style={INPUT} value={course.level} onChange={(e) => setCourse({ ...course, level: e.target.value })} /></Field>
              <Field label="Mô tả"><input style={INPUT} value={course.description} onChange={(e) => setCourse({ ...course, description: e.target.value })} /></Field>
              <Btn onClick={() => void act(async () => { await papi.createCourse(course); setCourse({ title: "", description: "", level: "Cơ bản" }); })}>Tạo khóa học</Btn>
            </Row>
            <Row>
              <Field label="Thêm bài giảng vào khóa">
                <select style={INPUT} value={lesson.courseId} onChange={(e) => setLesson({ ...lesson, courseId: e.target.value })}>
                  <option value="">(chọn khóa học)</option>{courseOptions}
                </select>
              </Field>
              <Field label="Tên bài giảng"><input style={INPUT} value={lesson.title} onChange={(e) => setLesson({ ...lesson, title: e.target.value })} /></Field>
              <Field label="Bài tập kèm theo (mã bài, cách nhau dấu phẩy)"><input style={INPUT} value={lesson.problems} onChange={(e) => setLesson({ ...lesson, problems: e.target.value })} placeholder="K001, K002" /></Field>
            </Row>
            <Row>
              <Field label="Nội dung bài giảng"><textarea style={TEXTAREA} value={lesson.content} onChange={(e) => setLesson({ ...lesson, content: e.target.value })} /></Field>
              <Btn disabled={!lesson.courseId} onClick={() => void act(async () => {
                await papi.addLesson(lesson.courseId, { title: lesson.title, content: lesson.content, problemIds: parseIds(lesson.problems) });
                setLesson({ ...lesson, title: "", content: "", problems: "" });
              })}>Thêm bài giảng</Btn>
            </Row>
          </div>
        </Card>
      )}

      {courses.data?.length === 0 && <Muted>Chưa có khóa học nào.</Muted>}
      {(courses.data ?? []).map((c) => (
        <Card key={c.id} title={c.title} right={<>
          {c.level && <Tag color="blue">{c.level}</Tag>}
          {isStaff && <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá khóa học "${c.title}"?`)) void act(() => papi.deleteCourse(c.id)); }}>Xoá</Btn>}
        </>}>
          <div style={{ fontSize: 13, color: "#616161", marginBottom: 10 }}>{c.description}</div>
          {c.lessons.length === 0 && <Muted>Giáo trình chưa có bài giảng.</Muted>}
          <ol style={{ margin: 0, paddingLeft: 20, display: "flex", flexDirection: "column", gap: 10 }}>
            {c.lessons.map((l, i) => (
              <li key={i} style={{ fontSize: 13.5, color: "#212121" }}>
                <b>{l.title}</b>
                <div style={{ fontSize: 13, color: "#616161", whiteSpace: "pre-wrap", margin: "2px 0 6px" }}>{l.content}</div>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {l.problems.map((p) => <ProblemChip key={p.id} id={p.id} title={p.title} />)}
                </div>
              </li>
            ))}
          </ol>
        </Card>
      ))}

      <Card title="Tài liệu học tập">
        {isStaff && (
          <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 14 }}>
            <Row>
              <Field label="Tên tài liệu"><input style={INPUT} value={doc.title} onChange={(e) => setDoc({ ...doc, title: e.target.value })} /></Field>
              <Field label="Thuộc khóa học">
                <select style={INPUT} value={doc.courseId} onChange={(e) => setDoc({ ...doc, courseId: e.target.value })}>
                  <option value="">(tài liệu chung)</option>{courseOptions}
                </select>
              </Field>
              <Field label="Loại">
                <select style={INPUT} value={doc.kind} onChange={(e) => setDoc({ ...doc, kind: e.target.value })}>
                  <option value="TEXT">Văn bản</option>
                  <option value="LINK">Đường link</option>
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={doc.kind === "LINK" ? "Địa chỉ (http:// hoặc https://)" : "Nội dung"}>
                <textarea style={TEXTAREA} value={doc.content} onChange={(e) => setDoc({ ...doc, content: e.target.value })} />
              </Field>
              <Btn onClick={() => void act(async () => { await papi.addDocument(doc); setDoc({ ...doc, title: "", content: "" }); })}>Thêm tài liệu</Btn>
            </Row>
          </div>
        )}
        {docs.data?.length === 0 && <Muted>Chưa có tài liệu nào.</Muted>}
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {(docs.data ?? []).map((d) => (
            <div key={d.id} style={{ border: "1px solid #F0F0F0", borderRadius: 6, padding: "10px 14px" }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <b style={{ fontSize: 14, color: "#212121" }}>{d.title}</b>
                <Tag color={d.kind === "LINK" ? "purple" : "gray"}>{d.kind === "LINK" ? "Link" : "Văn bản"}</Tag>
                {d.courseTitle && <Tag color="blue">{d.courseTitle}</Tag>}
                <span style={{ fontSize: 11.5, color: "#9E9E9E" }}>{d.authorName} · {fmtTime(d.createdAt)}</span>
                <span style={{ flex: 1 }} />
                {isStaff && <Btn small kind="danger" onClick={() => void act(() => papi.deleteDocument(d.id))}>Xoá</Btn>}
              </div>
              {d.kind === "LINK"
                ? <a href={d.content} target="_blank" rel="noopener noreferrer" style={{ fontSize: 13, color: "#1565C0", wordBreak: "break-all" }}>{d.content}</a>
                : <div style={{ fontSize: 13, color: "#424242", whiteSpace: "pre-wrap", marginTop: 4 }}>{d.content}</div>}
            </div>
          ))}
        </div>
      </Card>
    </Page>
  );
}
