import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Bars, Btn, Card, ErrorBox, Field, INPUT, Muted, Page, Progress, Row, Stat, Table, Tag, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { DIFFICULTY_META } from "../problem/learningPath";
import { useAuth } from "../account/auth";

/**
 * Module 23 (phan tich ket qua), 24 (danh gia nang luc), 25 (phat hien diem yeu),
 * 26 (goi y bai tap). Ca bon phan doc tu mot lan goi /api/analytics/...
 * Giang vien nhap ten dang nhap de xem ho so cua mot hoc sinh.
 */
export default function Analytics() {
  const { user } = useAuth();
  const isStaff = user?.role !== "STUDENT";
  const [username, setUsername] = useState("");      // "" = xem cua chinh minh
  const [typing, setTyping] = useState("");
  const profile = useLoad(() => (username ? papi.profileOf(username) : papi.myProfile()), [username]);
  const p = profile.data;
  const weak = (p?.topics ?? []).filter((t) => t.weak);
  const started = (p?.topics ?? []).filter((t) => t.total > 0);

  return (
    <Page title="Phân tích học tập" subtitle={p ? `Hồ sơ của ${p.user.fullName} (@${p.user.username}) - tính từ lịch sử nộp bài.` : ""}>
      {isStaff && (
        <Card title="Xem hồ sơ của học sinh">
          <Row>
            <Field label="Tên đăng nhập của học sinh"><input style={INPUT} value={typing} onChange={(e) => setTyping(e.target.value)} placeholder="ví dụ hiennm" /></Field>
            <Btn onClick={() => setUsername(typing.trim())}>Xem</Btn>
            <Btn kind="ghost" onClick={() => { setTyping(""); setUsername(""); }}>Của tôi</Btn>
          </Row>
        </Card>
      )}
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
            <Table head={["Chủ đề", "Lớp", "Đã giải", "Điểm kỹ năng", "Mức", "Lượt nộp sai"]}
              rows={started.map((t) => [
                <b>{t.name}</b>, t.group, `${t.solved}/${t.total}`,
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}><Progress value={t.score} /><span>{t.score}</span></div>,
                <Tag color={t.score >= 80 ? "green" : t.score >= 50 ? "blue" : t.score >= 20 ? "orange" : "gray"}>{t.level}</Tag>,
                `${t.failed}/${t.attempts}`,
              ])} />
          </Card>

          <Card title="Điểm yếu cần khắc phục">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {weak.length === 0
                ? <Muted>Chưa phát hiện chủ đề yếu (chủ đề yếu = đã nộp từ 3 lần mà ít nhất một nửa là sai).</Muted>
                : <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center", fontSize: 13 }}>
                    Chủ đề đang yếu: {weak.map((t) => <Tag key={t.id} color="red">{t.name} ({t.failed}/{t.attempts} lượt sai)</Tag>)}
                  </div>}
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13.5, color: "#424242", lineHeight: 1.7 }}>
                {p.weaknessNotes.map((n, i) => <li key={i}>{n}</li>)}
              </ul>
            </div>
          </Card>

          <Card title="Bài tập gợi ý cho bạn">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {p.recommendations.length === 0 && <Muted>Bạn đã giải hết các bài trong lộ trình.</Muted>}
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
