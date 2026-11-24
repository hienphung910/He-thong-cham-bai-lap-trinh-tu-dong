import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { ErrorBox, Muted, Page, Tag, fmtTime, useLoad } from "../../shared/kit";
import { useNav, type PageKey } from "../../shared/nav";

/** Ten trang dich de ghi tren thong bao ("Mo Lich hoc ->"). Trang khong co o day thi khong bam duoc. */
const PAGE_LABEL: Partial<Record<PageKey, string>> = {
  Meetings: "Lịch học",
  Assignments: "Bài tập được giao",
  Classrooms: "Lớp của tôi",
  Contests: "Kỳ thi",
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

/**
 * Module 29 (thong bao): danh sach thong bao cua toi. May chu tu tao thong bao khi co bai tap
 * moi, lich hoc moi, bai luyen tap moi, binh luan duoc duyet, hoac khi giang vien gui thong bao
 * cho lop. Mo trang nay la danh dau da doc het. Thong bao co trang dich thi bam vao la toi trang do.
 */
export default function Notifications({ onRead }: { onRead: () => void }) {
  const list = useLoad(() => papi.notifications());
  const { go, openProblem } = useNav();
  const [hover, setHover] = useState<string | null>(null);

  useEffect(() => {
    if (!list.data || list.data.unread === 0) return;
    papi.markNotificationsRead().then(onRead).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.data]);

  return (
    <Page title="Thông báo" subtitle="50 thông báo gần nhất của bạn. Bấm vào một thông báo để mở trang liên quan.">
      <ErrorBox text={list.error} />
      {list.data?.items.length === 0 && <Muted>Bạn chưa có thông báo nào.</Muted>}
      {(list.data?.items ?? []).map((n) => {
        const target = targetOf(n.link);
        const open = target ? () => (target.problemId ? openProblem(target.problemId) : go(target.page)) : undefined;
        const active = target !== null && hover === n.id;
        return (
          <div
            key={n.id}
            role={target ? "button" : undefined}
            tabIndex={target ? 0 : undefined}
            onClick={open}
            onKeyDown={open ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); } } : undefined}
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
              {!n.read && <Tag color="red">Mới</Tag>}
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
