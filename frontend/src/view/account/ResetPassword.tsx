import { useEffect, useState } from "react";
import Logo from "../../shared/Logo";
import { api, setToken } from "../../shared/api";

/**
 * Trang dat lai mat khau, mo tu link trong thu "Quen mat khau": /reset-password?token=...
 *
 * Ma trong link duoc doc mot lan roi xoa khoi thanh dia chi ngay (khong nam lai trong lich su trinh
 * duyet). Trang hoi may chu truoc xem ma con dung duoc khong de bao "link het han" ngay tu dau.
 */
export default function ResetPassword() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get("token") ?? "");
  const [state, setState] = useState<"checking" | "ready" | "invalid" | "done">("checking");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [username, setUsername] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    window.history.replaceState({}, "", "/reset-password");
    if (!token) { setState("invalid"); return; }
    let alive = true;
    api.checkResetToken(token)
      .then((r) => { if (alive) setState(r.valid ? "ready" : "invalid"); })
      .catch((e) => { if (alive) { setError(e instanceof Error ? e.message : String(e)); setState("invalid"); } });
    return () => { alive = false; };
  }, [token]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError("");
    if (password.length < 8) { setError("Mật khẩu phải có ít nhất 8 ký tự."); return; }
    if (password !== confirm) { setError("Hai lần nhập mật khẩu không khớp."); return; }
    setBusy(true);
    try {
      const res = await api.resetPassword(token, password);
      // May chu da huy moi phien cu cua tai khoan nay: bo luon token dang giu trong tab (neu co).
      setToken(null);
      setUsername(res.username);
      setState("done");
    } catch (e2) { setError(e2 instanceof Error ? e2.message : String(e2)); }
    finally { setBusy(false); }
  }

  const inputBox: React.CSSProperties = {
    width: "100%", height: 44, padding: "0 14px", background: "#fff",
    border: "1px solid #E0E0E0", borderRadius: 8, color: "#212121",
    fontSize: 14, outline: "none", boxSizing: "border-box",
  };
  const mainButton: React.CSSProperties = {
    height: 44, background: "var(--color-accent)", border: "none", borderRadius: 8,
    color: "var(--color-on-accent)", fontSize: 15, fontWeight: 700, cursor: busy ? "not-allowed" : "pointer",
    opacity: busy ? 0.6 : 1,
  };
  /** Ve trang dang nhap: tai lai tu dau de moi trang thai cu trong tab bi bo. */
  const toLogin = () => window.location.replace("/");

  return (
    <div style={{ height: "100%", display: "flex", background: "var(--color-page-bg)" }}>
      <div style={{ flex: "0 0 42%", background: "#111111", display: "flex", flexDirection: "column", justifyContent: "center", padding: "0 60px" }}>
        <div style={{ marginBottom: 20 }}><Logo size={64} /></div>
        <p style={{ fontSize: 13, color: "var(--color-accent)", margin: 0, fontFamily: "var(--font-mono)" }}>{">_"} ready to judge</p>
      </div>
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: 40 }}>
        <form onSubmit={handleSubmit} style={{ width: 400, display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ fontSize: 24, fontWeight: 700, color: "#212121", margin: 0 }}>Đặt lại mật khẩu</h2>

          {state === "checking" && <div style={{ fontSize: 13, color: "#757575" }}>Đang kiểm tra liên kết...</div>}

          {state === "invalid" && (
            <>
              <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 12.5, color: "#C62828" }}>
                {error || "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới ở trang đăng nhập."}
              </div>
              <button type="button" onClick={toLogin} style={mainButton}>Về trang đăng nhập</button>
            </>
          )}

          {state === "ready" && (
            <>
              <input type="password" placeholder="Mật khẩu mới" value={password} onChange={(e) => setPassword(e.target.value)}
                autoFocus autoComplete="new-password" style={inputBox} />
              <input type="password" placeholder="Nhập lại mật khẩu mới" value={confirm} onChange={(e) => setConfirm(e.target.value)}
                autoComplete="new-password" style={inputBox} />
              {error && (
                <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 12.5, color: "#C62828" }}>{error}</div>
              )}
              <button type="submit" disabled={busy} style={mainButton}>{busy ? "Đang xử lý..." : "Đặt mật khẩu mới"}</button>
            </>
          )}

          {state === "done" && (
            <>
              <div style={{ padding: "10px 14px", background: "#E8F5E9", border: "1px solid #C8E6C9", borderRadius: 6, fontSize: 12.5, color: "#2E7D32" }}>
                Đã đặt mật khẩu mới cho tài khoản @{username}. Mời bạn đăng nhập lại.
              </div>
              <button type="button" onClick={toLogin} style={mainButton}>Đăng nhập</button>
            </>
          )}
        </form>
      </div>
    </div>
  );
}
