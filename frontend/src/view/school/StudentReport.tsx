import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, Muted, Page, fmtTime, useLoad } from "../../shared/kit";
import { PrintArea } from "../../shared/print";
import type { StudentReport as Report } from "../../shared/platformTypes";
import "./report.css";

const PERIODS = [
  { days: 7, label: "7 ngày" },
  { days: 30, label: "30 ngày" },
  { days: 90, label: "3 tháng" },
];

const STATE: Record<Report["assignments"][number]["state"], { label: string; cls: string }> = {
  DONE: { label: "Hoàn thành đúng hạn", cls: "ok" },
  LATE: { label: "Hoàn thành, có bài nộp muộn", cls: "warn" },
  MISSED: { label: "Chưa hoàn thành", cls: "bad" },
  OPEN: { label: "Đang trong hạn", cls: "open" },
};

function dmy(iso: string) {
  const [y, m, d] = iso.slice(0, 10).split("-");
  return `${d}/${m}/${y}`;
}

/**
 * Bao cao hoc tap cua mot hoc sinh de gui phu huynh: so lieu trong ky (7 ngay / 30 ngay / 3 thang), bai tap
 * giao, ky thi, chu de manh / can co gang va nhan xet viet san. Giao vien sua nhan xet roi In / Luu PDF,
 * hoac sao chep thanh doan van ban de gui qua Zalo / tin nhan.
 */
export default function StudentReport({ classroomId, userId, onBack }: { classroomId: string; userId: string; onBack: () => void }) {
  const [days, setDays] = useState(30);
  const report = useLoad(() => papi.studentReport(classroomId, userId, days), [classroomId, userId, days]);
  const r = report.data;
  const [comment, setComment] = useState("");
  const [copied, setCopied] = useState(false);

  // Moi lan tai bao cao (doi ky) thi lay lai nhan xet viet san.
  useEffect(() => { if (r) setComment(r.comment); }, [r]);

  async function copyText() {
    if (!r) return;
    try {
      await navigator.clipboard.writeText(plainText(r, comment));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Page title="Báo cáo học tập" subtitle={r ? `${r.fullName} - ${r.classroomName}` : ""}
      actions={<>
        <div className="rp-periods" role="tablist" aria-label="Khoảng thời gian">
          {PERIODS.map((p) => (
            <button key={p.days} role="tab" aria-selected={days === p.days} className="rp-period" onClick={() => setDays(p.days)}>{p.label}</button>
          ))}
        </div>
        <Btn kind="ghost" disabled={!r} onClick={() => void copyText()}>{copied ? "Đã sao chép" : "Sao chép nội dung"}</Btn>
        <Btn disabled={!r} onClick={() => window.print()}>In / Lưu PDF</Btn>
        <Btn kind="ghost" onClick={onBack}>← Quay lại lớp</Btn>
      </>}>
      <ErrorBox text={report.error} />
      {!r && !report.error && <Muted>Đang tạo báo cáo...</Muted>}
      {r && (
        <>
          <div className="rp-screen">
            <ReportSheet r={r} comment={comment} editor={
              <div>
                <textarea className="rp-comment-edit" value={comment} rows={6} onChange={(e) => setComment(e.target.value)} />
                <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 6 }}>
                  <Btn small kind="ghost" disabled={comment === r.comment} onClick={() => setComment(r.comment)}>Dùng lại nhận xét tự động</Btn>
                </div>
              </div>
            } />
          </div>
          <PrintArea><ReportSheet r={r} comment={comment} /></PrintArea>
        </>
      )}
    </Page>
  );
}

