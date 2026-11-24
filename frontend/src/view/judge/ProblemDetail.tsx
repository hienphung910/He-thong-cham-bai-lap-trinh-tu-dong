import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../../shared/api";
import { useAuth } from "../account/auth";
import CodeEditor from "./CodeEditor";
import TestTable from "./TestTable";
import SubmitInsight from "./SubmitInsight";
import ProblemComments from "./ProblemComments";
import { DIFFICULTY_META } from "../problem/learningPath";
import type { LanguageInfo, Problem, Submission } from "../../shared/types";

const TEMPLATES: Record<string, string> = {
  Java: `import java.util.Scanner;\n\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        long a = sc.nextLong();\n        long b = sc.nextLong();\n        System.out.println(a + b);\n    }\n}\n`,
  "C++": `#include <iostream>\n\nint main() {\n    long long a, b;\n    std::cin >> a >> b;\n    std::cout << a + b << std::endl;\n    return 0;\n}\n`,
  Python: `import sys\n\ndata = sys.stdin.read().split()\nprint(int(data[0]) + int(data[1]))\n`,
  JavaScript: `const data = require("fs").readFileSync(0, "utf8").trim().split(/\\s+/);\nconsole.log((BigInt(data[0]) + BigInt(data[1])).toString());\n`,
};

/** File ma nguon tai len tu may: gioi han 256 KB (may chu nhan body toi da 512 KB). */
const MAX_SOURCE_BYTES = 256 * 1024;
const ARCHIVE_EXTENSIONS = ["zip", "rar", "7z", "tar", "gz"];
const LANGUAGE_BY_EXTENSION: Record<string, string> = {
  java: "Java", cpp: "C++", cc: "C++", cxx: "C++", c: "C++", h: "C++", py: "Python", js: "JavaScript", mjs: "JavaScript",
};

