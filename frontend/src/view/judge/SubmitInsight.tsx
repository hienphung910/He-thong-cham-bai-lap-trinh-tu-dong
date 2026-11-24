import { api } from "../../shared/api";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Muted, Progress, Tag, useLoad } from "../../shared/kit";
import { useNav } from "../../shared/nav";
import { useAuth } from "../account/auth";
import type { Problem, Submission } from "../../shared/types";

/**
 * Nhan xet rieng cho LAN NOP VUA ROI, suy ra tu ket qua tung test:
 * dung test vi du ma sai test an, co test qua thoi gian, loi khi chay...
 */
function reviewResult(result: Submission): string[] {
  const tests = result.tests ?? [];
  const notes: string[] = [];
  if (result.verdict === "AC") return ["Bài làm đúng tất cả các test."];
  if (result.verdict === "CE") return ["Chương trình chưa biên dịch được: đọc dòng đầu của thông báo lỗi phía trên, thường là thiếu dấu ; hoặc sai tên biến."];

  const samples = tests.filter((t) => t.sample);
  const hidden = tests.filter((t) => !t.sample);
  const samplesOk = samples.length > 0 && samples.every((t) => t.verdict === "AC");
  if (samplesOk && hidden.some((t) => t.verdict !== "AC")) {
    notes.push("Đúng test ví dụ nhưng sai test ẩn: thường do thiếu trường hợp biên (số nhỏ nhất, lớn nhất, số âm) hoặc tràn số.");
  }
  if (!samplesOk && samples.length > 0) {
    notes.push("Chưa đúng ngay cả test ví dụ: so lại output của bạn với phần Ví dụ trong đề, kể cả dấu cách và xuống dòng.");
  }
  if (tests.some((t) => t.verdict === "TLE")) notes.push("Có test chạy quá thời gian: thuật toán chưa đủ nhanh với dữ liệu lớn.");
  if (tests.some((t) => t.verdict === "RE")) notes.push("Có test bị lỗi khi chạy: kiểm tra chỉ số mảng, phép chia cho 0 và việc đọc đủ dữ liệu.");
  if (tests.some((t) => t.verdict === "MLE")) notes.push("Có test vượt giới hạn bộ nhớ: xem lại kích thước mảng.");
  return notes;
}

/**
 * Thong ke hien ngay duoi ket qua cham sau moi lan nop (Module 23-26 dua vao trang lam bai):
 *  - cac lan da nop bai nay,
 *  - nhan xet lan nop vua roi,
 *  - tien do chu de cua bai, cac chu de dang yeu, bai nen lam tiep.
 * Du lieu lay tu lich su nop bai (/api/submissions) va ho so hoc tap (/api/analytics/me).
 */
export default function SubmitInsight({ problem, result, onOpen }: {
  problem: Problem;
  /** Bai nop vua cham xong; doi sang bai nop khac thi thong ke duoc tai lai. */
  result: Submission;
  /** Mo mot bai khac ngay trong trang lam bai. */
  onOpen: (problemId: string) => void;
}) {
  const { user } = useAuth();
  // Quan tri vien khong co trang Phan tich hoc tap nen khong hien nut dan toi do.
  const isAdmin = user?.role === "ADMIN";
  const { go } = useNav();
  const history = useLoad(() => api.submissions(), [result.id]);
  const profile = useLoad(() => papi.myProfile(), [result.id]);

  // Lich su tra ve moi nhat truoc -> dao lai de hien theo thu tu thoi gian.
  const attempts = (history.data ?? [])
    .filter((s) => s.problem.id === problem.id && s.author === user?.username)
    .reverse();
  const firstAc = attempts.findIndex((s) => s.verdict === "AC");
  const topic = profile.data?.topics.find((t) => t.id === problem.topic);
  const weakTopics = (profile.data?.topics ?? []).filter((t) => t.weak);
  const nextProblems = (profile.data?.recommendations ?? []).filter((r) => r.problemId !== problem.id).slice(0, 3);

  return (
    <Card title="Phân tích sau khi nộp" right={isAdmin ? undefined : <Btn small kind="ghost" onClick={() => go("Analytics")}>Xem phân tích đầy đủ</Btn>}>
      <ErrorBox text={history.error || profile.error} />
      <div style={{ display: "flex", flexDirection: "column", gap: 14, fontSize: 13.5, color: "#424242" }}>
        <div>
          <b>Các lần bạn đã nộp bài này ({attempts.length}):</b>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center", marginTop: 6 }}>
            {attempts.map((s, i) => (
              <span key={s.id} title={`Lần ${i + 1} · ${s.language} · ${s.submittedAt.replace("T", " ")}`}>
                <Tag color={s.verdict === "AC" ? "green" : "red"}>{i + 1}. {s.verdict}</Tag>
              </span>
            ))}
            {attempts.length > 0 && (
              <span style={{ color: "#757575" }}>
                {firstAc >= 0 ? `→ AC ở lần thứ ${firstAc + 1}` : "→ chưa AC"}
              </span>
            )}
          </div>
        </div>

        <div>
          <b>Nhận xét lần nộp này:</b>
          <ul style={{ margin: "4px 0 0", paddingLeft: 18, lineHeight: 1.65 }}>
            {reviewResult(result).map((line, i) => <li key={i}>{line}</li>)}
          </ul>
        </div>

        {topic && (
          <div>
            <b>Chủ đề của bài — {topic.name}:</b>
            <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 6, flexWrap: "wrap" }}>
              <div style={{ width: 200 }}><Progress value={topic.score} /></div>
              <span>đã giải {topic.solved}/{topic.total} bài</span>
              <Tag color={topic.weak ? "red" : "blue"}>{topic.weak ? "Đang yếu" : topic.level}</Tag>
              <span style={{ color: "#757575" }}>{topic.failed}/{topic.attempts} lượt nộp sai</span>
            </div>
          </div>
        )}

        {profile.data && (
          <div>
            <b>Bạn đang yếu ở đâu:</b>
            {weakTopics.length > 0 && (
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                {weakTopics.map((t) => <Tag key={t.id} color="red">{t.name} ({t.failed}/{t.attempts} lượt sai)</Tag>)}
              </div>
            )}
            <ul style={{ margin: "4px 0 0", paddingLeft: 18, lineHeight: 1.65 }}>
              {weakTopics.length === 0 && <li>Chưa có chủ đề nào bị xếp là yếu.</li>}
              {profile.data.weaknessNotes.map((n, i) => <li key={i}>{n}</li>)}
            </ul>
          </div>
        )}

        {nextProblems.length > 0 && (
          <div>
            <b>Nên làm tiếp:</b>
            <div style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 6 }}>
              {nextProblems.map((r) => (
                <div key={r.problemId} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                  <Btn small kind="ghost" onClick={() => onOpen(r.problemId)}>{r.problemId}</Btn>
                  <span>{r.title}</span>
                  <Muted>{r.reason}</Muted>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
