import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, INPUT, Page, TEXTAREA, Tag, errorText } from "../../shared/kit";
import CodeEditor from "../judge/CodeEditor";
import type { LanguageInfo } from "../../shared/types";
import type { RunResult } from "../../shared/platformTypes";

const OUTPUT: React.CSSProperties = {
  margin: 0, padding: "10px 12px", background: "#FAFAFA", border: "1px solid #E0E0E0", borderRadius: 6,
  fontFamily: "var(--font-mono)", fontSize: 12.5, whiteSpace: "pre-wrap", minHeight: 60, maxHeight: 260, overflow: "auto",
};

/**
 * Module 10 (IDE truc tuyen): viet ma, tu nhap input va chay thu tren may chu ma KHONG
 * tinh la nop bai. May chu dung lai dung bo bien dich / chay cua bo cham (gioi han 5 giay).
 */
export default function Playground({ languages }: { languages: LanguageInfo[] }) {
  const [language, setLanguage] = useState(languages.find((l) => l.available)?.name ?? "Java");
  const [code, setCode] = useState("");
  const [input, setInput] = useState("");
  const [result, setResult] = useState<RunResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run() {
    setBusy(true);
    setError("");
    setResult(null);
    try { setResult(await papi.run({ language, code, input })); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }

  return (
    <Page title="IDE trực tuyến"
      actions={<>
        <select style={{ ...INPUT, width: 150 }} value={language} onChange={(e) => setLanguage(e.target.value)}>
          {languages.map((l) => <option key={l.name} value={l.name} disabled={!l.available}>{l.name}{l.available ? "" : " (chưa cài)"}</option>)}
        </select>
        <Btn disabled={busy || !code.trim()} onClick={() => void run()}>{busy ? "Đang chạy..." : "▶ Chạy"}</Btn>
      </>}>
      <ErrorBox text={error} />
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Card title="Mã nguồn" style={{ flex: "2 1 460px", minWidth: 0 }}>
          <CodeEditor value={code} language={language} onChange={setCode} minHeight={420} placeholder="Viết hoặc dán mã nguồn vào đây..." />
        </Card>
        <div style={{ flex: "1 1 300px", minWidth: 260, display: "flex", flexDirection: "column", gap: 16 }}>
          <Card title="Input (stdin)">
            <textarea style={{ ...TEXTAREA, fontFamily: "var(--font-mono)", minHeight: 120 }} value={input}
              onChange={(e) => setInput(e.target.value)} placeholder="Dữ liệu đưa vào chương trình..." />
          </Card>
          <Card title="Kết quả" right={result && !result.compileError && (
            <>
              <Tag color={result.timedOut ? "red" : result.exitCode === 0 ? "green" : "orange"}>
                {result.timedOut ? "Quá 5 giây" : `exit code ${result.exitCode}`}
              </Tag>
              <Tag>{result.timeMs} ms</Tag>
            </>
          )}>
            {!result && <span style={{ fontSize: 13, color: "#9E9E9E" }}>Bấm "Chạy" để xem output.</span>}
            {result?.compileError && (
              <>
                <Tag color="red">Lỗi biên dịch</Tag>
                <pre style={{ ...OUTPUT, marginTop: 8, color: "#C62828" }}>{result.compileError}</pre>
              </>
            )}
            {result && !result.compileError && (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                <pre style={OUTPUT}>{result.stdout || "(không có output)"}</pre>
                {result.stderr && <pre style={{ ...OUTPUT, color: "#C62828" }}>{result.stderr}</pre>}
              </div>
            )}
          </Card>
        </div>
      </div>
    </Page>
  );
}
