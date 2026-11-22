import { useMemo, useState } from "react";
import { api } from "../../shared/api";
import CodeEditor from "./CodeEditor";
import TestTable from "./TestTable";
import SubmitInsight from "./SubmitInsight";
import AssistantPopup from "./AssistantPopup";
import { DIFFICULTY_META } from "../problem/learningPath";
import type { LanguageInfo, Problem, Submission } from "../../shared/types";

const TEMPLATES: Record<string, string> = {
  Java: `import java.util.Scanner;\n\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        long a = sc.nextLong();\n        long b = sc.nextLong();\n        System.out.println(a + b);\n    }\n}\n`,
  "C++": `#include <iostream>\n\nint main() {\n    long long a, b;\n    std::cin >> a >> b;\n    std::cout << a + b << std::endl;\n    return 0;\n}\n`,
  Python: `import sys\n\ndata = sys.stdin.read().split()\nprint(int(data[0]) + int(data[1]))\n`,
  JavaScript: `const data = require("fs").readFileSync(0, "utf8").trim().split(/\\s+/);\nconsole.log((BigInt(data[0]) + BigInt(data[1])).toString());\n`,
};

export default function ProblemDetail({
  problem, languages, onBack, onSubmitted, topicName, nextProblem, onNext, onOpen,
}: {
  problem: Problem; languages: LanguageInfo[]; onBack: () => void; onSubmitted: () => void;
  /** Mo mot bai khac ngay tai day (bam vao bai duoc goi y sau khi nop). */
  onOpen: (problemId: string) => void;
  /** Ten chu de cua bai trong lo trinh hoc. */
  topicName?: string;
  /** Bai ke tiep theo lo trinh (null neu day la bai cuoi). */
  nextProblem?: Problem | null;
  onNext?: () => void;
}) {
  const diff = DIFFICULTY_META[problem.difficulty];
  const [language, setLanguage] = useState(languages[0]?.name ?? "Java");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Submission | null>(null);
  /** So lan da nop trong trang nay - moi lan tang thi tro ly pop-up tu nhan xet mot lan. */
  const [submitCount, setSubmitCount] = useState(0);

  const langNames = useMemo(() => languages.map((l) => l.name), [languages]);
  const unavailable = useMemo(() => languages.filter((l) => !l.available).map((l) => l.name), [languages]);
  const currentLang = languages.find((l) => l.name === language);

  /** Gui bai len may chu va cho cham xong; ket qua tra ve kem bang tung test. */
  async function doSubmit() {
    setError(""); setResult(null);
    if (!language) return setError("Chưa chọn ngôn ngữ.");
    if (!code.trim()) return setError("Mã nguồn đang rỗng.");
    setBusy(true);
    try {
      setResult(await api.submit({ problemId: problem.id, language, code }));
      setSubmitCount((c) => c + 1);
      onSubmitted();
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  }

  const tests = result?.tests ?? [];

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
      {/* ═══ BREADCRUMB BAR ═══ */}
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "10px 24px", background: "#fff", borderBottom: "1px solid #E0E0E0", flexShrink: 0,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button onClick={onBack} style={{
            display: "flex", alignItems: "center", gap: 4, background: "none", border: "1px solid #E0E0E0",
            borderRadius: 6, height: 30, padding: "0 10px", color: "#757575", fontSize: 12, cursor: "pointer",
          }}>
            ← Lớp học
          </button>
          <span style={{ fontSize: 13, fontWeight: 600, color: "#212121" }}>
            Làm bài lớp: CodeHub · {problem.id}
          </span>
          {topicName && <span style={{ fontSize: 12, color: "#757575" }}>{topicName}</span>}
          {diff && (
            <span style={{ fontSize: 11.5, fontWeight: 700, padding: "2px 10px", borderRadius: 10, color: diff.color, background: diff.bg }}>
              {diff.label}
            </span>
          )}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {nextProblem && (
            <button onClick={onNext} title={`${nextProblem.id} — ${nextProblem.title}`} style={{
              display: "flex", alignItems: "center", gap: 4, background: "none", border: "1px solid #E0E0E0",
              borderRadius: 6, height: 30, padding: "0 12px", color: "#757575", fontSize: 12, cursor: "pointer",
            }}>
              Bài tiếp theo →
            </button>
          )}
        </div>
      </div>

      <div style={{ flex: 1, overflow: "auto", padding: "16px 24px 24px", display: "flex", flexDirection: "column", gap: 16 }}>
        {/* ═══ PROBLEM STATEMENT CARD ═══ */}
        <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "20px 24px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: "#212121" }}>
              Đề bài: <span style={{ fontFamily: "var(--font-mono)", color: "var(--color-red)" }}>{problem.id}</span> — {problem.title}
            </div>
            <span style={{ fontSize: 12, color: "#9E9E9E", fontFamily: "var(--font-mono)", whiteSpace: "nowrap" }}>
              {problem.timeLimitMs / 1000}s · {problem.memoryLimitMb} MB
            </span>
          </div>

          <pre style={{
            margin: 0, whiteSpace: "pre-wrap", fontSize: 14, lineHeight: 1.75, color: "#424242",
            fontFamily: "var(--font-sans)",
          }}>
            {problem.statement || "(Đề bài chưa có statement.txt)"}
          </pre>

          {/* Sample I/O */}
          {problem.samples.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: "#212121", marginBottom: 10 }}>Ví dụ</div>
              {problem.samples.map((s, i) => (
                <table key={i} style={{ borderCollapse: "collapse", marginBottom: 12, border: "1px solid #E0E0E0" }}>
                  <thead>
                    <tr>
                      <th style={{ padding: "6px 16px", fontSize: 13, fontWeight: 700, color: "#212121", background: "#FAFAFA", borderBottom: "1px solid #E0E0E0", borderRight: "1px solid #E0E0E0", textAlign: "left" }}>Input</th>
                      <th style={{ padding: "6px 16px", fontSize: 13, fontWeight: 700, color: "#212121", background: "#FAFAFA", borderBottom: "1px solid #E0E0E0", textAlign: "left" }}>Output</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td style={{ padding: "8px 16px", fontFamily: "var(--font-mono)", fontSize: 13, color: "#1565C0", borderRight: "1px solid #E0E0E0", verticalAlign: "top", whiteSpace: "pre-wrap", position: "relative" }}>
                        {s.input.trimEnd()}
                        <button
                          onClick={() => navigator.clipboard.writeText(s.input)}
                          style={{ position: "absolute", top: 4, right: 4, background: "none", border: "none", cursor: "pointer", color: "#BDBDBD", fontSize: 12 }}
                          title="Copy"
                        >📋</button>
                      </td>
                      <td style={{ padding: "8px 16px", fontFamily: "var(--font-mono)", fontSize: 13, color: "#1565C0", verticalAlign: "top", whiteSpace: "pre-wrap", position: "relative" }}>
                        {s.output.trimEnd()}
                        <button
                          onClick={() => navigator.clipboard.writeText(s.output)}
                          style={{ position: "absolute", top: 4, right: 4, background: "none", border: "none", cursor: "pointer", color: "#BDBDBD", fontSize: 12 }}
                          title="Copy"
                        >📋</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              ))}
            </div>
          )}
        </div>

        {/* ═══ SUBMIT SECTION ═══ */}
        <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "20px 24px" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{
                height: 34, padding: "0 12px", border: "1px solid #E0E0E0", borderRadius: 6,
                fontSize: 13, color: "#212121", background: "#fff", cursor: "pointer", outline: "none",
              }}
            >
              {langNames.map((l) => (
                <option key={l} value={l} disabled={unavailable.includes(l)}>{l}</option>
              ))}
            </select>
            <button
              onClick={() => setCode(TEMPLATES[language] ?? "")}
              disabled={!TEMPLATES[language]}
              style={{
                height: 34, padding: "0 14px", border: "1px solid #E0E0E0", borderRadius: 6,
                fontSize: 12, color: "#616161", background: "#fff", cursor: "pointer",
                opacity: TEMPLATES[language] ? 1 : 0.4,
              }}
            >Chèn code mẫu</button>
            <div style={{ flex: 1 }} />
            <button
              onClick={() => void doSubmit()} disabled={busy}
              style={{
                height: 36, padding: "0 20px", border: "none", borderRadius: 6,
                fontSize: 13, fontWeight: 700, color: "#fff", background: "var(--color-red)",
                cursor: busy ? "not-allowed" : "pointer", opacity: busy ? 0.6 : 1,
              }}
            >
              {busy ? "Đang chấm..." : "▶ Nộp bài"}
            </button>
          </div>

          {currentLang && !currentLang.available && (
            <div style={{ fontSize: 12, color: "#E65100", marginBottom: 8, padding: "6px 10px", background: "var(--color-orange-bg)", borderRadius: 4 }}>
              Máy này chưa cài toolchain cho {currentLang.name}.
            </div>
          )}

          <CodeEditor value={code} onChange={setCode} />
        </div>

        {/* ═══ RESULTS ═══ */}
        {error && (
          <div style={{ padding: "12px 16px", background: "#FFEBEE", border: "1px solid #FFCDD2", borderRadius: 6, fontSize: 13, color: "#C62828" }}>{error}</div>
        )}

        {busy && !error && (
          <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "16px 20px", fontSize: 13, fontFamily: "var(--font-mono)", color: "#757575" }}>
            <span style={{ animation: "livePulse 1.2s infinite" }}>Đang biên dịch và chấm bài...</span>
          </div>
        )}

        {result && !error && (
          <div style={{ background: "#fff", border: "1px solid #E0E0E0", borderRadius: 8, padding: "16px 20px", display: "flex", flexDirection: "column", gap: 10 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <span style={{
                fontFamily: "var(--font-mono)", fontSize: 12, fontWeight: 700, padding: "2px 10px", borderRadius: 4,
                background: result.verdict === "AC" ? "var(--color-ac-bg)" : "var(--color-wa-bg)",
                color: result.verdict === "AC" ? "var(--color-ac-text)" : "var(--color-wa-text)",
              }}>
                {result.verdict}
              </span>
              <span style={{ fontSize: 13, fontFamily: "var(--font-mono)" }}>Đúng {result.passed}/{result.total} test</span>
              <span style={{ fontSize: 13, fontFamily: "var(--font-mono)", color: "var(--color-green)", fontWeight: 700 }}>{(result.score ?? 0).toFixed(1)}/{result.maxPoints.toFixed(0)} điểm</span>
            </div>

            {result.verdict === "AC" && nextProblem && (
              <div style={{
                display: "flex", alignItems: "center", gap: 12, padding: "10px 14px",
                background: "var(--color-ac-bg)", border: "1px solid var(--color-green-border)", borderRadius: 6,
              }}>
                <span style={{ flex: 1, fontSize: 13, color: "var(--color-ac-text)", fontWeight: 600 }}>
                  Chính xác! Sẵn sàng cho bài tiếp theo: {nextProblem.id} — {nextProblem.title}
                </span>
                <button onClick={onNext} style={{
                  height: 32, padding: "0 16px", border: "none", borderRadius: 6, cursor: "pointer",
                  fontSize: 13, fontWeight: 700, color: "#fff", background: "var(--color-green)",
                }}>
                  Làm bài tiếp →
                </button>
              </div>
            )}

            {/* Loi chung cua ca bai, vi du nguyen van thong bao cua trinh bien dich khi CE. */}
            {result.message && (
              <pre style={{
                margin: 0, padding: "10px 14px", background: "#FAFAFA", border: "1px solid #FFCDD2",
                borderRadius: 6, fontFamily: "var(--font-mono)", fontSize: 11.5, color: "#616161",
                whiteSpace: "pre-wrap", maxHeight: 200, overflow: "auto",
              }}>{result.message}</pre>
            )}

            {tests.length > 0 && <TestTable tests={tests} showMessage={tests.some((t) => t.message !== "")} />}
          </div>
        )}

        {/* ═══ THONG KE DIEM YEU SAU KHI NOP ═══ */}
        {result && !error && <SubmitInsight problem={problem} result={result} onOpen={onOpen} />}
      </div>

      {/* ═══ TRO LY POP-UP ═══ */}
      <AssistantPopup problem={problem} code={code} autoAskKey={submitCount} />
    </div>
  );
}
