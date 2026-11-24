import { papi } from "../../shared/platformApi";
import { Bars, Card, ErrorBox, Page, Stat, Table, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";

/** Module 33 (bao cao va thong ke): so lieu toan he thong cho giang vien va quan tri vien. */
export default function Dashboard() {
  const dash = useLoad(() => papi.dashboard());
  const d = dash.data;

  return (
    <Page title="Tổng quan hệ thống" subtitle="Số liệu tính trực tiếp từ dữ liệu hiện có mỗi lần mở trang.">
      <ErrorBox text={dash.error} />
      {d && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <Stat label="Tài khoản" value={d.users} />
            <Stat label="Học sinh" value={d.students} color="var(--color-blue)" />
            <Stat label="Giảng viên" value={d.teachers} color="var(--color-purple)" />
            <Stat label="Bài tập" value={d.problems} color="var(--color-orange)" />
            <Stat label="Lượt nộp" value={d.submissions} color="var(--color-green)" />
            <Stat label="Lớp học" value={d.classrooms} />
            <Stat label="Kỳ thi" value={d.contests} color="var(--color-purple)" />
          </div>
          <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
            <Card title="Lượt nộp 14 ngày gần đây" style={{ flex: "2 1 420px", minWidth: 0 }}>
              <Bars items={d.activity.map((a) => ({ label: a.key.slice(8), value: a.count }))} />
            </Card>
            <Card title="Phân bố kết quả chấm" style={{ flex: "1 1 280px", minWidth: 0 }}>
              <Bars color="var(--color-blue)" items={d.verdicts.filter((v) => v.key !== "PE" && v.key !== "IE").map((v) => ({ label: v.key, value: v.count }))} />
            </Card>
          </div>
          <Card title="5 bài được nộp nhiều nhất">
            <Table head={["Bài", "Tên bài", "Lượt nộp", "Tỉ lệ AC"]} empty="Chưa có lượt nộp nào."
              rows={d.topProblems.map((p) => [<ProblemChip id={p.id} title={p.title} />, p.title, p.attempts, `${p.acRate}%`])} />
          </Card>
        </>
      )}
    </Page>
  );
}
