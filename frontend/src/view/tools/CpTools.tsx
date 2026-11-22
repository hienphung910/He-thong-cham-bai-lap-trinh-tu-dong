import { useState } from "react";
import { Btn, Card, Field, INPUT, Page, Row, TEXTAREA, Table, Tag } from "../../shared/kit";

/**
 * Module 11 (cong cu ho tro lap trinh thi dau). Ca ba cong cu chay ngay trong trinh duyet,
 * khong goi may chu: sinh test ngau nhien, so sanh hai output, uoc luong do phuc tap.
 */
export default function CpTools() {
  return (
    <Page title="Công cụ CP" subtitle="Ba công cụ nhỏ hay dùng khi luyện lập trình thi đấu.">
      <TestGenerator />
      <OutputDiff />
      <ComplexityEstimator />
    </Page>
  );
}

function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/** Sinh mot bo test theo kieu da chon. */
function generate(kind: string, n: number, min: number, max: number): string {
  if (kind === "array") {
    const values = Array.from({ length: n }, () => randomInt(min, max));
    return `${n}\n${values.join(" ")}\n`;
  }
  if (kind === "permutation") {
    const p = Array.from({ length: n }, (_, i) => i + 1);
    for (let i = n - 1; i > 0; i--) {            // tron Fisher-Yates
      const j = randomInt(0, i);
      [p[i], p[j]] = [p[j], p[i]];
    }
    return `${n}\n${p.join(" ")}\n`;
  }
  if (kind === "tree") {
    // Dinh i noi voi mot dinh bat ky co so nho hon -> luon ra mot cay.
    const edges = Array.from({ length: n - 1 }, (_, i) => `${randomInt(1, i + 1)} ${i + 2}`);
    return `${n}\n${edges.join("\n")}\n`;
  }
  if (kind === "graph") {
    const m = Math.min(n * 2, (n * (n - 1)) / 2);
    const seen = new Set<string>();
    while (seen.size < m) {
      const a = randomInt(1, n);
      const b = randomInt(1, n);
      if (a !== b) seen.add(a < b ? `${a} ${b}` : `${b} ${a}`);
    }
    return `${n} ${seen.size}\n${[...seen].join("\n")}\n`;
  }
  // xau gom n chu cai thuong
  return Array.from({ length: n }, () => String.fromCharCode(97 + randomInt(0, 25))).join("") + "\n";
}

function TestGenerator() {
  const [kind, setKind] = useState("array");
  const [n, setN] = useState(10);
  const [min, setMin] = useState(1);
  const [max, setMax] = useState(100);
  const [output, setOutput] = useState("");
  const size = Math.max(1, Math.min(n, 100000));

  return (
    <Card title="Sinh test ngẫu nhiên">
      <Row>
        <Field label="Loại dữ liệu">
          <select style={INPUT} value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="array">Mảng số nguyên</option>
            <option value="permutation">Hoán vị 1..n</option>
            <option value="tree">Cây n đỉnh</option>
            <option value="graph">Đồ thị vô hướng</option>
            <option value="string">Xâu chữ thường</option>
          </select>
        </Field>
        <Field label="n"><input type="number" style={INPUT} value={n} onChange={(e) => setN(Number(e.target.value))} /></Field>
        {kind === "array" && <Field label="Giá trị nhỏ nhất"><input type="number" style={INPUT} value={min} onChange={(e) => setMin(Number(e.target.value))} /></Field>}
        {kind === "array" && <Field label="Giá trị lớn nhất"><input type="number" style={INPUT} value={max} onChange={(e) => setMax(Number(e.target.value))} /></Field>}
        <Btn onClick={() => setOutput(generate(kind, size, Math.min(min, max), Math.max(min, max)))}>Sinh test</Btn>
        <Btn kind="ghost" disabled={!output} onClick={() => void navigator.clipboard.writeText(output)}>Chép</Btn>
      </Row>
      {output && <textarea readOnly style={{ ...TEXTAREA, fontFamily: "var(--font-mono)", marginTop: 12, minHeight: 110 }} value={output} />}
    </Card>
  );
}

/** So hai output theo tung dong (bo khoang trang cuoi dong), tra ve dong dau tien khac nhau. */
function firstDifference(a: string, b: string): { line: number; left: string; right: string } | null {
  const la = a.trimEnd().split("\n").map((s) => s.trimEnd());
  const lb = b.trimEnd().split("\n").map((s) => s.trimEnd());
  for (let i = 0; i < Math.max(la.length, lb.length); i++) {
    if (la[i] !== lb[i]) return { line: i + 1, left: la[i] ?? "(hết dòng)", right: lb[i] ?? "(hết dòng)" };
  }
  return null;
}

function OutputDiff() {
  const [left, setLeft] = useState("");
  const [right, setRight] = useState("");
  const diff = firstDifference(left, right);
  const empty = left.trim() === "" && right.trim() === "";

  return (
    <Card title="So sánh hai output" right={!empty && (diff ? <Tag color="red">Khác nhau ở dòng {diff.line}</Tag> : <Tag color="green">Giống nhau</Tag>)}>
      <Row>
        <Field label="Output của bạn"><textarea style={{ ...TEXTAREA, fontFamily: "var(--font-mono)" }} value={left} onChange={(e) => setLeft(e.target.value)} /></Field>
        <Field label="Output đúng"><textarea style={{ ...TEXTAREA, fontFamily: "var(--font-mono)" }} value={right} onChange={(e) => setRight(e.target.value)} /></Field>
      </Row>
      {!empty && diff && (
        <div style={{ marginTop: 10, fontFamily: "var(--font-mono)", fontSize: 12.5, lineHeight: 1.7 }}>
          <div style={{ color: "#C62828" }}>Của bạn: {diff.left}</div>
          <div style={{ color: "#2E7D32" }}>Đúng&nbsp;&nbsp;&nbsp;: {diff.right}</div>
        </div>
      )}
    </Card>
  );
}

function ComplexityEstimator() {
  const [n, setN] = useState(100000);
  const [seconds, setSeconds] = useState(1);
  const budget = seconds * 1e8;      // may cham lam duoc khoang 10^8 phep tinh moi giay
  const rows: [string, number][] = [
    ["O(log n)", Math.log2(Math.max(n, 2))],
    ["O(n)", n],
    ["O(n log n)", n * Math.log2(Math.max(n, 2))],
    ["O(n √n)", n * Math.sqrt(n)],
    ["O(n²)", n * n],
    ["O(n³)", n * n * n],
    ["O(2ⁿ)", n > 60 ? Infinity : Math.pow(2, n)],
  ];

  return (
    <Card title="Ước lượng độ phức tạp">
      <Row>
        <Field label="Giới hạn n của đề"><input type="number" style={INPUT} value={n} onChange={(e) => setN(Number(e.target.value))} /></Field>
        <Field label="Giới hạn thời gian (giây)"><input type="number" style={INPUT} value={seconds} onChange={(e) => setSeconds(Number(e.target.value))} /></Field>
      </Row>
      <div style={{ height: 12 }} />
      <Table head={["Độ phức tạp", "Số phép tính ước lượng", "Kết luận"]}
        rows={rows.map(([name, ops]) => [
          <b>{name}</b>,
          Number.isFinite(ops) ? ops.toExponential(1) : "rất lớn",
          ops <= budget ? <Tag color="green">Chạy kịp</Tag> : <Tag color="red">Quá thời gian</Tag>,
        ])} />
    </Card>
  );
}
