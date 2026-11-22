import type { TestResult } from "../../shared/types";

const CELL: React.CSSProperties = { padding: "6px 12px", textAlign: "left", borderBottom: "1px solid #F0F0F0" };
const HEAD: React.CSSProperties = { ...CELL, fontSize: 11, fontWeight: 700, color: "#757575", background: "var(--color-table-header-bg)" };

/**
 * Bang ket qua tung test cua mot bai nop: ten test, verdict, thoi gian chay, diem.
 * Cot "Thông báo" giai thich vi sao sai (khong bao gio kem dap an cua test an).
 */
export default function TestTable({ tests, showMessage }: { tests: TestResult[]; showMessage: boolean }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "var(--font-mono)", fontSize: 12, border: "1px solid #E0E0E0" }}>
      <thead>
        <tr>
          <th style={HEAD}>Test</th>
          <th style={HEAD}>Kết quả</th>
          <th style={HEAD}>Thời gian</th>
          <th style={HEAD}>Điểm</th>
          {showMessage && <th style={HEAD}>Thông báo</th>}
        </tr>
      </thead>
      <tbody>
        {tests.map((t) => (
          <tr key={t.id}>
            <td style={CELL}>{t.id}</td>
            <td style={{ ...CELL, fontWeight: 700, color: t.verdict === "AC" ? "var(--color-ac-text)" : "var(--color-wa-text)" }}>{t.verdict}</td>
            <td style={{ ...CELL, color: "#757575" }}>{t.runtimeMs} ms</td>
            <td style={CELL}>{Number(t.points.toFixed(2))}</td>
            {showMessage && <td style={{ ...CELL, color: "#616161", whiteSpace: "pre-wrap" }}>{t.message}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
