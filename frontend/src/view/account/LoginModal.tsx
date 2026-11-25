import Logo from "../../shared/Logo";
import { useEffect, useRef, useState } from "react";
import { api } from "../../shared/api";
import { useAuth } from "./auth";
import { EVIDENCE_ACCEPT, readEvidence } from "./evidence";
import Turnstile from "./Turnstile";
import { checkConfirm, checkEmail, checkFullName, checkLabel, checkPassword, checkStudentCode, checkUsername } from "./validate";

/** Dong bao loi nho ngay duoi mot o nhap. */
function FieldError({ text }: { text: string }) {
  if (!text) return null;
  return <div style={{ marginTop: 5, fontSize: 12, lineHeight: 1.4, color: "#C62828" }}>{text}</div>;
}

type Mode = "login" | "register" | "forgot";

/** Dung luong file de doc: "182 KB", "1,0 MB". */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(".", ",")} MB`;
}

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
  const [email, setEmail] = useState("");
  /** May chu co gui duoc email khong: co thi moi hien "Quen mat khau?". */
  const [canReset, setCanReset] = useState(false);
  // Sau moi lan gui lien ket dat lai mat khau phai doi mot luc moi gui lai duoc (may chu cung chan):
  // resendAt la thoi diem duoc gui lai, resendIn la so giay con lai de hien tren nut.
  const [resendAt, setResendAt] = useState(0);
  const [resendIn, setResendIn] = useState(0);
  useEffect(() => {
    if (!resendAt) return;
    const tick = () => {
      const left = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
      setResendIn(left);
      if (left === 0) setResendAt(0);
    };
    tick();
    const timer = setInterval(tick, 500);
    return () => clearInterval(timer);
  }, [resendAt]);
  const [studentCode, setStudentCode] = useState("");
  const [className, setClassName] = useState("");
  // Vai tro chon khi dang ky: hoc sinh hoac giang vien (quan tri vien khong tu dang ky duoc).
  const [role, setRole] = useState<"STUDENT" | "TEACHER">("STUDENT");
  const [department, setDepartment] = useState("");
  // Minh chung giang vien gui kem de quan tri vien xem khi duyet: ten file, noi dung base64 va
  // dung luong se gui di (anh da duoc thu nho nen co the nho hon file goc).
  const [evidence, setEvidence] = useState<{ name: string; data: string; size: number } | null>(null);
  const evidenceInput = useRef<HTMLInputElement>(null);
  // CAPTCHA cho ca dang nhap va dang ky: may chu cho biet co bat khong (siteKey khac ""). Token dung
  // duoc mot lan nen sau moi lan gui form tang captchaReset de widget cap token moi.
  const [siteKey, setSiteKey] = useState("");
  const [captcha, setCaptcha] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);
  /** May chu co dang cho tu dang ky khong; mac dinh coi la co cho toi khi hoi duoc may chu. */
  const [allowRegister, setAllowRegister] = useState(true);
  useEffect(() => {
    api.config().then((c) => {
      setSiteKey(c.turnstileSiteKey);
      setAllowRegister(c.allowRegister !== false);
      setCanReset(c.passwordReset === true);
    }).catch(() => undefined);
  }, []);
  const registerClosed = mode === "register" && !allowRegister;
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [showPass, setShowPass] = useState(false);

  // Form dang ky bao loi ngay duoi tung o. Chi bao o nguoi dung DA ROI KHOI (hoac sau khi bam Tao tai
  // khoan), de khong hien loi do khi ho con dang go do.
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const touch = (field: string) => setTouched((old) => (old[field] ? old : { ...old, [field]: true }));
  const fieldErrors: Record<string, string> = mode !== "register" ? {} : {
    username: checkUsername(username),
    fullName: checkFullName(fullName),
    email: checkEmail(email),
    studentCode: role === "STUDENT" ? checkStudentCode(studentCode) : "",
    className: role === "STUDENT" ? checkLabel(className, "Lớp") : "",
    department: role === "TEACHER" ? checkLabel(department, "Bộ môn") : "",
    password: checkPassword(password, username),
    confirm: checkConfirm(password, confirm),
  };
  const shown = (field: string) => (touched[field] ? fieldErrors[field] ?? "" : "");
  /** Vien do cho o dang co loi. */
  const marked = (field: string): React.CSSProperties => (shown(field) ? { borderColor: "#E57373" } : {});

  function switchMode(next: Mode) { setMode(next); setError(""); setNotice(""); setPassword(""); setConfirm(""); setTouched({}); }

  async function pickEvidence(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";      // de chon lai dung file do van nhan duoc su kien
    if (!file) return;
    setError("");
    try {
      const data = await readEvidence(file);
      // 4 ky tu base64 = 3 byte; bo phan dem "=" o cuoi.
      const size = Math.floor(data.length * 3 / 4) - (data.endsWith("==") ? 2 : data.endsWith("=") ? 1 : 0);
      setEvidence({ name: file.name, data, size });
    }
    catch (e2) { setEvidence(null); setError(e2 instanceof Error ? e2.message : String(e2)); }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setNotice("");
    if (mode === "login") {
      if (!username || !password) { setError("Hãy nhập tên đăng nhập và mật khẩu."); return; }
      if (siteKey && !captcha) { setError("Hãy hoàn thành bước xác minh CAPTCHA."); return; }
      setBusy(true);
      try { await login(username.trim(), password, captcha); }
      catch (e2) {
        setError(e2 instanceof Error ? e2.message : String(e2));
        // Token vua gui da bi dung: lay token moi cho lan thu sau. (Dang nhap thanh cong thi form nay dong luon.)
        setCaptcha("");
        setCaptchaReset((n) => n + 1);
      }
      finally { setBusy(false); }
      return;
    }
    if (mode === "forgot") {
      if (!username.trim()) { setError("Hãy nhập tên đăng nhập hoặc email."); return; }
      if (siteKey && !captcha) { setError("Hãy hoàn thành bước xác minh CAPTCHA."); return; }
      setBusy(true);
      try {
        const res = await api.forgotPassword(username.trim(), captcha);
        // O lai form nay: nguoi dung chua nhan duoc thu thi doi het dem nguoc roi bam gui lai.
        setNotice(res.message);
        setResendAt(Date.now() + (res.retryAfter || 60) * 1000);
      } catch (e2) {
        const message = e2 instanceof Error ? e2.message : String(e2);
        setError(message);
        // May chu bao "doi N giay": dem nguoc dung so giay do.
        const wait = /đợi (\d+) giây/.exec(message);
        if (wait) setResendAt(Date.now() + Number(wait[1]) * 1000);
      }
      finally {
        setBusy(false);
        setCaptcha("");
        setCaptchaReset((n) => n + 1);
      }
      return;
    }
    // Bam Tao tai khoan: hien loi cua MOI o con sai, khong gui gi len may chu.
    setTouched({ username: true, fullName: true, email: true, studentCode: true, className: true, department: true, password: true, confirm: true });
    if (Object.values(fieldErrors).some((message) => message !== "")) {
      setError("Hãy sửa các ô đang báo lỗi rồi thử lại.");
      return;
    }
    if (role === "TEACHER" && !evidence) { setError("Hãy tải minh chứng lên trước khi đăng ký giảng viên."); return; }
    if (siteKey && !captcha) { setError("Hãy hoàn thành bước xác minh CAPTCHA."); return; }
    setBusy(true);
    try {
      const { pending, emailVerification } = await register({
        role, username: username.trim(), password, fullName: fullName.trim(), email: email.trim(),
        studentCode: studentCode.trim(), className: className.trim(), department: department.trim(),
        evidence: role === "TEACHER" && evidence ? evidence.data : "",
        captcha,
      });
      setEvidence(null);
      switchMode("login");
      setNotice((pending
        ? `Đã gửi đăng ký giảng viên @${username.trim()}. Tài khoản cần quản trị viên duyệt rồi mới đăng nhập được.`
        : `Đã tạo tài khoản học sinh @${username.trim()}. Mời bạn đăng nhập.`)
        + (emailVerification ? ` Thư xác minh đã gửi tới ${email.trim()}.` : ""));
    } catch (e2) { setError(e2 instanceof Error ? e2.message : String(e2)); }
    finally {
      setBusy(false);
      // Token vua gui da bi dung (du dang ky thanh cong hay khong): lay token moi cho lan gui sau.
      setCaptcha("");
      setCaptchaReset((n) => n + 1);
    }
  }

  /** Dang trong khoang cho giua hai lan gui lien ket dat lai mat khau. */
  const waiting = mode === "forgot" && resendIn > 0;

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
      {/* Form dang ky kem cac dong bao loi co the cao hon man hinh: khung nay cuon duoc, va form can giua bang
          margin auto (can giua bang alignItems thi phan tran phia tren bi cat mat, khong cuon toi duoc). */}
      <div style={{ flex: 1, display: "flex", padding: 40, overflowY: "auto" }}>
        {/* noValidate: loi nhap sai do handleSubmit bao bang tieng Viet, khong dung bong bao cua trinh duyet. */}
        <form onSubmit={handleSubmit} noValidate style={{ width: 400, maxWidth: "100%", margin: "auto", display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ fontSize: 24, fontWeight: 700, color: "#212121", margin: 0 }}>
            {mode === "login" ? "Đăng nhập" : mode === "register" ? "Đăng ký tài khoản" : "Quên mật khẩu"}
          </h2>

          {notice && (
            <div style={{ padding: "10px 14px", background: "#E8F5E9", border: "1px solid #C8E6C9", borderRadius: 6, fontSize: 12.5, color: "#2E7D32" }}>{notice}</div>
          )}
          {registerClosed && (
            <div style={{ padding: "10px 14px", background: "#FFF8E1", border: "1px solid #FFE082", borderRadius: 6, fontSize: 12.5, color: "#7A5200" }}>
              Hệ thống đang tạm khoá tự đăng ký. Hãy liên hệ quản trị viên để được cấp tài khoản.
            </div>
          )}

          {/* Username */}
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, ...inputBox, padding: 0, paddingLeft: 12, ...marked("username") }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1.4"><circle cx="8" cy="5" r="3" /><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" /></svg>
              <input type="text" placeholder={mode === "forgot" ? "Tên đăng nhập hoặc email" : "Tên đăng nhập"} value={username} onChange={(e) => setUsername(e.target.value)} autoFocus
                onBlur={() => touch("username")} maxLength={mode === "register" ? 32 : undefined} autoComplete="username"
                style={{ flex: 1, border: "none", outline: "none", fontSize: 14, height: "100%", background: "transparent" }} />
            </div>
            <FieldError text={shown("username")} />
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
              <div>
                <input type="text" placeholder="Họ và tên" value={fullName} onChange={(e) => setFullName(e.target.value)}
                  onBlur={() => touch("fullName")} maxLength={100} autoComplete="name" style={{ ...inputBox, ...marked("fullName") }} />
                <FieldError text={shown("fullName")} />
              </div>
              <div>
                <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)}
                  onBlur={() => touch("email")} maxLength={190} autoComplete="email" style={{ ...inputBox, ...marked("email") }} />
                <FieldError text={shown("email")} />
              </div>
              {role === "STUDENT" ? (
                <>
                  <div>
                    <input type="text" placeholder="Mã sinh viên" value={studentCode} onChange={(e) => setStudentCode(e.target.value)}
                      onBlur={() => touch("studentCode")} maxLength={30} style={{ ...inputBox, ...marked("studentCode") }} />
                    <FieldError text={shown("studentCode")} />
                  </div>
                  <div>
                    <input type="text" placeholder="Lớp" value={className} onChange={(e) => setClassName(e.target.value)}
                      onBlur={() => touch("className")} maxLength={40} style={{ ...inputBox, ...marked("className") }} />
                    <FieldError text={shown("className")} />
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <input type="text" placeholder="Bộ môn" value={department} onChange={(e) => setDepartment(e.target.value)}
                      onBlur={() => touch("department")} maxLength={40} style={{ ...inputBox, ...marked("department") }} />
                    <FieldError text={shown("department")} />
                  </div>
                  {/* Minh chung la giang vien: quan tri vien xem file nay khi duyet tai khoan. */}
                  <input ref={evidenceInput} type="file" accept={EVIDENCE_ACCEPT} style={{ display: "none" }} onChange={(e) => void pickEvidence(e)} />
                  {evidence ? (
                    // Da chon file: mot the dinh kem (bieu tuong loai file, ten, dung luong, doi / go) nhu cac form nop ho so.
                    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 8px 8px 10px", background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8 }}>
                      <span style={{
                        width: 36, height: 36, borderRadius: 8, flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center",
                        background: "var(--color-red-light)", color: "var(--color-red)",
                      }}>
                        {/\.pdf$/i.test(evidence.name) ? (
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></svg>
                        ) : (
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="m21 16-5-5-8 8" /></svg>
                        )}
                      </span>
                      <span style={{ flex: 1, minWidth: 0 }}>
                        <span title={evidence.name} style={{ display: "block", fontSize: 13.5, fontWeight: 600, color: "#212121", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{evidence.name}</span>
                        <span style={{ display: "block", fontSize: 11.5, color: "#9E9E9E", marginTop: 1 }}>Minh chứng - {formatSize(evidence.size)}</span>
                      </span>
                      <button type="button" onClick={() => evidenceInput.current?.click()} style={{
                        height: 30, padding: "0 10px", borderRadius: 6, border: "1px solid #E0E0E0", background: "#fff",
                        color: "#424242", fontSize: 12.5, fontWeight: 600, cursor: "pointer", flexShrink: 0,
                      }}>Đổi file</button>
                      <button type="button" onClick={() => setEvidence(null)} title="Gỡ minh chứng" aria-label="Gỡ minh chứng" style={{
                        width: 30, height: 30, borderRadius: 6, border: "none", background: "none", color: "#9E9E9E", cursor: "pointer",
                        display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
                      }}>
                        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M3.5 3.5l9 9M12.5 3.5l-9 9" /></svg>
                      </button>
                    </div>
                  ) : (
                    <button type="button" onClick={() => evidenceInput.current?.click()} title="Ảnh PNG, JPG, WEBP hoặc file PDF, tối đa 1 MB" style={{
                      display: "flex", alignItems: "center", justifyContent: "center", gap: 8, width: "100%", height: 44, borderRadius: 8, cursor: "pointer",
                      border: "1px dashed #BDBDBD", background: "#fff", color: "#616161", fontSize: 13.5, fontWeight: 600, boxSizing: "border-box",
                    }}>
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M8 10.5V2.5M4.8 5.5 8 2.3l3.2 3.2M2.5 10.5v2a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-2" /></svg>
                      Tải minh chứng lên
                    </button>
                  )}
                </>
              )}
            </>
          )}

          {/* Password */}
          {mode !== "forgot" && <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, ...inputBox, padding: 0, paddingLeft: 12, paddingRight: 8, ...marked("password") }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1.4"><rect x="3" y="7" width="10" height="7" rx="1.5" /><path d="M5 7V5a3 3 0 0 1 6 0v2" /></svg>
              <input type={showPass ? "text" : "password"} placeholder="Mật khẩu" value={password} onChange={(e) => setPassword(e.target.value)}
                onBlur={() => touch("password")} maxLength={128} autoComplete={mode === "register" ? "new-password" : "current-password"}
                style={{ flex: 1, border: "none", outline: "none", fontSize: 14, height: "100%", background: "transparent" }} />
              <button type="button" onClick={() => setShowPass((v) => !v)} style={{ background: "none", border: "none", cursor: "pointer", color: "#BDBDBD", display: "flex", padding: 4 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M1 8s3-5 7-5 7 5 7 5-3 5-7 5-7-5-7-5z" /><circle cx="8" cy="8" r="2" />{showPass && <line x1="2" y1="2" x2="14" y2="14" />}</svg>
              </button>
            </div>
            <FieldError text={shown("password")} />
            {mode === "login" && canReset && (
              <div style={{ textAlign: "right", marginTop: 6 }}>
                <button type="button" onClick={() => switchMode("forgot")} style={{ background: "none", border: "none", padding: 0, fontSize: 12, color: "var(--color-red)", cursor: "pointer" }}>Quên mật khẩu?</button>
              </div>
            )}
          </div>}

          {mode === "register" && (
            <div>
              <input type="password" placeholder="Nhập lại mật khẩu" value={confirm} onChange={(e) => setConfirm(e.target.value)}
                onBlur={() => touch("confirm")} maxLength={128} autoComplete="new-password" style={{ ...inputBox, ...marked("confirm") }} />
              <FieldError text={shown("confirm")} />
            </div>
          )}

          {siteKey && (
            <Turnstile siteKey={siteKey} resetKey={captchaReset} onToken={setCaptcha} onError={setError} />
          )}

          {error && (
            <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 12.5, color: "#C62828" }}>{error}</div>
          )}

          <button type="submit" disabled={busy || registerClosed || waiting} style={{
            height: 44, background: "var(--color-accent)", border: "none", borderRadius: 8,
            color: "var(--color-on-accent)", fontSize: 15, fontWeight: 700, cursor: busy || registerClosed || waiting ? "not-allowed" : "pointer",
            opacity: busy || registerClosed || waiting ? 0.6 : 1,
          }}>
            {busy ? "Đang xử lý..." : mode === "login" ? "Đăng nhập" : mode === "register" ? "Tạo tài khoản"
              : waiting ? `Gửi lại sau ${resendIn}s` : notice ? "Gửi lại liên kết" : "Gửi liên kết đặt lại"}
          </button>

          <button type="button" onClick={() => switchMode(mode === "login" ? "register" : "login")} style={{
            background: "none", border: "none", color: "#9E9E9E", fontSize: 13, cursor: "pointer", textAlign: "center",
          }}>
            {mode === "login" ? "Chưa có tài khoản? Đăng ký" : mode === "register" ? "Đã có tài khoản? Đăng nhập" : "Quay lại đăng nhập"}
          </button>
        </form>
      </div>
    </div>
  );
}
