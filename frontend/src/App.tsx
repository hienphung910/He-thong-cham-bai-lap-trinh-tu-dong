import { useCallback, useEffect, useState, type ReactElement } from "react";
import { api } from "./shared/api";
import type { LanguageInfo, Problem, Topic } from "./shared/types";
import Logo from "./shared/Logo";
// Module 1 - Quan ly tai khoan va phan quyen
import { AuthProvider, useAuth } from "./view/account/auth";
import LoginModal from "./view/account/LoginModal";
// Module 2 - Quan ly bai tap va lo trinh hoc (trang lam bai cua Module 3 mo tu Problems)
import Problems from "./view/problem/Problems";
import AdminPanel from "./view/problem/admin/AdminPanel";
// Module 4 - Lich su nop bai va bang xep hang
import Submissions from "./view/history/Submissions";
import MySubmissions from "./view/history/MySubmissions";
import Leaderboard from "./view/history/Leaderboard";


type Page = "Problems" | "Submissions" | "MySubmissions" | "Leaderboard" | "Admin";

const NAV_ITEMS: { key: Page; label: string; icon: ReactElement }[] = [
  {
    key: "Problems", label: "Lớp học",
    icon: <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><rect x="2" y="2" width="12" height="12" rx="2" /><path d="M2 6h12M6 6v8" /></svg>,
  },
  {
    key: "Submissions", label: "Trạng thái",
    icon: <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><rect x="2" y="2" width="12" height="12" rx="2" /><path d="M5 5h6M5 8h6M5 11h4" /></svg>,
  },
  {
    key: "MySubmissions", label: "Bài nộp",
    icon: <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M4 2v12M12 2v12M2 4h12M2 12h12" /></svg>,
  },
  {
    key: "Leaderboard", label: "Xếp hạng",
    icon: <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="8" cy="5" r="3" /><path d="M4 14l4-4 4 4" /></svg>,
  },
];

