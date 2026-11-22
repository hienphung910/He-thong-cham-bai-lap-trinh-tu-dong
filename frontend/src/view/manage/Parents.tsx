import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Modal, Page, Row, Table, errorText, useLoad } from "../../shared/kit";
import type { ParentReport } from "../../shared/platformTypes";

/**
 * Module 32 (quan ly phu huynh): luu thong tin lien he cua phu huynh tung hoc sinh va
 * soan bao cao hoc tap de gui cho phu huynh. Phu huynh khong co tai khoan dang nhap.
 */
export default function Parents() {
  const list = useLoad(() => papi.parents());
  const [form, setForm] = useState({ studentUsername: "", fullName: "", phone: "", email: "" });
  const [report, setReport] = useState<ParentReport | null>(null);
  const [error, setError] = useState("");

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Phụ huynh" subtitle="Danh bạ phụ huynh và báo cáo học tập gửi về gia đình.">
      <ErrorBox text={error || list.error} />
      <Card title="Thêm phụ huynh">
        <Row>
          <Field label="Tên đăng nhập của học sinh"><input style={INPUT} value={form.studentUsername} onChange={(e) => setForm({ ...form, studentUsername: e.target.value })} /></Field>
          <Field label="Họ tên phụ huynh"><input style={INPUT} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
          <Field label="Số điện thoại"><input style={INPUT} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
          <Field label="Email"><input style={INPUT} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
          <Btn onClick={() => void act(async () => { await papi.addParent(form); setForm({ studentUsername: "", fullName: "", phone: "", email: "" }); })}>Thêm</Btn>
        </Row>
      </Card>
      <Table head={["Phụ huynh", "Điện thoại", "Email", "Con", ""]} empty="Chưa có phụ huynh nào."
        rows={(list.data ?? []).map((p) => [
          <b>{p.fullName}</b>, p.phone, p.email || "—",
          <span>{p.studentName} <span style={{ color: "#9E9E9E" }}>@{p.studentUsername}</span></span>,
          <div style={{ display: "flex", gap: 6 }}>
            <Btn small onClick={() => void act(async () => setReport(await papi.parentReport(p.id)))}>Báo cáo học tập</Btn>
            <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá phụ huynh "${p.fullName}"?`)) void act(() => papi.deleteParent(p.id)); }}>Xoá</Btn>
          </div>,
        ])} />

      {report && (
        <Modal title="Báo cáo học tập gửi phụ huynh" onClose={() => setReport(null)}>
          <div style={{ fontSize: 14, lineHeight: 1.75, color: "#424242" }}>
            <p style={{ margin: "0 0 8px" }}>Kính gửi anh/chị <b>{report.parentName}</b>,</p>
            <p style={{ margin: "0 0 8px" }}>Trung tâm xin gửi tình hình học tập của em <b>{report.studentName}</b>:</p>
            <ul style={{ margin: 0, paddingLeft: 18 }}>{report.lines.map((l, i) => <li key={i}>{l}</li>)}</ul>
          </div>
          <div><Btn kind="ghost" onClick={() => void navigator.clipboard.writeText(
            `Kính gửi anh/chị ${report.parentName},\nTình hình học tập của em ${report.studentName}:\n- ${report.lines.join("\n- ")}`)}>Chép nội dung</Btn></div>
        </Modal>
      )}
    </Page>
  );
}
