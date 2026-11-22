import { useCallback, useEffect, useState } from "react";
import { api } from "./shared/api";
import { papi } from "./shared/platformApi";
import { NavContext, type PageKey } from "./shared/nav";
import type { LanguageInfo, Problem, Topic } from "./shared/types";
import Logo from "./shared/Logo";
import { AuthProvider, useAuth } from "./view/account/auth";
import LoginModal from "./view/account/LoginModal";
import MyAccount from "./view/account/MyAccount";
import Problems from "./view/problem/Problems";
import AdminPanel from "./view/problem/admin/AdminPanel";
import Submissions from "./view/history/Submissions";
import MySubmissions from "./view/history/MySubmissions";
import Leaderboard from "./view/history/Leaderboard";
import Classrooms from "./view/school/Classrooms";
import Courses from "./view/school/Courses";
import Assignments from "./view/school/Assignments";
import Contests from "./view/contest/Contests";
import Meetings from "./view/meeting/Meetings";
import Playground from "./view/tools/Playground";
import CpTools from "./view/tools/CpTools";
import StressTest from "./view/tools/StressTest";
import Assistant from "./view/tools/Assistant";
import Analytics from "./view/profile/Analytics";
import Achievements from "./view/profile/Achievements";
import Billing from "./view/profile/Billing";
import Forum from "./view/community/Forum";
import ChatPage from "./view/community/ChatPage";
import Notifications from "./view/community/Notifications";
import Dashboard from "./view/manage/Dashboard";
import TeachingAssistant from "./view/manage/TeachingAssistant";
import Parents from "./view/manage/Parents";
import Accounts from "./view/admin/Accounts";
import Organizations from "./view/admin/Organizations";
import SystemAdmin from "./view/admin/SystemAdmin";
import AuditLogs from "./view/admin/AuditLogs";
import ModuleMap from "./view/ModuleMap";

/**
 * Thanh dieu huong ben trai, chia theo nhom. Truong "role" gioi han ai thay muc do:
 * "staff" = giang vien va quan tri, "admin" = chi quan tri. Day chi la an / hien tren
 * giao dien - may chu van kiem tra lai quyen o tung request.
 */
interface NavItem { key: PageKey; label: string; role?: "staff" | "admin" }
const NAV_GROUPS: { title: string; items: NavItem[] }[] = [
  {
    title: "Học tập",
    items: [
      { key: "Problems", label: "Lớp học (lộ trình)" },
      { key: "Classrooms", label: "Lớp của tôi" },
      { key: "Courses", label: "Khóa học, tài liệu" },
      { key: "Assignments", label: "Bài tập được giao" },
      { key: "Contests", label: "Kỳ thi" },
      { key: "Meetings", label: "Phòng học trực tuyến" },
    ],
  },
  {
    title: "Công cụ",
    items: [
      { key: "Playground", label: "IDE trực tuyến" },
      { key: "CpTools", label: "Công cụ CP" },
      { key: "StressTest", label: "Stress test" },
      { key: "Assistant", label: "Trợ lý AI" },
    ],
  },
  {
    title: "Cá nhân",
    items: [
      { key: "Analytics", label: "Phân tích học tập" },
      { key: "Achievements", label: "Thành tích" },
      { key: "Billing", label: "Gói dịch vụ" },
      { key: "Account", label: "Tài khoản của tôi" },
    ],
  },
  {
    title: "Cộng đồng",
    items: [
      { key: "Forum", label: "Diễn đàn" },
      { key: "Chat", label: "Chat" },
      { key: "Notifications", label: "Thông báo" },
    ],
  },
  {
    title: "Lịch sử",
    items: [
      { key: "Submissions", label: "Trạng thái" },
      { key: "MySubmissions", label: "Bài nộp" },
      { key: "Leaderboard", label: "Xếp hạng" },
    ],
  },
  {
    title: "Giảng dạy",
    items: [
      { key: "Dashboard", label: "Tổng quan", role: "staff" },
      { key: "TeachingAssistant", label: "Trợ lý giảng dạy", role: "staff" },
      { key: "Parents", label: "Phụ huynh", role: "staff" },
    ],
  },
  {
    title: "Quản trị",
    items: [
      { key: "Admin", label: "Bài tập và bộ test", role: "admin" },
      { key: "Accounts", label: "Tài khoản, giảng viên", role: "admin" },
      { key: "Organizations", label: "Tổ chức", role: "admin" },
      { key: "System", label: "Hệ thống", role: "admin" },
      { key: "Audit", label: "Nhật ký hoạt động", role: "admin" },
    ],
  },
  { title: "Giới thiệu", items: [{ key: "ModuleMap", label: "Sơ đồ 40 module" }] },
];