function AppShell() {
  const { user, checking, logout } = useAuth();
  const [page, setPage] = useState<Page>("Problems");

  const [avatarOpen, setAvatarOpen] = useState(false);

  const [problems, setProblems] = useState<Problem[]>([]);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [languages, setLanguages] = useState<LanguageInfo[]>([]);
  const [online, setOnline] = useState<boolean | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  // Muc "Quản trị" chi hien voi tai khoan duoc tao bai tap (may chu van kiem tra lai quyen).
  const isAdmin = user?.canCreateProblem === true;

  const loadProblems = useCallback(async () => {
    // Lo trinh la tuy chon: loi khi tai chu de khong duoc lam hong ca trang bai tap.
    const [p, l, t] = await Promise.all([api.problems(), api.languages(), api.topics().catch(() => [])]);
    setProblems(p);
    setLanguages(l);
    setTopics(t);
  }, []);

  useEffect(() => {
    if (!user) { setProblems([]); setOnline(null); return; }
    let alive = true;
    loadProblems()
      .then(() => alive && setOnline(true))
      .catch(() => alive && setOnline(false));
    return () => { alive = false; };
  }, [user, loadProblems]);

  const onSubmitted = useCallback(() => { setReloadKey((k) => k + 1); }, []);
  // Tra ve Promise de trang quan tri doi tai lai danh sach xong moi dong hop thoai.
  const onProblemsChanged = useCallback(async () => { await loadProblems().catch(() => undefined); setReloadKey((k) => k + 1); }, [loadProblems]);

  function handleLogout() { logout(); setPage("Problems"); setAvatarOpen(false); }

  if (checking) {
    return (
      <div style={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--color-page-bg)" }}>
        <span style={{ fontSize: 13, color: "var(--color-text-muted)" }}>đang kiểm tra phiên đăng nhập...</span>
      </div>
    );
  }

  if (!user) return <LoginModal />;

  const navItems = isAdmin ? [...NAV_ITEMS, {
    key: "Admin" as Page, label: "Quản trị",
    icon: <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="8" cy="8" r="3" /><path d="M8 1v2M8 13v2M1 8h2M13 8h2" /></svg>,
  }] : NAV_ITEMS;

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", background: "var(--color-page-bg)" }}>
      {/* ═══ RED HEADER ═══ */}
      <header style={{
        display: "flex", alignItems: "center", height: 48, padding: "0 20px",
        background: "var(--color-header)", flexShrink: 0, zIndex: 50,
      }}>
        {/* Logo */}
        <div
          onClick={() => window.location.reload()}
          style={{ display: "flex", alignItems: "center", gap: 8, marginRight: 28, cursor: "pointer", userSelect: "none" }}
        >
          {/* Nen trang de o do giua khong bi chim vao header do */}
          <div style={{ width: 30, height: 30, borderRadius: 8, background: "#fff", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Logo size={18} />
          </div>
          <span style={{ fontSize: 18, fontWeight: 800, color: "#fff", letterSpacing: "-0.01em" }}>CodeHub</span>
        </div>

        {/* Nav tabs */}
        <nav style={{ display: "flex", height: "100%", gap: 0 }}>
          {navItems.map((item) => {
            const active = page === item.key;
            return (
              <button
                key={item.key}
                onClick={() => setPage(item.key)}
                style={{
                  height: "100%", padding: "0 16px", border: "none", cursor: "pointer",
                  display: "flex", alignItems: "center", gap: 6,
                  background: active ? "rgba(255,255,255,0.18)" : "transparent",
                  color: active ? "#fff" : "rgba(255,255,255,0.8)",
                  fontSize: 13.5, fontWeight: active ? 700 : 500,
                  borderRadius: active ? "6px 6px 0 0" : 0,
                  transition: "background 0.15s",
                }}
                onMouseEnter={(e) => { if (!active) e.currentTarget.style.background = "rgba(255,255,255,0.08)"; }}
                onMouseLeave={(e) => { if (!active) e.currentTarget.style.background = "transparent"; }}
              >
                {item.icon}
                {item.label}
              </button>
            );
          })}
        </nav>

        <div style={{ flex: 1 }} />

        {/* Right side */}
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          {/* Status dot */}
          <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 11, color: "rgba(255,255,255,0.6)", fontFamily: "var(--font-mono)" }}>
            <span style={{
              width: 6, height: 6, borderRadius: "50%",
              background: online === false ? "#ff5252" : online ? "#69f0ae" : "rgba(255,255,255,0.4)",
              animation: online === null ? "livePulse 1.4s infinite" : "none",
            }} />
          </span>

          {/* Notification bell */}
          <button style={{ background: "none", border: "none", color: "rgba(255,255,255,0.8)", cursor: "pointer", padding: 4, display: "flex" }}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M4 6a4 4 0 0 1 8 0c0 4 2 5 2 5H2s2-1 2-5" /><path d="M6.5 13a1.5 1.5 0 0 0 3 0" /></svg>
          </button>

          {/* User avatar */}
          <div style={{ position: "relative" }}>
            <button
              onClick={() => setAvatarOpen((v) => !v)}
              style={{
                display: "flex", alignItems: "center", gap: 8, background: "none",
                border: "none", cursor: "pointer", padding: "4px 6px", borderRadius: 20,
              }}
            >
              <div style={{
                width: 28, height: 28, borderRadius: "50%", background: "#E57373",
                display: "flex", alignItems: "center", justifyContent: "center",
                fontSize: 11, fontWeight: 700, color: "#fff",
              }}>
                {user.username.slice(0, 2).toUpperCase()}
              </div>
              <span style={{ fontSize: 13, fontWeight: 600, color: "#fff" }}>{user.fullName || user.username}</span>
              <svg width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="2" strokeLinecap="round" style={{ transform: avatarOpen ? "rotate(180deg)" : "none", transition: "transform 0.15s" }}><polyline points="2 4 6 8 10 4" /></svg>
            </button>

            {avatarOpen && (
              <div
                style={{
                  position: "absolute", top: "calc(100% + 6px)", right: 0,
                  background: "#fff", border: "1px solid #E0E0E0",
                  borderRadius: 10, boxShadow: "0 8px 30px rgba(0,0,0,0.12)", minWidth: 200, padding: 6, zIndex: 200,
                  animation: "slideIn 0.12s ease",
                }}
                onMouseLeave={() => setAvatarOpen(false)}
              >
                <div style={{ padding: "10px 14px 8px", borderBottom: "1px solid #F0F0F0", marginBottom: 4 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: "#212121" }}>{user.fullName || user.username}</div>
                  <div style={{ fontSize: 11, color: "#9E9E9E", fontFamily: "var(--font-mono)" }}>@{user.username} · {user.roleName}</div>
                </div>
                {isAdmin && (
                  <button onClick={() => { setPage("Admin"); setAvatarOpen(false); }}
                    style={{ width: "100%", display: "flex", alignItems: "center", gap: 8, padding: "8px 14px", background: "none", border: "none", color: "var(--color-red)", fontSize: 13, cursor: "pointer", borderRadius: 6, textAlign: "left" }}
                    onMouseEnter={(e) => { e.currentTarget.style.background = "#FFF5F5"; }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = "none"; }}
                  >
                    Quản trị
                  </button>
                )}
                <button onClick={handleLogout}
                  style={{ width: "100%", display: "flex", alignItems: "center", gap: 8, padding: "8px 14px", background: "none", border: "none", color: "#F44336", fontSize: 13, cursor: "pointer", borderRadius: 6, textAlign: "left" }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = "#FFF5F5"; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = "none"; }}
                >
                  <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M6 3H3a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h3" /><polyline points="10 10 14 8 10 6" /><line x1="14" y1="8" x2="6" y2="8" /></svg>
                  Đăng xuất
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* ═══ CONTENT ═══ */}
      <main style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
        {online === false ? (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12 }}>
            <div style={{ fontSize: 14, color: "#F44336", fontWeight: 600 }}>Không kết nối được máy chủ</div>
            <div style={{ fontSize: 13, color: "#9E9E9E", textAlign: "center", lineHeight: 1.7 }}>
              Chạy <code style={{ color: "var(--color-red)", background: "#FDECEA", padding: "2px 6px", borderRadius: 4 }}>java ... com.ptit.oj.Main --serve</code> (xem README) rồi tải lại trang.
            </div>
          </div>
        ) : (
          <>
            {page === "Problems" && <Problems problems={problems} topics={topics} languages={languages} onSubmitted={onSubmitted} reloadKey={reloadKey} />}
            {page === "Submissions" && <Submissions reloadKey={reloadKey} />}
            {page === "MySubmissions" && <MySubmissions />}
            {page === "Leaderboard" && <Leaderboard reloadKey={reloadKey} problems={problems} />}
            {page === "Admin" && isAdmin && <AdminPanel onProblemsChanged={onProblemsChanged} problems={problems} topics={topics} />}
          </>
        )}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
