import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Row, Table, Tag, errorText, useLoad } from "../../shared/kit";

const ROLE_COLOR: Record<string, string> = { ADMIN: "red", TEACHER: "purple", STUDENT: "blue" };

/**
 * Module 1 (tai khoan va phan quyen) va 35 (giang vien, tro giang): xem moi tai khoan,
 * loc theo vai tro, tao tai khoan giang vien / hoc sinh / quan tri. Chi quan tri vien.
 */
export default function Accounts() {
  const users = useLoad(() => papi.users());
  const [role, setRole] = useState("");
  const [error, setError] = useState("");
  const [form, setForm] = useState({ role: "TEACHER", username: "", password: "", fullName: "", detail: "" });
  const shown = (users.data ?? []).filter((u) => role === "" || u.role === role);

  async function create() {
    setError("");
    try {
      await papi.createUser(form);
      setForm({ ...form, username: "", password: "", fullName: "", detail: "" });
      users.reload();
    } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Tài khoản và giảng viên">
      <ErrorBox text={error || users.error} />
      <Card title="Tạo tài khoản">
        <Row>
          <Field label="Vai trò">
            <select style={INPUT} value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              <option value="TEACHER">Giảng viên / trợ giảng</option>
              <option value="STUDENT">Học sinh</option>
              <option value="ADMIN">Quản trị viên</option>
            </select>
          </Field>
          <Field label="Tên đăng nhập"><input style={INPUT} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoComplete="off" /></Field>
          <Field label="Mật khẩu (ít nhất 8 ký tự)"><input type="password" style={INPUT} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} autoComplete="new-password" /></Field>
          <Field label="Họ tên"><input style={INPUT} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
          {form.role !== "ADMIN" && (
            <Field label={form.role === "TEACHER" ? "Bộ môn" : "Lớp"}><input style={INPUT} value={form.detail} onChange={(e) => setForm({ ...form, detail: e.target.value })} /></Field>
          )}
          <Btn onClick={() => void create()}>Tạo</Btn>
        </Row>
      </Card>

      <div style={{ display: "flex", gap: 8 }}>
        {[["", "Tất cả"], ["STUDENT", "Học sinh"], ["TEACHER", "Giảng viên"], ["ADMIN", "Quản trị"]].map(([key, label]) => (
          <Btn key={key} small kind={role === key ? "primary" : "ghost"} onClick={() => setRole(key)}>{label}</Btn>
        ))}
      </div>
      <Table head={["Tên đăng nhập", "Họ tên", "Vai trò", "Lớp / bộ môn"]}
        rows={shown.map((u) => [<b>{u.username}</b>, u.fullName, <Tag color={ROLE_COLOR[u.role]}>{u.roleName}</Tag>, u.detail || "—"])} />
    </Page>
  );
}
