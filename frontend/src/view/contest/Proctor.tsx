import { useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Card, ErrorBox, INPUT, Muted, Table, Tag, useLoad } from "../../shared/kit";
import type { Contest, ProctorEventType } from "../../shared/platformTypes";

/** Ky thi dang duoc giam sat trong tab nay; nho lai de tai lai trang van tiep tuc giam sat. */
const ACTIVE_KEY = "oj-proctor-contest";

/** Ten hien thi cua tung loai su kien. */
const EVENT_LABEL: Record<ProctorEventType, { label: string; color: string }> = {
  TAB_HIDDEN: { label: "Rời tab", color: "red" },
  TAB_RETURN: { label: "Quay lại tab", color: "gray" },
  WINDOW_BLUR: { label: "Chuyển cửa sổ khác", color: "red" },
  WINDOW_RETURN: { label: "Quay lại cửa sổ", color: "gray" },
  COPY: { label: "Sao chép", color: "orange" },
  CUT: { label: "Cắt", color: "orange" },
  PASTE: { label: "Dán", color: "orange" },
  PAGE_CLOSED: { label: "Đóng / tải lại trang", color: "red" },
};

/**
 * Nut "tai file ma nguon" mo hop thoai chon file cua he dieu hanh, lam cua so trinh duyet mat
 * focus trong chot lat. Nut do goi ham nay ngay truoc khi mo hop thoai de lan mat focus ay
 * khong bi ghi nham la "chuyen sang cua so khac".
 */
let fileDialogAt = 0;
export function markFileDialog() {
  fileDialogAt = Date.now();
}

function seconds(since: number): string {
  return `vắng ${Math.max(1, Math.round((Date.now() - since) / 1000))} giây`;
}

/**
 * Giam sat thi sinh trong ky thi (chi gan cho tai khoan hoc sinh, xem App.tsx).
 *
 * Bat dau khi thi sinh mo mot bai thuoc ky thi DANG DIEN RA, va tiep tuc cho toi khi ky thi ket
 * thuc - ke ca khi thi sinh chuyen sang trang khac cua CodeHub. Trong luc do trinh duyet ghi lai:
 * roi tab, chuyen sang cua so khac, sao chep / cat / dan, dong hoac tai lai trang; moi su kien
 * duoc gui ngay len may chu de nguoi tao ky thi xem. Khong chan thao tac nao va khong ghi noi dung
 * duoc sao chep hay dan, chi ghi do dai. Thanh bao o dau trang cho thi sinh biet minh dang bi giam sat.
 */
