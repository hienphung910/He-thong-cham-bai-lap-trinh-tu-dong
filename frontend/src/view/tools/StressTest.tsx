import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Tag, errorText } from "../../shared/kit";
import CodeEditor from "../judge/CodeEditor";
import type { LanguageInfo } from "../../shared/types";
import type { StressResult } from "../../shared/platformTypes";

/** Vi du mau bang Python: bai tinh tong a + b, loi giai "can kiem tra" co cai sai khi a >= 90. */
const EXAMPLE = {
  generator: "import random\nseed = int(input())      # may chu dua so thu tu vong vao stdin\nrandom.seed(seed)\nprint(random.randint(1, 100), random.randint(1, 100))\n",
  brute: "a, b = map(int, input().split())\nprint(a + b)\n",
  solution: "a, b = map(int, input().split())\nprint(a + b if a < 90 else a - b)\n",
};

const PRE: React.CSSProperties = {
  margin: 0, padding: "8px 12px", background: "#FAFAFA", border: "1px solid #E0E0E0", borderRadius: 6,
  fontFamily: "var(--font-mono)", fontSize: 12.5, whiteSpace: "pre-wrap", maxHeight: 180, overflow: "auto",
};

/**
 * Module 12 (stress test): chay loi giai can kiem tra va loi giai vet can tren cac test
 * sinh ngau nhien, dung lai o test dau tien ma hai loi giai cho ket qua khac nhau.
 */
export default function StressTest({ languages }: { languages: LanguageInfo[] }) {
  const [language, setLanguage] = useState(languages.find((l) => l.available)?.name ?? "Python");
  const [solution, setSolution] = useState("");
  const [brute, setBrute] = useState("");
  const [generator, setGenerator] = useState("");
  const [rounds, setRounds] = useState(20);
  const [result, setResult] = useState<StressResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run() {
    setBusy(true);
    setError("");
    setResult(null);
    try { setResult(await papi.stress({ language, solution, brute, generator, rounds })); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  function loadExample() {
    setLanguage("Python");
    setSolution(EXAMPLE.solution);
    setBrute(EXAMPLE.brute);
    setGenerator(EXAMPLE.generator);
  }

  return (
    <Page title="Stress test" subtitle="Tìm phản ví dụ: so lời giải của bạn với một lời giải vét cạn chắc chắn đúng trên test sinh ngẫu nhiên."
      actions={<>
        <Btn kind="ghost" onClick={loadExample}>Nạp ví dụ mẫu</Btn>
        <select style={{ ...INPUT, width: 140 }} value={language} onChange={(e) => setLanguage(e.target.value)}>
          {languages.map((l) => <option key={l.name} value={l.name} disabled={!l.available}>{l.name}</option>)}
        </select>
        <div style={{ width: 110 }}><Field label="Số vòng (≤ 30)"><input type="number" style={INPUT} value={rounds} onChange={(e) => setRounds(Number(e.target.value))} /></Field></div>
        <Btn disabled={busy} onClick={() => void run()}>{busy ? "Đang chạy..." : "▶ Chạy stress test"}</Btn>
      </>}>
      <ErrorBox text={error} />

      {result && (
        <Card title="Kết quả" right={<Tag color={result.found ? "red" : "green"}>{result.found ? `Tìm thấy ở vòng ${result.rounds}` : "Không thấy lỗi"}</Tag>}>
          <div style={{ fontSize: 13.5, marginBottom: 10 }}>{result.message}</div>
          {result.found && (
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <div style={{ flex: 1, minWidth: 200 }}><b style={{ fontSize: 12 }}>Input gây lỗi</b><pre style={PRE}>{result.input}</pre></div>
              <div style={{ flex: 1, minWidth: 200 }}><b style={{ fontSize: 12 }}>Vét cạn trả về</b><pre style={PRE}>{result.expected}</pre></div>
              <div style={{ flex: 1, minWidth: 200 }}><b style={{ fontSize: 12 }}>Lời giải của bạn trả về</b><pre style={{ ...PRE, color: "#C62828" }}>{result.actual || "(không có)"}</pre></div>
            </div>
          )}
        </Card>
      )}

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Card title="1. Lời giải cần kiểm tra" style={{ flex: "1 1 320px", minWidth: 0 }}>
          <CodeEditor value={solution} onChange={setSolution} minHeight={240} placeholder="Lời giải nhanh mà bạn nghi là sai..." />
        </Card>
        <Card title="2. Lời giải vét cạn (chắc chắn đúng)" style={{ flex: "1 1 320px", minWidth: 0 }}>
          <CodeEditor value={brute} onChange={setBrute} minHeight={240} placeholder="Lời giải chậm nhưng đúng..." />
        </Card>
        <Card title="3. Chương trình sinh test" style={{ flex: "1 1 320px", minWidth: 0 }}>
          <CodeEditor value={generator} onChange={setGenerator} minHeight={240}
            placeholder="Đọc một số nguyên (số thứ tự vòng) từ stdin làm seed, in ra một input ngẫu nhiên nhỏ..." />
        </Card>
      </div>
    </Page>
  );
}
