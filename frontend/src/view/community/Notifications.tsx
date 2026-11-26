import { useEffect, useRef, useState, type ReactNode } from "react";
import "./notifications.css";
import { confirmDialog } from "../../shared/dialog";
import { papi } from "../../shared/platformApi";
import { ErrorBox, Page, fmtTime } from "../../shared/kit";
import { useNav, type PageKey } from "../../shared/nav";
import type { NotificationItem, NotificationList } from "../../shared/platformTypes";

/** Ten trang dich de ghi tren thong bao ("Mo Lich hoc ->"). Trang khong co o day thi khong bam duoc. */
const PAGE_LABEL: Partial<Record<PageKey, string>> = {
  Meetings: "Lịch học",
  Assignments: "Bài tập được giao",
  Classrooms: "Lớp của tôi",
  Contests: "Kỳ thi",
  Accounts: "Tài khoản",
};

/** Doi truong link cua thong bao thanh nhan hien thi va viec can lam khi bam; null = khong bam duoc. */
function targetOf(link: string): { label: string; page: PageKey; problemId?: string } | null {
  if (link.startsWith("Problems/")) {
    const problemId = link.slice("Problems/".length);
    return problemId ? { label: `bài ${problemId}`, page: "Problems", problemId } : null;
  }
  const label = PAGE_LABEL[link as PageKey];
  return label ? { label, page: link as PageKey } : null;
}

/** Mo trang dich cua mot thong bao; thong bao khong co trang dich thi mo trang Thong bao. */
function useOpenNotification() {
  const { go, openProblem } = useNav();
  return (item: NotificationItem) => {
    const target = targetOf(item.link);
    if (!target) go("Notifications");
    else if (target.problemId) openProblem(target.problemId);
    else go(target.page);
  };
}

/** Thay doi dang cho may chu: hien ngay tren giao dien, bo di khi danh sach tai lai da khop. */
type Pending = Record<string, "read" | "unread" | "deleted">;

/** "vừa xong", "5 phút trước", "3 giờ trước", "2 ngày trước"; cu hon 7 ngay thi ghi ngay gio. */
function relativeTime(iso: string, now: number): string {
  const at = Date.parse(iso);
  if (Number.isNaN(at)) return fmtTime(iso);
  const minutes = Math.floor((now - at) / 60000);
  if (minutes < 1) return "vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return fmtTime(iso);
}

/**
 * Module 29 (thong bao): danh sach thong bao cua toi. May chu tu tao thong bao khi co bai tap
 * moi, lich hoc moi, bai luyen tap moi, binh luan duoc duyet, hoac khi giang vien gui thong bao
 * cho lop. Thong bao co trang dich thi bam vao la toi trang do.
 *
 * Giong cac nen tang khac: mo trang KHONG tu danh dau da doc. Bam vao mot thong bao thi thong bao
 * do thanh da doc; co nut danh dau tat ca da doc, danh dau lai chua doc, xoa tung thong bao, xoa
 * cac thong bao da doc va xoa tat ca (hai muc sau nam trong menu "...").
 *
 * Danh sach do App.tsx tai lai vai giay mot lan va truyen xuong; sau moi thao tac goi onChanged
 * de tai lai ngay (ca so thong bao chua doc tren thanh menu).
 */