/** Noi dung bao cao - dung chung cho man hinh va ban in. editor: o sua nhan xet (chi tren man hinh). */
function ReportSheet({ r, comment, editor }: { r: Report; comment: string; editor?: React.ReactNode }) {
  const trend = r.newSolved - r.prevNewSolved;
  return (
    <div className="rp-sheet">
      <div className="rp-head">
        <div>
          <div className="rp-brand">CodeHub</div>
          <h2 className="rp-title">Báo cáo học tập</h2>
          <div className="rp-sub">Từ {dmy(r.from)} đến {dmy(r.to)}</div>
        </div>
        <table className="rp-info">
          <tbody>
            <tr><th>Học sinh</th><td>{r.fullName}</td></tr>
            <tr><th>Lớp</th><td>{r.classroomName}</td></tr>
            {r.teacherName && <tr><th>Giáo viên</th><td>{r.teacherName}</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="rp-kpis">
        <Kpi value={`${r.activeDays}/${r.days}`} label="Ngày có luyện tập" />
        <Kpi value={r.newSolved} label="Bài mới giải được"
          note={r.prevNewSolved > 0 || trend !== 0 ? (trend > 0 ? `+${trend} so với kỳ trước` : trend < 0 ? `${trend} so với kỳ trước` : "bằng kỳ trước") : undefined} />
        <Kpi value={`${r.acRate}%`} label={`Tỉ lệ đúng (${r.submissions} lần nộp)`} />
        <Kpi value={r.classRank > 0 ? `${r.classRank}/${r.classSize}` : "-"} label="Thứ hạng trong lớp" />
      </div>

      <section className="rp-section">
        <h3>Bài tập được giao</h3>
        {r.assignments.length === 0 ? <p className="rp-empty">Không có bài tập nào trong kỳ này.</p> : (
          <table className="rp-table">
            <thead><tr><th>Bài tập</th><th>Hạn nộp</th><th>Đã làm</th><th>Kết quả</th></tr></thead>
            <tbody>
              {r.assignments.map((a, i) => (
                <tr key={i}>
                  <td>{a.title}</td>
                  <td>{fmtTime(a.dueAt)}</td>
                  <td>{a.done + a.late}/{a.total}</td>
                  <td><span className={`rp-badge rp-${STATE[a.state].cls}`}>{STATE[a.state].label}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {r.contests.length > 0 && (
        <section className="rp-section">
          <h3>Kỳ thi</h3>
          <table className="rp-table">
            <thead><tr><th>Kỳ thi</th><th>Ngày</th><th>Số bài giải</th><th>Xếp hạng</th></tr></thead>
            <tbody>
              {r.contests.map((k, i) => (
                <tr key={i}>
                  <td>{k.title}</td>
                  <td>{dmy(k.startAt)}</td>
                  <td>{k.solved}/{k.problemCount}</td>
                  <td>{k.rank > 0 ? `${k.rank}/${k.participants}` : "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {(r.strongTopics.length > 0 || r.weakTopics.length > 0) && (
        <section className="rp-section rp-topics">
          <div>
            <h3>Làm tốt</h3>
            {r.strongTopics.length === 0 ? <p className="rp-empty">-</p> : <ul>{r.strongTopics.map((t) => <li key={t}>{t}</li>)}</ul>}
          </div>
          <div>
            <h3>Cần luyện thêm</h3>
            {r.weakTopics.length === 0 ? <p className="rp-empty">-</p> : <ul>{r.weakTopics.map((t) => <li key={t}>{t}</li>)}</ul>}
          </div>
        </section>
      )}

      <section className="rp-section">
        <h3>Nhận xét của giáo viên</h3>
        {editor ?? <p className="rp-comment">{comment}</p>}
      </section>

      <div className="rp-foot">
        <span>Tổng số bài đã giải từ trước tới nay: {r.totalSolved}</span>
        {r.teacherName && <span className="rp-sign">Giáo viên<br /><br /><br />{r.teacherName}</span>}
      </div>
    </div>
  );
}

function Kpi({ value, label, note }: { value: string | number; label: string; note?: string }) {
  return (
    <div className="rp-kpi">
      <div className="rp-kpi-value">{value}</div>
      <div className="rp-kpi-label">{label}</div>
      {note && <div className="rp-kpi-note">{note}</div>}
    </div>
  );
}

/** Ban van ban de dan vao Zalo / tin nhan. */
function plainText(r: Report, comment: string): string {
  const lines = [
    `BÁO CÁO HỌC TẬP - ${r.fullName} (${r.classroomName})`,
    `Từ ${dmy(r.from)} đến ${dmy(r.to)}`,
    "",
    `- Số ngày luyện tập: ${r.activeDays}/${r.days}`,
    `- Bài mới giải được: ${r.newSolved}`,
    `- Tỉ lệ đúng: ${r.acRate}% (${r.submissions} lần nộp)`,
  ];
  if (r.classRank > 0) lines.push(`- Thứ hạng trong lớp: ${r.classRank}/${r.classSize}`);
  if (r.assignments.length > 0) {
    lines.push("", "Bài tập được giao:");
    for (const a of r.assignments) lines.push(`- ${a.title}: ${a.done + a.late}/${a.total} bài - ${STATE[a.state].label}`);
  }
  if (r.contests.length > 0) {
    lines.push("", "Kỳ thi:");
    for (const k of r.contests) lines.push(`- ${k.title}: ${k.solved}/${k.problemCount} bài${k.rank > 0 ? `, hạng ${k.rank}/${k.participants}` : ""}`);
  }
  lines.push("", "Nhận xét:", comment);
  if (r.teacherName) lines.push("", `Giáo viên: ${r.teacherName}`);
  return lines.join("\n");
}
