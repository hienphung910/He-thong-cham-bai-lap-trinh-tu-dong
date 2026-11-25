import { useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Muted, Page, Tag, fmtTime } from "../../shared/kit";
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

/**
 * Module 29 (thong bao): danh sach thong bao cua toi. May chu tu tao thong bao khi co bai tap
 * moi, lich hoc moi, bai luyen tap moi, binh luan duoc duyet, hoac khi giang vien gui thong bao
 * cho lop. Thong bao co trang dich thi bam vao la toi trang do.
 *
 * Danh sach do App.tsx tai lai vai giay mot lan va truyen xuong, nen thong bao moi tu hien ra
 * ma khong can tai lai trang. Mo trang nay la danh dau da doc het.
 */
export default function Notifications({ data, onRead }: { data: NotificationList | null; onRead: () => void }) {
  const open = useOpenNotification();
  const [hover, setHover] = useState<string | null>(null);
  // Thong bao chua doc luc minh nhin thay o trang nay: giu nhan "Moi" cho toi khi roi trang,
  // du ngay sau do may chu da danh dau la da doc.
  const freshIds = useRef(new Set<string>());
  for (const item of data?.items ?? []) {
    if (!item.read) freshIds.current.add(item.id);
  }

  useEffect(() => {
    if (!data || data.unread === 0) return;
    papi.markNotificationsRead().then(onRead).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  return (
    <Page title="Thông báo">
      {data === null && <Muted>Đang tải thông báo...</Muted>}
      {data?.items.length === 0 && <Muted>Bạn chưa có thông báo nào.</Muted>}
      {(data?.items ?? []).map((n) => {
        const target = targetOf(n.link);
        const active = target !== null && hover === n.id;
        return (
          <div
            key={n.id}
            role={target ? "button" : undefined}
            tabIndex={target ? 0 : undefined}
            onClick={target ? () => open(n) : undefined}
            onKeyDown={target ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(n); } } : undefined}
            onMouseEnter={() => setHover(n.id)} onMouseLeave={() => setHover(null)}
            title={target ? `Mở ${target.label}` : undefined}
            style={{
              background: active ? "var(--color-table-row-alt)" : "#fff", borderRadius: 8, padding: "16px 20px",
              border: `1px solid ${active ? "var(--color-accent)" : "#E0E0E0"}`,
              cursor: target ? "pointer" : "default", transition: "background 0.1s, border-color 0.1s",
            }}
          >
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <b style={{ fontSize: 14, color: "#212121" }}>{n.title}</b>
              {freshIds.current.has(n.id) && <Tag color="red">Mới</Tag>}
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: "#9E9E9E" }}>{fmtTime(n.createdAt)}</span>
            </div>
            <div style={{ fontSize: 13.5, color: "#616161", marginTop: 4, whiteSpace: "pre-wrap" }}>{n.body}</div>
            {target && (
              <div style={{ marginTop: 8, fontSize: 12.5, fontWeight: 600, color: "var(--color-red)" }}>Mở {target.label} →</div>
            )}
          </div>
        );
      })}
    </Page>
  );
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