export default function Notifications({ data, onChanged }: { data: NotificationList | null; onChanged: () => void }) {
  const open = useOpenNotification();
  const [onlyUnread, setOnlyUnread] = useState(false);
  const [pending, setPending] = useState<Pending>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  // Cap nhat "x phut truoc" moi phut.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  // Thay doi da duoc may chu ghi nhan (danh sach moi da khop) thi bo khoi pending.
  useEffect(() => {
    if (!data) return;
    setPending((current) => {
      const next: Pending = {};
      for (const [id, change] of Object.entries(current)) {
        const item = data.items.find((n) => n.id === id);
        const settled = change === "deleted" ? !item : item !== undefined && item.read === (change === "read");
        if (!settled) next[id] = change;
      }
      return Object.keys(next).length === Object.keys(current).length ? current : next;
    });
  }, [data]);

  const items = (data?.items ?? [])
    .filter((n) => pending[n.id] !== "deleted")
    .map((n) => (pending[n.id] ? { ...n, read: pending[n.id] === "read" } : n));
  const unread = items.filter((n) => !n.read).length;
  const shown = onlyUnread ? items.filter((n) => !n.read) : items;
  const readIds = items.filter((n) => n.read).map((n) => n.id);
  const unreadIds = items.filter((n) => !n.read).map((n) => n.id);

  async function change(ids: string[], kind: Pending[string], work: () => Promise<unknown>) {
    setError("");
    setPending((current) => {
      const next = { ...current };
      for (const id of ids) next[id] = kind;
      return next;
    });
    try {
      await work();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setPending((current) => {
        const next = { ...current };
        for (const id of ids) delete next[id];
        return next;
      });
    }
    onChanged();
  }

  async function bulk(ids: string[], kind: Pending[string], work: () => Promise<unknown>) {
    if (busy || ids.length === 0) return;
    setBusy(true);
    try { await change(ids, kind, work); } finally { setBusy(false); }
  }

  function clickItem(n: NotificationItem) {
    if (!n.read) void change([n.id], "read", () => papi.setNotificationRead(n.id, true));
    if (targetOf(n.link)) open(n);
  }

  return (
    <Page title="Thông báo">
      <ErrorBox text={error} />
      <div className="ntf-panel">
        <div className="ntf-toolbar">
          <div className="ntf-tabs" role="tablist" aria-label="Lọc thông báo">
            <button role="tab" aria-selected={!onlyUnread} className="ntf-tab" onClick={() => setOnlyUnread(false)}>
              Tất cả <span className="ntf-count">{items.length}</span>
            </button>
            <button role="tab" aria-selected={onlyUnread} className="ntf-tab" onClick={() => setOnlyUnread(true)}>
              Chưa đọc {unread > 0 && <span className="ntf-count ntf-count-accent">{unread}</span>}
            </button>
          </div>
          <div className="ntf-toolbar-actions">
            <button className="ntf-text-btn" disabled={busy || unread === 0}
              onClick={() => void bulk(unreadIds, "read", () => papi.markNotificationsRead())}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="m1.5 8.5 3 3 6-7" /><path d="m7.5 11.5 1 0 6-7" />
              </svg>
              Đánh dấu tất cả đã đọc
            </button>
            <div className="ntf-menu-wrap">
              <button className="ntf-icon-btn" aria-label="Thêm thao tác" title="Thêm thao tác" aria-haspopup="menu" aria-expanded={menuOpen}
                disabled={items.length === 0} onClick={() => setMenuOpen((v) => !v)}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <circle cx="3" cy="8" r="1.4" /><circle cx="8" cy="8" r="1.4" /><circle cx="13" cy="8" r="1.4" />
                </svg>
              </button>
              {menuOpen && (
                <Menu onClose={() => setMenuOpen(false)}>
                  <button role="menuitem" className="ntf-menu-item" disabled={busy || readIds.length === 0}
                    onClick={() => { setMenuOpen(false); void bulk(readIds, "deleted", () => papi.deleteNotifications(true)); }}>
                    <TrashIcon />Xoá thông báo đã đọc
                    <span className="ntf-menu-hint">{readIds.length}</span>
                  </button>
                  <div className="ntf-menu-sep" />
                  <button role="menuitem" className="ntf-menu-item ntf-menu-danger" disabled={busy}
                    onClick={() => {
                      setMenuOpen(false);
                      void confirmDialog({
                        title: "Xoá tất cả thông báo?",
                        message: `${items.length} thông báo sẽ bị xoá vĩnh viễn và không khôi phục được.`,
                        confirmLabel: "Xoá tất cả",
                      }).then((ok) => { if (ok) void bulk(items.map((n) => n.id), "deleted", () => papi.deleteNotifications(false)); });
                    }}>
                    <TrashIcon />Xoá tất cả thông báo
                  </button>
                </Menu>
              )}
            </div>
          </div>
        </div>

        {data === null && <div className="ntf-empty">Đang tải thông báo...</div>}
        {data !== null && shown.length === 0 && (
          <div className="ntf-empty">
            <svg width="36" height="36" viewBox="0 0 16 16" fill="none" stroke="#BDBDBD" strokeWidth="1" aria-hidden="true">
              <path d="M4 6a4 4 0 0 1 8 0c0 4 2 5 2 5H2s2-1 2-5" /><path d="M6.5 13a1.5 1.5 0 0 0 3 0" />
            </svg>
            <div>{onlyUnread && items.length > 0 ? "Bạn đã đọc hết thông báo." : "Bạn chưa có thông báo nào."}</div>
          </div>
        )}

        {shown.map((n) => {
          const target = targetOf(n.link);
          return (
            <div
              key={n.id}
              className={`ntf-row${n.read ? "" : " ntf-unread"}`}
              role="button"
              tabIndex={0}
              onClick={() => clickItem(n)}
              onKeyDown={(e) => { if (e.target === e.currentTarget && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); clickItem(n); } }}
            >
              <span className="ntf-dot" aria-label={n.read ? undefined : "Chưa đọc"} />
              <div className="ntf-body">
                <div className="ntf-title">{n.title}</div>
                <div className="ntf-text">{n.body}</div>
                <div className="ntf-meta">
                  <span title={fmtTime(n.createdAt)}>{relativeTime(n.createdAt, now)}</span>
                  {target && <><span aria-hidden="true">·</span><span className="ntf-link">Mở {target.label}</span></>}
                </div>
              </div>
              <div className="ntf-actions">
                <IconBtn label={n.read ? "Đánh dấu chưa đọc" : "Đánh dấu đã đọc"}
                  onClick={() => void change([n.id], n.read ? "unread" : "read", () => papi.setNotificationRead(n.id, !n.read))}>
                  {n.read
                    ? <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" aria-hidden="true"><rect x="2" y="3.5" width="12" height="9" rx="1.5" /><path d="m2.5 4.5 5.5 4 5.5-4" /></svg>
                    : <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 8.5 3 3 7-7" /></svg>}
                </IconBtn>
                <IconBtn label="Xoá thông báo" onClick={() => void change([n.id], "deleted", () => papi.deleteNotification(n.id))}>
                  <TrashIcon />
                </IconBtn>
              </div>
            </div>
          );
        })}
      </div>

    </Page>
  );
}

function TrashIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2.5 4.5h11M6.5 4.5V3h3v1.5M4 4.5l.7 8.5h6.6l.7-8.5M6.8 7v4M9.2 7v4" />
    </svg>
  );
}

/** Nut bieu tuong tren mot thong bao; khong lam mo thong bao khi bam. */
function IconBtn({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button className="ntf-icon-btn" aria-label={label} title={label}
      onClick={(e) => { e.stopPropagation(); onClick(); }}
      onKeyDown={(e) => e.stopPropagation()}>
      {children}
    </button>
  );
}

/** Menu tha xuong: dong khi bam ra ngoai hoac nhan Esc. */
function Menu({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      const wrap = ref.current?.parentElement;
      if (wrap && !wrap.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    ref.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
    return () => { document.removeEventListener("mousedown", onDown); document.removeEventListener("keydown", onKey); };
  }, [onClose]);
  return <div ref={ref} role="menu" className="ntf-menu">{children}</div>;
}

/**
 * Khung nho bat len o goc duoi ben phai khi vua co thong bao moi (dang o trang khac). Tu tat
 * sau 7 giay; bam vao thi mo trang lien quan, bam dau x thi dong.
 */
export function NotificationToast({ item, onClose }: { item: NotificationItem | null; onClose: () => void }) {
  const open = useOpenNotification();

  useEffect(() => {
    if (item === null) return;
    const timer = window.setTimeout(onClose, 7000);
    return () => window.clearTimeout(timer);
  }, [item, onClose]);

  if (item === null) return null;
  const target = targetOf(item.link);
  return (
    <div
      role="status"
      onClick={() => { open(item); onClose(); }}
      style={{
        position: "fixed", right: 24, bottom: 24, zIndex: 300, width: 340, maxWidth: "calc(100vw - 48px)", cursor: "pointer",
        background: "#161616", color: "#fff", borderRadius: 10, padding: "12px 14px 12px 16px",
        borderLeft: "4px solid var(--color-accent)", boxShadow: "0 10px 30px rgba(0,0,0,0.3)",
        animation: "slideIn 0.2s ease",
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
        <svg width="18" height="18" viewBox="0 0 16 16" fill="none" stroke="var(--color-accent)" strokeWidth="1.5" style={{ flexShrink: 0, marginTop: 1 }} aria-hidden="true">
          <path d="M4 6a4 4 0 0 1 8 0c0 4 2 5 2 5H2s2-1 2-5" /><path d="M6.5 13a1.5 1.5 0 0 0 3 0" />
        </svg>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 13.5, fontWeight: 700 }}>{item.title}</div>
          <div style={{ fontSize: 12.5, color: "#BDBDBD", marginTop: 3, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{item.body}</div>
          <div style={{ fontSize: 12, fontWeight: 600, color: "var(--color-accent)", marginTop: 6 }}>
            {target ? `Mở ${target.label} →` : "Xem thông báo →"}
          </div>
        </div>
        <button
          onClick={(e) => { e.stopPropagation(); onClose(); }} aria-label="Đóng"
          style={{ background: "none", border: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 16, lineHeight: 1, padding: 2 }}
        >×</button>
      </div>
    </div>
  );
}
