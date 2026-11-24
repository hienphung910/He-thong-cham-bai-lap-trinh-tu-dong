import { papi } from "../../shared/platformApi";
import { Bars, Btn, Card, ErrorBox, Muted, Page, Progress, Stat, Table, Tag, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { DIFFICULTY_META } from "../problem/learningPath";

/**
 * Module 23 (phan tich ket qua), 24 (danh gia nang luc), 26 (goi y bai tap). Ca ba phan doc tu
 * mot lan goi /api/analytics/...
 *
 * Khong truyen username: hien ho so cua chinh nguoi dang dang nhap (muc Ca nhan tren menu).
 * Co username: giang vien dang xem ho so cua mot hoc sinh trong lop minh - mo tu danh sach hoc
 * sinh o trang Lop cua toi; may chu tu choi neu hoc sinh do khong thuoc lop giang vien quan ly.
 */
export default function Analytics({ username, onBack }: { username?: string; onBack?: () => void }) {
  const profile = useLoad(() => (username ? papi.profileOf(username) : papi.myProfile()), [username]);
  const p = profile.data;
  const started = (p?.topics ?? []).filter((t) => t.total > 0);

  return (
    <Page title={username ? `Phân tích học tập — ${p ? p.user.fullName : username}` : "Phân tích học tập"}
      subtitle={p ? `Hồ sơ của ${p.user.fullName} (@${p.user.username})` : ""}
      actions={onBack ? <Btn kind="ghost" onClick={onBack}>← Quay lại lớp</Btn> : undefined}>
      <ErrorBox text={profile.error} />
      {p && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <Stat label="Lượt nộp" value={p.totalSubmissions} />
            <Stat label="Bài đã giải" value={p.solvedCount} color="var(--color-green)" />
            <Stat label="Tỉ lệ AC" value={`${p.acRate}%`} color="var(--color-blue)" />
            <Stat label="Chuỗi ngày học" value={p.streak} color="var(--color-orange)" />
          </div>

          <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
            <Card title="Số lượt nộp 14 ngày gần đây" style={{ flex: "2 1 420px", minWidth: 0 }}>
              <Bars items={p.activity.map((a) => ({ label: a.key.slice(8), value: a.count }))} />
            </Card>
            <Card title="Kết quả chấm" style={{ flex: "1 1 280px", minWidth: 0 }}>
              <Bars color="var(--color-blue)" items={p.verdicts.filter((v) => v.key !== "PE" && v.key !== "IE").map((v) => ({ label: v.key, value: v.count }))} />
            </Card>
          </div>

          <Card title="Đánh giá năng lực theo chủ đề">
            {started.length === 0 && <Muted>Chưa nộp bài nào trong lộ trình.</Muted>}
            {started.length > 0 && (
              <Table head={["Chủ đề", "Lớp", "Đã giải", "Điểm kỹ năng", "Mức"]}
                rows={started.map((t) => [
                  <b>{t.name}</b>, t.group, `${t.solved}/${t.total}`,
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}><Progress value={t.score} /><span>{t.score}</span></div>,
                  <Tag color={t.score >= 80 ? "green" : t.score >= 50 ? "blue" : t.score >= 20 ? "orange" : "gray"}>{t.level}</Tag>,
                ])} />
            )}
          </Card>

          <Card title={username ? "Bài tập gợi ý" : "Bài tập gợi ý cho bạn"}>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {p.recommendations.length === 0 && <Muted>Đã giải hết các bài trong lộ trình.</Muted>}
              {p.recommendations.map((r) => {
                const diff = DIFFICULTY_META[r.difficulty];
                return (
                  <div key={r.problemId} style={{ display: "flex", gap: 10, alignItems: "center", fontSize: 13.5, flexWrap: "wrap" }}>
                    <ProblemChip id={r.problemId} title={r.title} />
                    <b>{r.title}</b>
                    {diff && <span style={{ fontSize: 11, fontWeight: 700, padding: "1px 8px", borderRadius: 10, color: diff.color, background: diff.bg }}>{diff.label}</span>}
                    <span style={{ color: "#9E9E9E" }}>{r.reason}</span>
                  </div>
                );
              })}
            </div>
          </Card>
        </>
      )}
    </Page>
  );
}