export default function ContestProctor({ problemId }: { problemId: string | null }) {
  const [contests, setContests] = useState<Contest[]>([]);
  const [activeId, setActiveId] = useState(() => {
    try { return sessionStorage.getItem(ACTIVE_KEY) ?? ""; } catch { return ""; }
  });
  const [now, setNow] = useState(() => Date.now());
  const problemRef = useRef(problemId);
  problemRef.current = problemId;

  // Danh sach ky thi: tai luc dau, moi 30 giay, va moi lan mo mot bai (de bat duoc ky thi vua bat dau).
  useEffect(() => {
    let alive = true;
    const load = () => { papi.contests().then((list) => { if (alive) setContests(list); }).catch(() => undefined); };
    load();
    const timer = setInterval(load, 30_000);
    return () => { alive = false; clearInterval(timer); };
  }, [problemId]);

  const running = contests.filter((c) => c.status === "RUNNING" && Date.parse(c.endAt) > now);
  const entering = problemId ? running.find((c) => c.problems.some((p) => p.id === problemId)) : undefined;
  const active = running.find((c) => c.id === activeId) ?? null;

  // Mo mot bai cua ky thi dang dien ra -> bat dau giam sat ky thi do.
  useEffect(() => {
    if (!entering || entering.id === activeId) return;
    setActiveId(entering.id);
    try { sessionStorage.setItem(ACTIVE_KEY, entering.id); } catch { /* khong nho duoc thi tai lai trang se phai mo lai bai */ }
  }, [entering, activeId]);

  // Dong ho dem nguoc, va de biet ky thi da het gio ngay ca khi chua tai lai danh sach.
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);

  const contestId = active?.id ?? "";
  useEffect(() => {
    if (!contestId) return;
    // Moi loai su kien toi da mot lan moi giay: giu phim Ctrl+V khong tao ra hang tram dong.
    const lastSent: Partial<Record<ProctorEventType, number>> = {};
    const send = (type: ProctorEventType, detail = "") => {
      const at = Date.now();
      if (at - (lastSent[type] ?? 0) < 1000) return;
      lastSent[type] = at;
      papi.proctor(contestId, { type, detail, problemId: problemRef.current ?? "" }).catch(() => undefined);
    };

    let hiddenAt = 0;
    let blurAt = 0;
    let blurTimer: number | undefined;

    const onVisibility = () => {
      if (document.hidden) {
        hiddenAt = Date.now();
        send("TAB_HIDDEN");
      } else if (hiddenAt) {
        send("TAB_RETURN", seconds(hiddenAt));
        hiddenAt = 0;
      }
    };
    const onBlur = () => {
      if (Date.now() - fileDialogAt < 1500) return;      // hop thoai chon file cua chinh trang nay
      window.clearTimeout(blurTimer);
      // Doi mot chut: neu tab bi an thi su kien "roi tab" da ghi roi, khong ghi them lan nua.
      blurTimer = window.setTimeout(() => {
        if (!document.hidden && !document.hasFocus()) {
          blurAt = Date.now();
          send("WINDOW_BLUR");
        }
      }, 200);
    };
    const onFocus = () => {
      window.clearTimeout(blurTimer);
      if (blurAt) {
        send("WINDOW_RETURN", seconds(blurAt));
        blurAt = 0;
      }
    };
    const selectionLength = () => `${window.getSelection()?.toString().length ?? 0} ký tự`;
    const onCopy = () => send("COPY", selectionLength());
    const onCut = () => send("CUT", selectionLength());
    const onPaste = (e: ClipboardEvent) => send("PASTE", `${e.clipboardData?.getData("text").length ?? 0} ký tự`);
    const onPageHide = () => send("PAGE_CLOSED");

    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("blur", onBlur);
    window.addEventListener("focus", onFocus);
    window.addEventListener("pagehide", onPageHide);
    document.addEventListener("copy", onCopy, true);
    document.addEventListener("cut", onCut, true);
    document.addEventListener("paste", onPaste, true);
    return () => {
      window.clearTimeout(blurTimer);
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("blur", onBlur);
      window.removeEventListener("focus", onFocus);
      window.removeEventListener("pagehide", onPageHide);
      document.removeEventListener("copy", onCopy, true);
      document.removeEventListener("cut", onCut, true);
      document.removeEventListener("paste", onPaste, true);
    };
  }, [contestId]);

  if (!active) return null;
  const left = Math.max(0, Math.floor((Date.parse(active.endAt) - now) / 1000));
  const clock = `${String(Math.floor(left / 3600)).padStart(2, "0")}:${String(Math.floor((left % 3600) / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`;
  return (
    <div role="status" style={{
      flexShrink: 0, display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", padding: "7px 20px",
      background: "#161616", color: "#E0E0E0", fontSize: 12.5, borderBottom: "2px solid var(--color-accent)",
    }}>
      <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#FF5252", animation: "livePulse 1.4s infinite", flexShrink: 0 }} />
      <b style={{ color: "#fff" }}>Đang giám sát kỳ thi {active.title}</b>
      <span>Rời tab, chuyển cửa sổ, sao chép và dán đều được ghi lại và gửi cho giảng viên.</span>
      <span style={{ flex: 1 }} />
      <span style={{ fontFamily: "var(--font-mono)", color: "var(--color-accent)", fontWeight: 700 }}>Còn {clock}</span>
    </div>
  );
}

/** Lay gio:phut:giay tu "2026-11-24T10:59:03". */
function clockOf(iso: string): string {
  return iso.slice(11, 19);
}