function AppShell() {
  const { user, checking, logout } = useAuth();
  const [page, setPage] = useState<PageKey>("Problems");
  /** Ma bai can mo san khi chuyen sang trang Lop hoc (bam tu mot duong link toi bai). */
  const [openProblemId, setOpenProblemId] = useState<string | null>(null);
  const [avatarOpen, setAvatarOpen] = useState(false);

  const [problems, setProblems] = useState<Problem[]>([]);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [languages, setLanguages] = useState<LanguageInfo[]>([]);
  const [online, setOnline] = useState<boolean | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [unread, setUnread] = useState(0);
  const [announcement, setAnnouncement] = useState("");

  const isAdmin = user?.role === "ADMIN";
  const isStaff = user?.role === "ADMIN" || user?.role === "TEACHER";

  /** Nguoi dang dang nhap co duoc vao trang nay khong (theo truong role cua NAV_GROUPS). */
  const allowed = useCallback((key: PageKey) => {
    for (const group of NAV_GROUPS) {
      for (const item of group.items) {
        if (item.key !== key) continue;
        if (item.role === "admin") return isAdmin;
        if (item.role === "staff") return isStaff;
      }
    }
    return true;
  }, [isAdmin, isStaff]);

  const loadProblems = useCallback(async () => {
    // Lo trinh la tuy chon: loi khi tai chu de khong duoc lam hong ca trang bai tap.
    const [p, l, t] = await Promise.all([api.problems(), api.languages(), api.topics().catch(() => [])]);
    setProblems(p);
    setLanguages(l);
    setTopics(t);
  }, []);

  /** So thong bao chua doc (hien tren chuong) va thong bao chung cua quan tri vien. */
  const loadHeader = useCallback(() => {
    papi.notifications().then((n) => setUnread(n.unread)).catch(() => undefined);
    papi.settings().then((s) => setAnnouncement(s.announcement)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!user) { setProblems([]); setOnline(null); return; }
    let alive = true;
    loadProblems()
      .then(() => alive && setOnline(true))
      .catch(() => alive && setOnline(false));
    loadHeader();
    const timer = setInterval(loadHeader, 15000);      // cap nhat chuong thong bao moi 15 giay
    return () => { alive = false; clearInterval(timer); };
  }, [user, loadProblems, loadHeader]);

  const onSubmitted = useCallback(() => { setReloadKey((k) => k + 1); }, []);
  // Tra ve Promise de trang quan tri doi tai lai danh sach xong moi dong hop thoai.
  const onProblemsChanged = useCallback(async () => { await loadProblems().catch(() => undefined); setReloadKey((k) => k + 1); }, [loadProblems]);

  const go = useCallback((next: PageKey) => { setOpenProblemId(null); setPage(next); }, []);
  const openProblem = useCallback((id: string) => { setOpenProblemId(id); setPage("Problems"); }, []);

  function handleLogout() { logout(); setPage("Problems"); setAvatarOpen(false); }

  if (checking) {
    return (
      <div style={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--color-page-bg)" }}>
        <span style={{ fontSize: 13, color: "var(--color-text-muted)" }}>đang kiểm tra phiên đăng nhập...</span>
      </div>
    );
  }

  if (!user) return <LoginModal />;

  return (
    <NavContext.Provider value={{ go, openProblem }}>
      <div style={{ height: "100%", display: "flex", flexDirection: "column", background: "var(--color-page-bg)" }}>
        {/* ═══ THANH TREN CUNG ═══ */}
        <header style={{ display: "flex", alignItems: "center", height: 48, padding: "0 20px", background: "var(--color-header)", flexShrink: 0, zIndex: 50 }}>
          <div onClick={() => go("Problems")} style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer", userSelect: "none" }}>
            {/* Nen trang de o do giua khong bi chim vao header do */}
            <div style={{ width: 30, height: 30, borderRadius: 8, background: "#fff", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Logo size={18} />
            </div>
            <span style={{ fontSize: 18, fontWeight: 800, color: "#fff", letterSpacing: "-0.01em" }}>CodeHub</span>
          </div>

          <div style={{ flex: 1 }} />

          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            {/* Cham trang thai ket noi may chu */}
            <span title={online === false ? "Mất kết nối máy chủ" : "Đã kết nối máy chủ"} style={{
              width: 6, height: 6, borderRadius: "50%",
              background: online === false ? "#ff5252" : online ? "#69f0ae" : "rgba(255,255,255,0.4)",
              animation: online === null ? "livePulse 1.4s infinite" : "none",
            }} />

            {/* Chuong thong bao (Module 29) */}
            <button onClick={() => go("Notifications")} title="Thông báo"
              style={{ position: "relative", background: "none", border: "none", color: "rgba(255,255,255,0.9)", cursor: "pointer", padding: 4, display: "flex" }}>
              <svg width="18" height="18" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M4 6a4 4 0 0 1 8 0c0 4 2 5 2 5H2s2-1 2-5" /><path d="M6.5 13a1.5 1.5 0 0 0 3 0" /></svg>
              {unread > 0 && (
                <span style={{ position: "absolute", top: -2, right: -4, minWidth: 16, height: 16, borderRadius: 8, background: "#FFEB3B", color: "#B71C1C", fontSize: 10, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center", padding: "0 3px" }}>
                  {unread}
                </span>
              )}
            </button>

            {/* Tai khoan */}
            <div style={{ position: "relative" }}>
              <button onClick={() => setAvatarOpen((v) => !v)}
                style={{ display: "flex", alignItems: "center", gap: 8, background: "none", border: "none", cursor: "pointer", padding: "4px 6px", borderRadius: 20 }}>
                <div style={{ width: 28, height: 28, borderRadius: "50%", background: "#E57373", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 11, fontWeight: 700, color: "#fff" }}>
                  {user.username.slice(0, 2).toUpperCase()}
                </div>
                <span style={{ fontSize: 13, fontWeight: 600, color: "#fff" }}>{user.fullName || user.username}</span>
              </button>

              {avatarOpen && (
                <div onMouseLeave={() => setAvatarOpen(false)} style={{
                  position: "absolute", top: "calc(100% + 6px)", right: 0, background: "#fff", border: "1px solid #E0E0E0",
                  borderRadius: 10, boxShadow: "0 8px 30px rgba(0,0,0,0.12)", minWidth: 200, padding: 6, zIndex: 200,
                }}>
                  <div style={{ padding: "10px 14px 8px", borderBottom: "1px solid #F0F0F0", marginBottom: 4 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "#212121" }}>{user.fullName || user.username}</div>
                    <div style={{ fontSize: 11, color: "#9E9E9E", fontFamily: "var(--font-mono)" }}>@{user.username} · {user.roleName}</div>
                  </div>
                  <button onClick={() => { go("Account"); setAvatarOpen(false); }} style={MENU_ITEM}>Tài khoản của tôi</button>
                  <button onClick={handleLogout} style={{ ...MENU_ITEM, color: "#F44336" }}>Đăng xuất</button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Thong bao chung do quan tri vien dat (Module 39) */}
        {announcement && (
          <div style={{ padding: "6px 20px", background: "#FFF8E1", borderBottom: "1px solid #FFE082", fontSize: 13, color: "#8D6E00", flexShrink: 0 }}>
            📢 {announcement}
          </div>
        )}

        <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>
          {/* ═══ THANH DIEU HUONG BEN TRAI ═══ */}
          <nav style={{ width: 212, flexShrink: 0, background: "#fff", borderRight: "1px solid #E0E0E0", overflow: "auto", padding: "10px 8px 20px" }}>
            {NAV_GROUPS.map((group) => {
              const items = group.items.filter((item) => allowed(item.key));
              if (items.length === 0) return null;
              return (
                <div key={group.title} style={{ marginBottom: 10 }}>
                  <div style={{ padding: "6px 10px 4px", fontSize: 10.5, fontWeight: 700, color: "#9E9E9E", textTransform: "uppercase", letterSpacing: "0.08em" }}>{group.title}</div>
                  {items.map((item) => {
                    const active = page === item.key;
                    return (
                      <button key={item.key} onClick={() => go(item.key)} style={{
                        width: "100%", textAlign: "left", padding: "7px 10px", border: "none", borderRadius: 6, cursor: "pointer",
                        fontSize: 13, fontWeight: active ? 700 : 500,
                        background: active ? "var(--color-red-light)" : "transparent",
                        color: active ? "var(--color-red)" : "#424242",
                      }}>
                        {item.label}
                        {item.key === "Notifications" && unread > 0 && <span style={{ marginLeft: 6, fontSize: 11, color: "var(--color-red)" }}>({unread})</span>}
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </nav>

          {/* ═══ NOI DUNG ═══ */}
          <main style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minWidth: 0 }}>
            {online === false ? (
              <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12 }}>
                <div style={{ fontSize: 14, color: "#F44336", fontWeight: 600 }}>Không kết nối được máy chủ</div>
                <div style={{ fontSize: 13, color: "#9E9E9E", textAlign: "center", lineHeight: 1.7 }}>
                  Chạy <code style={{ color: "var(--color-red)", background: "#FDECEA", padding: "2px 6px", borderRadius: 4 }}>java ... com.ptit.oj.Main --serve</code> (xem README) rồi tải lại trang.
                </div>
              </div>
            ) : !allowed(page) ? null : (
              <>
                {page === "Problems" && <Problems problems={problems} topics={topics} languages={languages} onSubmitted={onSubmitted} reloadKey={reloadKey} openId={openProblemId} />}
                {page === "Classrooms" && <Classrooms />}
                {page === "Courses" && <Courses />}
                {page === "Assignments" && <Assignments />}
                {page === "Contests" && <Contests />}
                {page === "Meetings" && <Meetings languages={languages} />}
                {page === "Playground" && <Playground languages={languages} />}
                {page === "CpTools" && <CpTools />}
                {page === "StressTest" && <StressTest languages={languages} />}
                {page === "Assistant" && <Assistant problems={problems} />}
                {page === "Analytics" && <Analytics />}
                {page === "Achievements" && <Achievements />}
                {page === "Billing" && <Billing />}
                {page === "Account" && <MyAccount />}
                {page === "Forum" && <Forum />}
                {page === "Chat" && <ChatPage />}
                {page === "Notifications" && <Notifications onRead={loadHeader} />}
                {page === "Submissions" && <Submissions reloadKey={reloadKey} />}
                {page === "MySubmissions" && <MySubmissions />}
                {page === "Leaderboard" && <Leaderboard reloadKey={reloadKey} problems={problems} />}
                {page === "Dashboard" && <Dashboard />}
                {page === "TeachingAssistant" && <TeachingAssistant />}
                {page === "Parents" && <Parents />}
                {page === "Admin" && <AdminPanel onProblemsChanged={onProblemsChanged} problems={problems} topics={topics} />}
                {page === "Accounts" && <Accounts />}
                {page === "Organizations" && <Organizations />}
                {page === "System" && <SystemAdmin onSettingsChanged={loadHeader} />}
                {page === "Audit" && <AuditLogs />}
                {page === "ModuleMap" && <ModuleMap allowed={allowed} />}
              </>
            )}
          </main>
        </div>
      </div>
    </NavContext.Provider>
  );
}

const MENU_ITEM: React.CSSProperties = {
  width: "100%", display: "block", padding: "8px 14px", background: "none", border: "none",
  color: "#424242", fontSize: 13, cursor: "pointer", borderRadius: 6, textAlign: "left",
};

export default function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
