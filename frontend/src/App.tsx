import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { api } from "./shared/api";
import { papi } from "./shared/platformApi";
import { NavContext, type PageKey } from "./shared/nav";
import type { LanguageInfo, Problem, Topic } from "./shared/types";
import Logo from "./shared/Logo";
import { AuthProvider, useAuth } from "./view/account/auth";
import LoginModal from "./view/account/LoginModal";
import MyAccount from "./view/account/MyAccount";
import StreakBadge, { StreakToast } from "./view/account/StreakBadge";
import { useStreak } from "./view/account/streak";
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

/**
 * Menu tren thanh do tren cung, chia theo nhom: thanh chi hien ten nhom, re chuot vao
 * nhom nao thi xo xuong cac trang cua nhom do. Truong "role" gioi han ai thay muc do:
 * "staff" = giang vien va quan tri, "teacher" = chi giang vien, "admin" = chi quan tri.
 * Day chi la an / hien tren giao dien - may chu van kiem tra lai quyen o tung request.
 */
interface NavItem { key: PageKey; label: string; role?: "staff" | "teacher" | "admin" }

/** Icon net cho ten nhom tren thanh menu: khung 24x24, net 2px, bo tron dau net. */
function NavIcon({ children }: { children: ReactNode }) {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ marginRight: 6, flexShrink: 0 }}>
      {children}
    </svg>
  );
}

const NAV_GROUPS: { title: string; icon: ReactNode; items: NavItem[] }[] = [
  {
    // Quyen sach co dai danh dau
    title: "Học tập",
    icon: <NavIcon><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" /><path d="M10 2v8l3-3 3 3V2" /></NavIcon>,
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
    // Cap ngoac nhon cua ma nguon
    title: "Công cụ",
    icon: <NavIcon><path d="m16 18 6-6-6-6" /><path d="m8 6-6 6 6 6" /></NavIcon>,
    items: [
      { key: "Playground", label: "IDE trực tuyến" },
      { key: "CpTools", label: "Công cụ CP" },
      { key: "StressTest", label: "Stress test" },
      { key: "Assistant", label: "Trợ lý AI" },
    ],
  },
  {
    // Hinh nguoi
    title: "Cá nhân",
    icon: <NavIcon><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></NavIcon>,
    items: [
      { key: "Analytics", label: "Phân tích học tập" },
      { key: "Achievements", label: "Thành tích" },
      { key: "Billing", label: "Gói dịch vụ" },
      { key: "Account", label: "Tài khoản của tôi" },
    ],
  },
  {
    // Bong thoai
    title: "Cộng đồng",
    icon: <NavIcon><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></NavIcon>,
    items: [
      { key: "Forum", label: "Diễn đàn" },
      { key: "Chat", label: "Chat" },
      { key: "Notifications", label: "Thông báo" },
    ],
  },
  {
    // Khung danh sach
    title: "Lịch sử",
    icon: <NavIcon><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M7 8h10M7 12h10M7 16h6" /></NavIcon>,
    items: [
      { key: "Submissions", label: "Trạng thái" },
      { key: "MySubmissions", label: "Bài nộp" },
      { key: "Leaderboard", label: "Xếp hạng" },
    ],
  },
  {
    // Mu tot nghiep
    title: "Giảng dạy",
    icon: <NavIcon><path d="M22 10 12 5 2 10l10 5 10-5Z" /><path d="M6 12v5c0 1.1 2.7 3 6 3s6-1.9 6-3v-5" /></NavIcon>,
    items: [
      { key: "Dashboard", label: "Tổng quan", role: "staff" },
      { key: "TeachingAssistant", label: "Trợ lý giảng dạy", role: "staff" },
      { key: "Parents", label: "Phụ huynh", role: "staff" },
      { key: "MyProblems", label: "Kho bài của tôi", role: "teacher" },
    ],
  },
  {
    // Khien bao ve
    title: "Quản trị",
    icon: <NavIcon><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" /></NavIcon>,
    items: [
      { key: "Admin", label: "Kho bài chung và bộ test", role: "admin" },
      { key: "Accounts", label: "Tài khoản, giảng viên", role: "admin" },
      { key: "Organizations", label: "Tổ chức", role: "admin" },
      { key: "System", label: "Hệ thống", role: "admin" },
      { key: "Audit", label: "Nhật ký hoạt động", role: "admin" },
    ],
  },
];

