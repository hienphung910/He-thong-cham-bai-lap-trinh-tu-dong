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

/** Cac lua chon cua bo sinh test. */
interface GenOptions {
  kind: string;
  n: number;
  min: number;
  max: number;
  /** Mang so: sinh so thuc thay vi so nguyen, voi so chu so sau dau phay cho truoc. */
  real: boolean;
  decimals: number;
  /** Do thi: so canh mong muon, co huong hay khong, co trong so hay khong. */
  edges: number;
  directed: boolean;
  weighted: boolean;
}

/** Mot gia tri ngau nhien trong [min, max]: so nguyen, hoac so thuc lam tron den "decimals" chu so. */
function randomValue(o: GenOptions): string {
  if (!o.real) return String(randomInt(Math.ceil(o.min), Math.floor(o.max)));
  return (o.min + Math.random() * (o.max - o.min)).toFixed(o.decimals);
}

/** Sinh mot bo test theo kieu da chon. */
function generate(o: GenOptions): string {
  const n = o.n;
  if (o.kind === "array") {
    const values = Array.from({ length: n }, () => randomValue(o));
    return `${n}\n${values.join(" ")}\n`;
  }
  if (o.kind === "permutation") {
    const p = Array.from({ length: n }, (_, i) => i + 1);
    for (let i = n - 1; i > 0; i--) {            // tron Fisher-Yates
      const j = randomInt(0, i);
      [p[i], p[j]] = [p[j], p[i]];
    }
    return `${n}\n${p.join(" ")}\n`;
  }
  if (o.kind === "tree") {
    // Dinh i noi voi mot dinh bat ky co so nho hon -> luon ra mot cay.
    const edges = Array.from({ length: n - 1 }, (_, i) => `${randomInt(1, i + 1)} ${i + 2}`);
    return `${n}\n${edges.join("\n")}\n`;
  }
  if (o.kind === "graph") {
    // Khong co khuyen va khong co canh lap. Do thi vo huong: canh (a, b) va (b, a) la mot,
    // nen toi da n(n-1)/2 canh; co huong thi hai chieu la hai cung khac nhau, toi da n(n-1).
    const maxEdges = o.directed ? n * (n - 1) : (n * (n - 1)) / 2;
    const m = Math.max(0, Math.min(o.edges, maxEdges));
    const seen = new Set<string>();
    while (seen.size < m) {
      const a = randomInt(1, n);
      const b = randomInt(1, n);
      if (a === b) continue;
      seen.add(o.directed || a < b ? `${a} ${b}` : `${b} ${a}`);
    }
    // Trong so la so nguyen trong [min, max], ghi sau hai dinh cua canh.
    const lines = [...seen].map((edge) => (o.weighted ? `${edge} ${randomInt(Math.ceil(o.min), Math.floor(o.max))}` : edge));
    return `${n} ${lines.length}\n${lines.join("\n")}\n`;
  }
  // xau gom n chu cai thuong
  return Array.from({ length: n }, () => String.fromCharCode(97 + randomInt(0, 25))).join("") + "\n";
}

const CHECK: React.CSSProperties = { display: "flex", gap: 6, alignItems: "center", fontSize: 13, height: 34, whiteSpace: "nowrap" };

function TestGenerator() {
  const [kind, setKind] = useState("array");
  const [n, setN] = useState(10);
  const [min, setMin] = useState(1);
  const [max, setMax] = useState(100);
  const [real, setReal] = useState(false);
  const [decimals, setDecimals] = useState(2);
  const [edges, setEdges] = useState(20);
  const [directed, setDirected] = useState(false);
  const [weighted, setWeighted] = useState(false);
  const [output, setOutput] = useState("");

  const isArray = kind === "array";
  const isGraph = kind === "graph";
  // Mang va do thi co trong so moi dung khoang gia tri [min, max].
  const usesRange = isArray || (isGraph && weighted);
  // Do thi it dinh hon de so canh toi da (n binh phuong) khong lam treo trinh duyet.
  const size = Math.max(1, Math.min(n, isGraph ? 2000 : 100000));
  const maxEdges = directed ? size * (size - 1) : (size * (size - 1)) / 2;

  function run() {
    setOutput(generate({
      kind, n: size, min: Math.min(min, max), max: Math.max(min, max),
      real, decimals: Math.max(1, Math.min(decimals, 9)),
      edges: Math.max(0, Math.min(edges, maxEdges, 200000)), directed, weighted,
    }));
  }

  return (
    <Card title="Sinh test ngẫu nhiên">
      <Row>
        <Field label="Loại dữ liệu">
          <select style={INPUT} value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="array">Mảng số</option>
            <option value="permutation">Hoán vị 1..n</option>
            <option value="tree">Cây n đỉnh</option>
            <option value="graph">Đồ thị</option>
            <option value="string">Xâu chữ thường</option>
          </select>
        </Field>
        <Field label={isGraph ? "Số đỉnh n" : "n"}><input type="number" style={INPUT} value={n} onChange={(e) => setN(Number(e.target.value))} /></Field>

        {isArray && (
          <Field label="Kiểu số">
            <select style={INPUT} value={real ? "real" : "int"} onChange={(e) => setReal(e.target.value === "real")}>
              <option value="int">Số nguyên</option>
              <option value="real">Số thực</option>
            </select>
          </Field>
        )}
        {isArray && real && (
          <Field label="Số chữ số thập phân"><input type="number" min={1} max={9} style={INPUT} value={decimals} onChange={(e) => setDecimals(Number(e.target.value))} /></Field>
        )}

        {isGraph && (
          <Field label={`Số cạnh m (tối đa ${maxEdges})`}><input type="number" min={0} style={INPUT} value={edges} onChange={(e) => setEdges(Number(e.target.value))} /></Field>
        )}
        {isGraph && (
          <Field label="Hướng">
            <select style={INPUT} value={directed ? "directed" : "undirected"} onChange={(e) => setDirected(e.target.value === "directed")}>
              <option value="undirected">Vô hướng</option>
              <option value="directed">Có hướng</option>
            </select>
          </Field>
        )}
        {isGraph && (
          <Field label="Trọng số">
            <select style={INPUT} value={weighted ? "yes" : "no"} onChange={(e) => setWeighted(e.target.value === "yes")}>
              <option value="no">Không có trọng số</option>
              <option value="yes">Có trọng số</option>
            </select>
          </Field>
        )}

        {usesRange && <Field label={isGraph ? "Trọng số nhỏ nhất" : "Giá trị nhỏ nhất"}><input type="number" style={INPUT} value={min} onChange={(e) => setMin(Number(e.target.value))} /></Field>}
        {usesRange && <Field label={isGraph ? "Trọng số lớn nhất" : "Giá trị lớn nhất"}><input type="number" style={INPUT} value={max} onChange={(e) => setMax(Number(e.target.value))} /></Field>}
        <div style={CHECK}>
          <Btn onClick={run}>Sinh test</Btn>
          <Btn kind="ghost" disabled={!output} onClick={() => void navigator.clipboard.writeText(output)}>Chép</Btn>
        </div>
      </Row>
      {isGraph && (
        <div style={{ marginTop: 8, fontSize: 12, color: "#9E9E9E" }}>
          Dòng đầu là n và m; mỗi dòng sau là một cạnh {directed ? "u v (cung đi từ u tới v)" : "u v"}{weighted ? " kèm trọng số w" : ""}. Không có khuyên và cạnh lặp.
        </div>
      )}
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
