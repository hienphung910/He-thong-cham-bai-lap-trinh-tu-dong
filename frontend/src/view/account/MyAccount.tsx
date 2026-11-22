import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Row, Tag, errorText } from "../../shared/kit";
import { useAuth } from "./auth";

/** Module 1 (tai khoan): thong tin tai khoan dang dang nhap va doi mat khau. */
export default function MyAccount() {
  const { user } = useAuth();
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function change() {
    setError("");
    setDone(false);
    try {
      await papi.changePassword(oldPassword, newPassword);
      setOldPassword("");
      setNewPassword("");
      setDone(true);
    } catch (e) {
      setError(errorText(e));
    }
  }

  if (!user) return null;
  return (
    <Page title="Tài khoản của tôi">
      <Card title="Thông tin">
        <div style={{ fontSize: 14, lineHeight: 1.9, color: "#424242" }}>
          <div>Họ tên: <b>{user.fullName}</b></div>
          <div>Tên đăng nhập: <b>@{user.username}</b></div>
          <div>Vai trò: <Tag color={user.role === "ADMIN" ? "red" : user.role === "TEACHER" ? "purple" : "blue"}>{user.roleName}</Tag></div>
        </div>
      </Card>
      <Card title="Đổi mật khẩu">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Row>
            <Field label="Mật khẩu hiện tại"><input type="password" style={INPUT} value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} autoComplete="current-password" /></Field>
            <Field label="Mật khẩu mới (ít nhất 8 ký tự)"><input type="password" style={INPUT} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" /></Field>
            <Btn disabled={!oldPassword || !newPassword} onClick={() => void change()}>Đổi mật khẩu</Btn>
          </Row>
          <ErrorBox text={error} />
          {done && <div style={{ fontSize: 13, color: "#2E7D32" }}>Đã đổi mật khẩu.</div>}
        </div>
      </Card>
    </Page>
  );
}