function AppShell() {
  const { user, checking, logout } = useAuth();
  const [page, setPage] = useState<PageKey>("Problems");
  /** Ma bai can mo san khi chuyen sang trang Lop hoc (bam tu mot duong link toi bai). */
  const [openProblemId, setOpenProblemId] = useState<string | null>(null);
  /** Tang moi lan nguoi dung chon ve trang bai tap, de trang do dong bai dang mo va hien lai danh sach. */
  const [homeKey, setHomeKey] = useState(0);
  const [avatarOpen, setAvatarOpen] = useState(false);
  /** Ten nhom menu dang xo xuong (dang re chuot vao); null = khong nhom nao mo. */
  const [openGroup, setOpenGroup] = useState<string | null>(null);

  const [problems, setProblems] = useState<Problem[]>([]);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [languages, setLanguages] = useState<LanguageInfo[]>([]);
  const [online, setOnline] = useState<boolean | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [unread, setUnread] = useState(0);
  const [announcement, setAnnouncement] = useState("");

  // Chuoi ngay luyen tap (ngon lua tren header), tinh lai moi khi vua nop bai xong.
  const streak = useStreak(user?.username ?? null, reloadKey);

  const isAdmin = user?.role === "ADMIN";
  const isStaff = user?.role === "ADMIN" || user?.role === "TEACHER";
  const isTeacher = user?.role === "TEACHER";

  /** Nguoi dang dang nhap co duoc vao trang nay khong (theo truong role cua NAV_GROUPS). */
  const allowed = useCallback((key: PageKey) => {
    for (const group of NAV_GROUPS) {
      for (const item of group.items) {
        if (item.key !== key) continue;
        if (item.role === "admin") return isAdmin;
        if (item.role === "staff") return isStaff;
        if (item.role === "teacher") return isTeacher;
      }
    }
    return true;
  }, [isAdmin, isStaff, isTeacher]);

  // Hai kho bai: kho chung (owner rong) hien o lo trinh hoc; kho rieng la bai giang vien tu tao.
  const sharedProblems = useMemo(() => problems.filter((p) => !p.owner), [problems]);
  const myProblems = useMemo(() => problems.filter((p) => !!p.owner && p.owner === user?.username), [problems, user]);

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

  const go = useCallback((next: PageKey) => {
    setOpenProblemId(null); setOpenGroup(null); setPage(next);
    if (next === "Problems") setHomeKey((k) => k + 1);
  }, []);
  const openProblem = useCallback((id: string) => {
    // Bai kho rieng vua duoc dua vao ky thi / bai tap co the chua nam trong danh sach da tai: tai lai.
    if (!problems.some((p) => p.id === id)) void loadProblems().catch(() => undefined);
    setOpenProblemId(id);
    setPage("Problems");
  }, [problems, loadProblems]);

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
            <Logo size={30} />
          </div>

          {/* ═══ MENU: chi hien ten nhom, re chuot vao thi xo xuong cac trang con ═══ */}
          <nav style={{ display: "flex", height: "100%", marginLeft: 24 }}>
            {NAV_GROUPS.map((group) => {
              const items = group.items.filter((item) => allowed(item.key));
              if (items.length === 0) return null;
              const active = items.some((item) => item.key === page);     // dang o mot trang cua nhom nay
              const open = openGroup === group.title;
              return (
                // onMouseLeave dat o khung bao ca ten nhom lan danh sach con: chuot di tu ten nhom
                // xuong danh sach van nam trong khung nen menu khong bi dong.
                <div key={group.title} style={{ position: "relative", height: "100%" }}
                  onMouseEnter={() => setOpenGroup(group.title)} onMouseLeave={() => setOpenGroup(null)}>
                  <button onClick={() => setOpenGroup(open ? null : group.title)} style={{
                    height: "100%", padding: "0 10px", border: "none", cursor: "pointer", whiteSpace: "nowrap",
                    display: "flex", alignItems: "center",
                    background: active || open ? "rgba(255,255,255,0.18)" : "transparent",
                    color: "#fff", fontSize: 13.5, fontWeight: active ? 700 : 500,
                  }}>
                    {group.icon}
                    {group.title}
                    {group.title === "Cộng đồng" && unread > 0 && <span style={{ marginLeft: 5, fontSize: 11, color: "#FFEB3B" }}>({unread})</span>}
                    <span style={{ marginLeft: 5, fontSize: 9, opacity: 0.8 }}>▼</span>
                  </button>
                  {open && (
                    <div style={{
                      position: "absolute", top: "100%", left: 0, minWidth: 210, padding: 6, zIndex: 200,
                      background: "#fff", border: "1px solid #E0E0E0", borderRadius: "0 0 10px 10px",
                      boxShadow: "0 8px 30px rgba(0,0,0,0.14)",
                    }}>
                      {items.map((item) => {
                        const current = page === item.key;
                        return (
                          <button key={item.key} onClick={() => go(item.key)} style={{
                            ...MENU_ITEM, fontWeight: current ? 700 : 500, whiteSpace: "nowrap",
                            background: current ? "var(--color-red-light)" : "none",
                            color: current ? "var(--color-red)" : "#424242",
                          }}
                            onMouseEnter={(e) => { if (!current) e.currentTarget.style.background = "#F5F5F5"; }}
                            onMouseLeave={(e) => { if (!current) e.currentTarget.style.background = "none"; }}>
                            {item.label}
                            {item.key === "Notifications" && unread > 0 && <span style={{ marginLeft: 6, fontSize: 11, color: "var(--color-red)" }}>({unread})</span>}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </nav>

          <div style={{ flex: 1 }} />

          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            {/* Cham trang thai ket noi may chu */}
            <span title={online === false ? "Mất kết nối máy chủ" : "Đã kết nối máy chủ"} style={{
              width: 6, height: 6, borderRadius: "50%",
              background: online === false ? "#ff5252" : online ? "#69f0ae" : "rgba(255,255,255,0.4)",
              animation: online === null ? "livePulse 1.4s infinite" : "none",
            }} />

            {/* Chuoi ngay luyen tap: bam vao ngon lua de xem chi tiet */}
            <StreakBadge info={streak.info} />

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

        {/* Loi chuc mung bat len khi chuoi ngay luyen tap vua tang sau mot lan nop bai */}
        <StreakToast value={streak.increasedTo} onClose={streak.dismissIncrease} />

        {/* Thong bao chung do quan tri vien dat (Module 39) */}
        {announcement && (
          <div style={{ padding: "6px 20px", background: "#FFF8E1", borderBottom: "1px solid #FFE082", fontSize: 13, color: "#8D6E00", flexShrink: 0 }}>
            📢 {announcement}
          </div>
        )}

        <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>
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
                {page === "Problems" && <Problems problems={sharedProblems} allProblems={problems} topics={topics} languages={languages} onSubmitted={onSubmitted} reloadKey={reloadKey} openId={openProblemId} homeKey={homeKey} />}
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
                {page === "Leaderboard" && <Leaderboard reloadKey={reloadKey} problems={sharedProblems} />}
                {page === "Dashboard" && <Dashboard />}
                {page === "TeachingAssistant" && <TeachingAssistant />}
                {page === "Parents" && <Parents />}
                {page === "MyProblems" && <AdminPanel mine onProblemsChanged={onProblemsChanged} problems={myProblems} topics={topics} />}
                {page === "Admin" && <AdminPanel onProblemsChanged={onProblemsChanged} problems={sharedProblems} topics={topics} />}
                {page === "Accounts" && <Accounts />}
                {page === "Organizations" && <Organizations />}
                {page === "System" && <SystemAdmin onSettingsChanged={loadHeader} />}
                {page === "Audit" && <AuditLogs />}
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
