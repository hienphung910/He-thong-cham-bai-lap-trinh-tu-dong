import Logo from "../../shared/Logo";
import { useRef, useState } from "react";
import { useAuth } from "./auth";
import { EVIDENCE_ACCEPT, readEvidence } from "./evidence";

type Mode = "login" | "register";

/**
 * Man hinh dang nhap / dang ky (LoginView + RegisterView trong bao cao).
 * Mot thanh phan, hai che do: "login" va "register". App.tsx hien thanh phan nay
 * khi chua co phien dang nhap; dang nhap xong thi AuthProvider cap nhat `user`
 * va App tu chuyen sang giao dien chinh.
 */
export default function LoginModal() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fullName, setFullName] = useState("");
  const [studentCode, setStudentCode] = useState("");
  const [className, setClassName] = useState("");
  // Vai tro chon khi dang ky: hoc sinh hoac giang vien (quan tri vien khong tu dang ky duoc).
  const [role, setRole] = useState<"STUDENT" | "TEACHER">("STUDENT");
  const [department, setDepartment] = useState("");
  // Minh chung giang vien gui kem de quan tri vien xem khi duyet: ten file va noi dung base64.
  const [evidence, setEvidence] = useState<{ name: string; data: string } | null>(null);
  const evidenceInput = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [showPass, setShowPass] = useState(false);

  function switchMode(next: Mode) { setMode(next); setError(""); setNotice(""); setPassword(""); setConfirm(""); }

  async function pickEvidence(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";      // de chon lai dung file do van nhan duoc su kien
    if (!file) return;
    setError("");
    try { setEvidence({ name: file.name, data: await readEvidence(file) }); }
    catch (e2) { setEvidence(null); setError(e2 instanceof Error ? e2.message : String(e2)); }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setNotice("");
    if (mode === "login") {
      if (!username || !password) { setError("Hãy nhập tên đăng nhập và mật khẩu."); return; }
      setBusy(true);
      try { await login(username.trim(), password); }
      catch (e2) { setError(e2 instanceof Error ? e2.message : String(e2)); }
      finally { setBusy(false); }
      return;
    }
    if (password.length < 8) { setError("Mật khẩu phải có ít nhất 8 ký tự."); return; }
    if (password !== confirm) { setError("Hai lần nhập mật khẩu không khớp."); return; }
    if (role === "TEACHER" && !evidence) { setError("Hãy tải minh chứng lên trước khi đăng ký giảng viên."); return; }
    setBusy(true);
    try {
      const pending = await register({
        role, username: username.trim(), password, fullName: fullName.trim(),
        studentCode: studentCode.trim(), className: className.trim(), department: department.trim(),
        evidence: role === "TEACHER" && evidence ? evidence.data : "",
      });
      setEvidence(null);
      switchMode("login");
      setNotice(pending
        ? `Đã gửi đăng ký giảng viên @${username.trim()}. Tài khoản cần quản trị viên duyệt rồi mới đăng nhập được.`
        : `Đã tạo tài khoản học sinh @${username.trim()}. Mời bạn đăng nhập.`);
    } catch (e2) { setError(e2 instanceof Error ? e2.message : String(e2)); }
    finally { setBusy(false); }
  }

  const inputBox: React.CSSProperties = {
    width: "100%", height: 44, padding: "0 14px", background: "#fff",
    border: "1px solid #E0E0E0", borderRadius: 8, color: "#212121",
    fontSize: 14, outline: "none", boxSizing: "border-box",
  };

  return (
    <div style={{ height: "100%", display: "flex", background: "var(--color-page-bg)" }}>
      {/* Left panel */}
      <div style={{ flex: "0 0 42%", background: "#111111", display: "flex", flexDirection: "column", justifyContent: "center", padding: "0 60px" }}>
        <div style={{ marginBottom: 20 }}><Logo size={64} /></div>
        <p style={{ fontSize: 13, color: "var(--color-accent)", margin: 0, fontFamily: "var(--font-mono)" }}>{">_"} ready to judge</p>
      </div>
      {/* Right panel - inline form */}
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: 40 }}>
        <form onSubmit={handleSubmit} style={{ width: 400, display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ fontSize: 24, fontWeight: 700, color: "#212121", margin: 0 }}>
            {mode === "login" ? "Đăng nhập" : "Đăng ký tài khoản"}
          </h2>

          {notice && (
            <div style={{ padding: "10px 14px", background: "#E8F5E9", border: "1px solid #C8E6C9", borderRadius: 6, fontSize: 12.5, color: "#2E7D32" }}>{notice}</div>
          )}

          {/* Username */}
          <div style={{ display: "flex", alignItems: "center", gap: 8, ...inputBox, padding: 0, paddingLeft: 12 }}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1.4"><circle cx="8" cy="5" r="3" /><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" /></svg>
            <input type="text" placeholder="Tên đăng nhập" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus
              style={{ flex: 1, border: "none", outline: "none", fontSize: 14, height: "100%", background: "transparent" }} />
          </div>

          {mode === "register" && (
            <>
              {/* Chon vai tro: hai nut, nut dang chon to mau do */}
              <div style={{ display: "flex", gap: 8 }}>
                {([["STUDENT", "Học sinh"], ["TEACHER", "Giảng viên"]] as const).map(([value, label]) => (
                  <button key={value} type="button" onClick={() => setRole(value)} style={{
                    flex: 1, height: 40, borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer",
                    border: role === value ? "1px solid var(--color-accent-dark)" : "1px solid #E0E0E0",
                    background: role === value ? "var(--color-accent)" : "#fff",
                    color: role === value ? "var(--color-on-accent)" : "#616161",
                  }}>{label}</button>
                ))}
              </div>
              <input type="text" placeholder="Họ và tên" value={fullName} onChange={(e) => setFullName(e.target.value)} style={inputBox} />
              {role === "STUDENT" ? (
                <>
                  <input type="text" placeholder="Mã sinh viên" value={studentCode} onChange={(e) => setStudentCode(e.target.value)} style={inputBox} />
                  <input type="text" placeholder="Lớp" value={className} onChange={(e) => setClassName(e.target.value)} style={inputBox} />
                </>
              ) : (
                <>
                  <input type="text" placeholder="Bộ môn" value={department} onChange={(e) => setDepartment(e.target.value)} style={inputBox} />
                  {/* Minh chung la giang vien: quan tri vien xem file nay khi duyet tai khoan. */}
                  <input ref={evidenceInput} type="file" accept={EVIDENCE_ACCEPT} style={{ display: "none" }} onChange={(e) => void pickEvidence(e)} />
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <button type="button" onClick={() => evidenceInput.current?.click()} title="Ảnh PNG, JPG, WEBP hoặc file PDF, tối đa 1 MB" style={{
                      display: "inline-flex", alignItems: "center", gap: 8, height: 40, padding: "0 14px", borderRadius: 8, cursor: "pointer",
                      border: "1px dashed var(--color-accent-dark)", background: evidence ? "var(--color-red-light)" : "#fff",
                      color: "#424242", fontSize: 13.5, fontWeight: 600, flexShrink: 0,
                    }}>
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M8 10.5V2.5M4.8 5.5 8 2.3l3.2 3.2M2.5 10.5v2a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-2" /></svg>
                      {evidence ? "Đổi minh chứng" : "Tải minh chứng lên"}
                    </button>
                    {evidence && (
                      <>
                        <span title={evidence.name} style={{ flex: 1, minWidth: 0, fontSize: 12.5, color: "#2E7D32", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>✓ {evidence.name}</span>
                        <button type="button" onClick={() => setEvidence(null)} title="Gỡ minh chứng"
                          style={{ border: "none", background: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 18, lineHeight: 1, padding: "0 4px" }}>×</button>
                      </>
                    )}
                  </div>
                </>
              )}
            </>
          )}

          {/* Password */}
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, ...inputBox, padding: 0, paddingLeft: 12, paddingRight: 8 }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1.4"><rect x="3" y="7" width="10" height="7" rx="1.5" /><path d="M5 7V5a3 3 0 0 1 6 0v2" /></svg>
              <input type={showPass ? "text" : "password"} placeholder="Mật khẩu" value={password} onChange={(e) => setPassword(e.target.value)}
                style={{ flex: 1, border: "none", outline: "none", fontSize: 14, height: "100%", background: "transparent" }} />
              <button type="button" onClick={() => setShowPass((v) => !v)} style={{ background: "none", border: "none", cursor: "pointer", color: "#BDBDBD", display: "flex", padding: 4 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M1 8s3-5 7-5 7 5 7 5-3 5-7 5-7-5-7-5z" /><circle cx="8" cy="8" r="2" />{showPass && <line x1="2" y1="2" x2="14" y2="14" />}</svg>
              </button>
            </div>
            {mode === "login" && <div style={{ textAlign: "right", marginTop: 6 }}><span style={{ fontSize: 12, color: "var(--color-red)", cursor: "pointer" }}>Quên mật khẩu?</span></div>}
          </div>

          {mode === "register" && (
            <input type="password" placeholder="Nhập lại mật khẩu" value={confirm} onChange={(e) => setConfirm(e.target.value)} style={inputBox} />
          )}

          {error && (
            <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 12.5, color: "#C62828" }}>{error}</div>
          )}

          <button type="submit" disabled={busy} style={{
            height: 44, background: "var(--color-accent)", border: "none", borderRadius: 8,
            color: "var(--color-on-accent)", fontSize: 15, fontWeight: 700, cursor: busy ? "not-allowed" : "pointer",
            opacity: busy ? 0.6 : 1,
          }}>
            {busy ? "Đang xử lý..." : mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}
          </button>

          <button type="button" onClick={() => switchMode(mode === "login" ? "register" : "login")} style={{
            background: "none", border: "none", color: "#9E9E9E", fontSize: 13, cursor: "pointer", textAlign: "center",
          }}>
            {mode === "login" ? "Chưa có tài khoản? Đăng ký" : "Đã có tài khoản? Đăng nhập"}
          </button>
        </form>
      </div>
    </div>
  );
}