/**
 * Bang giam sat tren trang ky thi, chi nguoi tao ky thi (va quan tri vien) thay: tong hop theo
 * thi sinh va nhat ky tung su kien. Tu tai lai moi 5 giay khi ky thi dang dien ra.
 */
export function ProctorPanel({ contestId, running }: { contestId: string; running: boolean }) {
  const log = useLoad(() => papi.proctorLog(contestId), [contestId]);
  const [student, setStudent] = useState("");
  const reload = log.reload;

  useEffect(() => {
    if (!running) return;
    const timer = setInterval(reload, 5000);
    return () => clearInterval(timer);
  }, [running, reload]);

  const students = log.data?.students ?? [];
  const events = (log.data?.events ?? []).filter((e) => !student || e.username === student);

  return (
    <Card title="Giám sát thí sinh" right={<>
      {running && <Tag color="green">Tự cập nhật mỗi 5 giây</Tag>}
      <Tag color={students.length > 0 ? "orange" : "gray"}>{students.length} thí sinh có ghi nhận</Tag>
    </>}>
      <ErrorBox text={log.error} />
      <Muted>
        Trình duyệt của thí sinh tự ghi lại khi các em rời tab, chuyển sang cửa sổ khác, sao chép, dán, hoặc đóng / tải lại trang
        trong lúc làm bài của kỳ thi. Đây là dấu hiệu để tham khảo, không phải bằng chứng chắc chắn.
      </Muted>

      <div style={{ height: 12 }} />
      <Table head={["Thí sinh", "Tổng", "Rời tab", "Chuyển cửa sổ", "Sao chép / cắt", "Dán", "Đóng / tải lại", "Gần nhất", ""]}
        empty="Chưa ghi nhận sự kiện nào."
        rows={students.map((s) => [
          <span><b>{s.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{s.username}</span></span>,
          <Tag color={s.violations >= 5 ? "red" : "orange"}>{s.violations}</Tag>,
          s.tabHidden, s.windowBlur, s.copy, s.paste, s.pageClosed, clockOf(s.lastAt),
          <button onClick={() => setStudent(student === s.username ? "" : s.username)} style={{
            background: "none", border: "none", padding: 0, cursor: "pointer", fontSize: 12.5, fontWeight: 600, color: "var(--color-red)",
          }}>{student === s.username ? "Bỏ lọc" : "Xem nhật ký"}</button>,
        ])} />

      <div style={{ display: "flex", alignItems: "center", gap: 10, margin: "16px 0 8px", flexWrap: "wrap" }}>
        <b style={{ fontSize: 13.5, color: "#212121" }}>Nhật ký sự kiện</b>
        <select style={{ ...INPUT, width: 240 }} value={student} onChange={(e) => setStudent(e.target.value)}>
          <option value="">Tất cả thí sinh</option>
          {students.map((s) => <option key={s.username} value={s.username}>{s.fullName} (@{s.username})</option>)}
        </select>
        <span style={{ fontSize: 12, color: "#9E9E9E" }}>
          {events.length} sự kiện{log.data && log.data.total > log.data.events.length ? ` (hiện ${log.data.events.length} sự kiện mới nhất trên tổng ${log.data.total})` : ""}
        </span>
      </div>
      <div style={{ maxHeight: 320, overflow: "auto" }}>
        <Table head={["Lúc", "Thí sinh", "Sự kiện", "Chi tiết", "Bài đang mở"]} empty="Không có sự kiện nào."
          rows={events.map((e) => [
            <span style={{ fontFamily: "var(--font-mono)", fontSize: 12.5 }}>{clockOf(e.at)}</span>,
            <span><b>{e.fullName}</b> <span style={{ color: "#9E9E9E" }}>@{e.username}</span></span>,
            <Tag color={EVENT_LABEL[e.type]?.color ?? "gray"}>{EVENT_LABEL[e.type]?.label ?? e.type}</Tag>,
            e.detail || "—",
            e.problemId ? <span style={{ fontFamily: "var(--font-mono)" }}>{e.problemId}</span> : "—",
          ])} />
      </div>
    </Card>
  );
}
