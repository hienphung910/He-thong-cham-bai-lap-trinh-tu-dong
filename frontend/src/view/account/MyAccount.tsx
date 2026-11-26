import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Row, Tag, errorText } from "../../shared/kit";
import { useAuth } from "./auth";

/** Module 1 (tai khoan): thong tin tai khoan dang dang nhap va doi mat khau. */
export default function MyAccount() {
  const { user, refresh } = useAuth();
  // Dang xuat moi phien khac cua tai khoan (vd. quen dang xuat o may tinh phong lab), giu phien dang dung.
  const [othersError, setOthersError] = useState("");
  const [othersDone, setOthersDone] = useState(false);

  async function logoutOthers() {
    setOthersError("");
    setOthersDone(false);
    try {
      await papi.logoutOthers();
      setOthersDone(true);
    } catch (e) {
      setOthersError(errorText(e));
    }
  }

  // Email khoi phuc: doi phai nhap mat khau hien tai, vi ai giu email la dat lai duoc mat khau.
  const [email, setEmail] = useState(user?.email ?? "");
  const [emailPassword, setEmailPassword] = useState("");
  const [emailError, setEmailError] = useState("");
  /** Thong bao sau khi luu email / gui lai thu xac minh. */
  const [emailDone, setEmailDone] = useState("");

  async function saveEmail() {
    setEmailError("");
    setEmailDone("");
    try {
      const res = await papi.changeEmail(emailPassword, email.trim());
      setEmailPassword("");
      // Email moi chi co hieu luc sau khi bam lien ket trong thu xac minh (neu may chu gui duoc thu).
      setEmailDone(res.pendingEmail ? `Đã gửi thư xác minh tới ${res.pendingEmail}.` : "Đã lưu email.");
      await refresh();
    } catch (e) {
      setEmailError(errorText(e));
    }
  }

  async function resendVerification() {
    setEmailError("");
    setEmailDone("");
    try {
      await papi.resendEmailVerification();
      setEmailDone(`Đã gửi lại thư xác minh tới ${user?.pendingEmail ?? ""}.`);
    } catch (e) {
      setEmailError(errorText(e));
    }
  }

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
          <div>Email khôi phục: {user.email ? <b>{user.email}</b> : <span style={{ color: "#C62828" }}>{user.pendingEmail ? "chưa xác minh" : "chưa khai"}</span>}</div>
          {user.pendingEmail && (
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <span>Chờ xác minh: <b>{user.pendingEmail}</b></span>
              <Tag color="orange">Chưa xác minh</Tag>
              <Btn small kind="ghost" onClick={() => void resendVerification()}>Gửi lại thư xác minh</Btn>
            </div>
          )}
        </div>
      </Card>
      <Card title="Email khôi phục mật khẩu">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Row>
            <Field label="Email"><input type="email" style={INPUT} value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" /></Field>
            <Field label="Mật khẩu hiện tại"><input type="password" style={INPUT} value={emailPassword} onChange={(e) => setEmailPassword(e.target.value)} autoComplete="current-password" /></Field>
            <Btn disabled={!email.trim() || !emailPassword || email.trim().toLowerCase() === user.email} onClick={() => void saveEmail()}>Lưu email</Btn>
          </Row>
          <ErrorBox text={emailError} />
          {emailDone && <div style={{ fontSize: 13, color: "#2E7D32" }}>{emailDone}</div>}
        </div>
      </Card>
      <Card title="Đổi mật khẩu">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Row>
            <Field label="Mật khẩu hiện tại"><input type="password" style={INPUT} value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} autoComplete="current-password" /></Field>
            <Field label="Mật khẩu mới (ít nhất 8 ký tự, có cả chữ và số)"><input type="password" style={INPUT} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" /></Field>
            <Btn disabled={!oldPassword || !newPassword} onClick={() => void change()}>Đổi mật khẩu</Btn>
          </Row>
          <ErrorBox text={error} />
          {done && <div style={{ fontSize: 13, color: "#2E7D32" }}>Đã đổi mật khẩu.</div>}
        </div>
      </Card>
      <Card title="Thiết bị đăng nhập">
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <Row>
            <Btn kind="ghost" onClick={() => void logoutOthers()}>Đăng xuất các thiết bị khác</Btn>
          </Row>
          <ErrorBox text={othersError} />
          {othersDone && <div style={{ fontSize: 13, color: "#2E7D32" }}>Đã đăng xuất các thiết bị khác.</div>}
        </div>
      </Card>
    </Page>
  );
}
