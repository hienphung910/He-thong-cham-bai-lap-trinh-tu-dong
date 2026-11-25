import { useEffect, useState } from "react";
import Logo from "../../shared/Logo";
import { api } from "../../shared/api";

/**
 * Trang xac minh email, mo tu link trong thu: /verify-email?token=...
 * Ma duoc doc mot lan roi xoa khoi thanh dia chi; trang gui ma len may chu va bao ket qua.
 */
export default function VerifyEmail() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get("token") ?? "");
  const [state, setState] = useState<"checking" | "done" | "failed">("checking");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    window.history.replaceState({}, "", "/verify-email");
    if (!token) { setState("failed"); return; }
    let alive = true;
    api.verifyEmail(token)
      .then((r) => { if (alive) { setEmail(r.email); setState("done"); } })
      .catch((e) => { if (alive) { setError(e instanceof Error ? e.message : String(e)); setState("failed"); } });
    return () => { alive = false; };
  }, [token]);

  return (
    <div style={{ height: "100%", display: "flex", background: "var(--color-page-bg)" }}>
      <div style={{ flex: "0 0 42%", background: "#111111", display: "flex", flexDirection: "column", justifyContent: "center", padding: "0 60px" }}>
        <div style={{ marginBottom: 20 }}><Logo size={64} /></div>
        <p style={{ fontSize: 13, color: "var(--color-accent)", margin: 0, fontFamily: "var(--font-mono)" }}>{">_"} ready to judge</p>
      </div>
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: 40 }}>
        <div style={{ width: 400, display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ fontSize: 24, fontWeight: 700, color: "#212121", margin: 0 }}>Xác minh email</h2>
          {state === "checking" && <div style={{ fontSize: 13, color: "#757575" }}>Đang xác minh...</div>}
          {state === "done" && (
            <div style={{ padding: "10px 14px", background: "#E8F5E9", border: "1px solid #C8E6C9", borderRadius: 6, fontSize: 12.5, color: "#2E7D32" }}>
              Đã xác minh email {email}.
            </div>
          )}
          {state === "failed" && (
            <div style={{ padding: "10px 14px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 12.5, color: "#C62828" }}>
              {error || "Liên kết xác minh không hợp lệ hoặc đã hết hạn. Hãy gửi lại thư xác minh ở trang Tài khoản của tôi."}
            </div>
          )}
          {state !== "checking" && (
            <button type="button" onClick={() => window.location.replace("/")} style={{
              height: 44, background: "var(--color-accent)", border: "none", borderRadius: 8,
              color: "var(--color-on-accent)", fontSize: 15, fontWeight: 700, cursor: "pointer",
            }}>Về trang chính</button>
          )}
        </div>
      </div>
    </div>
  );
}