/** Dung luong file dang de doc: 812 B, 3.4 KB. */
function formatBytes(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

/** Nut sao chep o goc o vi du: khung vien nho, bieu tuong hai o vuong chong nhau; bam xong hien dau tich. */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      // Trinh duyet chan clipboard (vd. trang khong phai https): bo qua, khong lam vo giao dien.
    }
  }
  return (
    <button
      type="button"
      onClick={() => void copy()}
      title={copied ? "Đã sao chép" : "Sao chép"}
      aria-label="Sao chép"
      style={{
        position: "absolute", top: 6, right: 6, width: 22, height: 22, padding: 0,
        display: "flex", alignItems: "center", justifyContent: "center",
        background: "#fff", border: "1px solid #D0D0D0", borderRadius: 4, cursor: "pointer",
        color: copied ? "#2E7D32" : "#757575",
      }}
    >
      {copied ? (
        <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 8.5l3.2 3L13 4.5" />
        </svg>
      ) : (
        <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round">
          <rect x="5.5" y="5.5" width="8.5" height="8.5" rx="1.5" />
          <path d="M10.5 2.5V2.2c0-.4-.3-.7-.7-.7H3.2c-.9 0-1.7.8-1.7 1.7v6.6c0 .4.3.7.7.7h.3" />
        </svg>
      )}
    </button>
  );
}

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
  const { user } = useAuth();
  // Ban nhap luu theo tai khoan + bai trong sessionStorage: het phien dang nhap (8 gio) hoac may chu
  // khoi dong lai lam trang quay ve man hinh dang nhap, dang nhap lai van con ma dang go do.
  const draftKey = `oj-draft:${user?.username ?? ""}:${problem.id}`;
  const draft = useMemo(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(draftKey) ?? "null") as { language?: string; code?: string } | null;
      return saved && typeof saved.code === "string" ? saved : null;
    } catch {
      return null;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftKey]);
  const [language, setLanguage] = useState(
    draft?.language && languages.some((l) => l.name === draft.language) ? draft.language : languages[0]?.name ?? "Java");
  const [code, setCode] = useState(draft?.code ?? "");
  useEffect(() => {
    try {
      if (code === "") sessionStorage.removeItem(draftKey);
      else sessionStorage.setItem(draftKey, JSON.stringify({ language, code }));
    } catch {
      // sessionStorage day hoac bi chan: bo qua, chi mat tinh nang giu ban nhap
    }
  }, [draftKey, language, code]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Submission | null>(null);
  /** O chon file an; nut icon "tai file" bam ho vao day. */
  const fileInput = useRef<HTMLInputElement>(null);
  /** File ma nguon vua nap vao o soan (de hien ten, dung luong, so dong); null = chua nap file nao. */
  const [uploaded, setUploaded] = useState<{ name: string; size: number; lines: number } | null>(null);

  /**
   * Doc file ma nguon nguoi dung chon tu may va dua vao o soan; duoi file quyet
   * dinh ngon ngu. He thong cham MOT file ma nguon nen file nen (.zip, .rar, .7z) bi tu choi.
   */
  async function loadSource(file: File | undefined) {
    if (!file) return;
    setError("");
    const kind = file.name.slice(file.name.lastIndexOf(".") + 1).toLowerCase();
    if (ARCHIVE_EXTENSIONS.includes(kind)) {
      setError(`Chưa hỗ trợ file nén (${file.name}). Hãy tải lên một file mã nguồn .java, .cpp, .py hoặc .js.`);
      return;
    }
    if (file.size > MAX_SOURCE_BYTES) {
      setError(`File ${file.name} quá lớn (tối đa ${MAX_SOURCE_BYTES / 1024} KB).`);
      return;
    }
    const text = await file.text();
    if (text.includes("\u0000")) {
      setError(`File ${file.name} không phải file mã nguồn dạng văn bản.`);
      return;
    }
    const extension = file.name.slice(file.name.lastIndexOf(".") + 1).toLowerCase();
    const detected = LANGUAGE_BY_EXTENSION[extension];
    if (detected && languages.some((l) => l.name === detected && l.available)) setLanguage(detected);
    setCode(text);
    setUploaded({ name: file.name, size: file.size, lines: text === "" ? 0 : text.replace(/\n$/, "").split("\n").length });
  }

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
                      <td style={{ padding: "8px 40px 8px 16px", fontFamily: "var(--font-mono)", fontSize: 13, color: "#1565C0", borderRight: "1px solid #E0E0E0", verticalAlign: "top", whiteSpace: "pre-wrap", position: "relative" }}>
                        {s.input.trimEnd()}
                        <CopyButton text={s.input} />
                      </td>
                      <td style={{ padding: "8px 40px 8px 16px", fontFamily: "var(--font-mono)", fontSize: 13, color: "#1565C0", verticalAlign: "top", whiteSpace: "pre-wrap", position: "relative" }}>
                        {s.output.trimEnd()}
                        <CopyButton text={s.output} />
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
          <input ref={fileInput} type="file" accept=".java,.cpp,.cc,.cxx,.c,.h,.py,.js,.mjs,.txt" style={{ display: "none" }}
            onChange={(e) => { const file = e.target.files?.[0]; e.target.value = ""; void loadSource(file); }} />

          {currentLang && !currentLang.available && (
            <div style={{ fontSize: 12, color: "#E65100", marginBottom: 8, padding: "6px 10px", background: "var(--color-orange-bg)", borderRadius: 4 }}>
              Máy này chưa cài toolchain cho {currentLang.name}.
            </div>
          )}

          <CodeEditor value={code} language={language} onChange={setCode} />

          {/* ── Hang nut duoi cung: code mau ben trai; ngon ngu va nop bai ben phai ── */}
          <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 14, flexWrap: "wrap" }}>
            <button
              onClick={() => { setCode(TEMPLATES[language] ?? ""); setUploaded(null); }}
              disabled={!TEMPLATES[language]}
              style={{
                height: 38, padding: "0 14px", border: "1px solid #E0E0E0", borderRadius: 6,
                fontSize: 12.5, color: "#616161", background: "#fff", cursor: "pointer",
                opacity: TEMPLATES[language] ? 1 : 0.4,
              }}
            >Chèn code mẫu</button>
            {/* Nut icon nho: chon mot file ma nguon tren may de dua vao o soan */}
            <button
              onClick={() => fileInput.current?.click()}
              title="Tải file mã nguồn từ máy (.java, .cpp, .py, .js)"
              aria-label="Tải file mã nguồn từ máy"
              style={{
                width: 38, height: 38, padding: 0, border: "1px solid #E0E0E0", borderRadius: 6,
                color: "#616161", background: "#fff", cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--color-accent)"; e.currentTarget.style.color = "var(--color-red)"; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = "#E0E0E0"; e.currentTarget.style.color = "#616161"; }}
            >
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 16V4" /><path d="m7 9 5-5 5 5" /><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
              </svg>
            </button>
            {uploaded && (
              <span title={`${uploaded.name} · ${formatBytes(uploaded.size)} · ${uploaded.lines} dòng`} style={{
                display: "inline-flex", alignItems: "center", gap: 6, height: 26, padding: "0 4px 0 10px", borderRadius: 13,
                background: "var(--color-red-light)", border: "1px solid #F3D9A4", fontSize: 12, color: "var(--color-red)",
                fontFamily: "var(--font-mono)", maxWidth: 260,
              }}>
                <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{uploaded.name}</span>
                <button
                  onClick={() => { setUploaded(null); setCode(""); }} title="Gỡ file và xoá nội dung ô soạn" aria-label="Gỡ file"
                  style={{ width: 20, height: 20, border: "none", borderRadius: "50%", background: "none", color: "inherit", cursor: "pointer", fontSize: 14, lineHeight: 1, padding: 0 }}
                >×</button>
              </span>
            )}
            <div style={{ flex: 1 }} />
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              title="Ngôn ngữ của bài nộp"
              style={{
                height: 38, minWidth: 170, padding: "0 12px", border: "1px solid #D0D0D0", borderRadius: 6,
                fontSize: 13.5, color: "#212121", background: "#fff", cursor: "pointer", outline: "none",
              }}
            >
              {langNames.map((l) => (
                <option key={l} value={l} disabled={unavailable.includes(l)}>{l}</option>
              ))}
            </select>
            <button
              onClick={() => void doSubmit()} disabled={busy}
              style={{
                height: 38, padding: "0 24px", border: "none", borderRadius: 6,
                fontSize: 14, fontWeight: 700, color: "var(--color-on-accent)", background: "var(--color-accent)",
                cursor: busy ? "not-allowed" : "pointer", opacity: busy ? 0.6 : 1,
              }}
            >
              {busy ? "Đang chấm..." : "▶ Nộp bài"}
            </button>
          </div>
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

        {/* ═══ BINH LUAN CUA NGUOI DUNG VE BAI NAY ═══ */}
        <ProblemComments problemId={problem.id} />
      </div>
    </div>
  );
}
