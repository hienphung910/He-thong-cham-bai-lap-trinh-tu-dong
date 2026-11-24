import { useRef, useState } from "react";
import { api } from "../../../shared/api";
import { Select } from "../../../shared/ui";
import { DIFFICULTY_META } from "../learningPath";
import type { NewTestInput, Topic } from "../../../shared/types";

const COMPARATORS = ["token", "lines", "exact", "float", "float:1e-6", "float:1e-9"];

let nextKey = 1;
interface TestRow extends NewTestInput {
  key: number;
}
function newTestRow(sample: boolean, name: string): TestRow {
  return { key: nextKey++, name, input: "", output: "", sample };
}

/** Gioi han khi nap test tu thu muc: khop voi may chu (200 test, moi file 1 trieu ky tu, body 8 MB). */
const MAX_TESTS = 200;
const MAX_TEST_CHARS = 1_000_000;
const MAX_TOTAL_CHARS = 6_000_000;
const INPUT_EXTENSIONS = ["in", "inp"];
const OUTPUT_EXTENSIONS = ["out", "ans", "ok"];

/**
 * Ghep cac file trong mot thu muc test thanh tung cap input / output.
 *
 * Nhan hai cach dat ten pho bien:
 *   - cung thu muc, ten giong nhau:   01.in + 01.out, sample01.inp + sample01.ans ...
 *   - moi test mot thu muc con (kieu Themis):   Test01/BAI.INP + Test01/BAI.OUT
 * Ten test bat dau bang "sample" la test vi du. File khong ghep duoc cap thi bo qua va bao lai.
 */
async function readTestFolder(files: File[]): Promise<{ rows: TestRow[]; skipped: number; error: string }> {
  interface Pair { dir: string; base: string; input?: File; output?: File }
  const pairs = new Map<string, Pair>();
  let skipped = 0;
  for (const file of files) {
    const path = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
    const slash = path.lastIndexOf("/");
    const dir = slash < 0 ? "" : path.slice(0, slash);
    const fileName = path.slice(slash + 1);
    const dot = fileName.lastIndexOf(".");
    const extension = dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
    const isInput = INPUT_EXTENSIONS.includes(extension);
    const isOutput = OUTPUT_EXTENSIONS.includes(extension);
    if (!isInput && !isOutput) { skipped++; continue; }
    const base = fileName.slice(0, dot);
    const key = `${dir}/${base}`.toLowerCase();
    const pair = pairs.get(key) ?? { dir, base };
    if (isInput) pair.input = file; else pair.output = file;
    pairs.set(key, pair);
  }

  const complete = [...pairs.values()].filter((p) => p.input && p.output);
  skipped += [...pairs.values()].filter((p) => !p.input || !p.output).length;
  if (complete.length === 0) {
    return { rows: [], skipped, error: "Không tìm thấy cặp file test nào. Mỗi test cần một file .in (hoặc .inp) và một file .out (hoặc .ans) cùng tên." };
  }
  if (complete.length > MAX_TESTS) {
    return { rows: [], skipped, error: `Thư mục có ${complete.length} test, vượt giới hạn ${MAX_TESTS} test mỗi bài.` };
  }

  // Kieu Themis: moi thu muc con mot test, ten file giong het nhau -> lay ten thu muc lam ten test.
  const sameBase = new Set(complete.map((p) => p.base.toLowerCase())).size === 1;
  const manyDirs = new Set(complete.map((p) => p.dir)).size > 1;
  const used = new Set<string>();
  const rows: TestRow[] = [];
  let total = 0;
  for (const p of complete) {
    const rawName = sameBase && manyDirs ? p.dir.slice(p.dir.lastIndexOf("/") + 1) : p.base;
    let name = rawName.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 60) || "test";
    while (used.has(name.toLowerCase())) name += "_";
    used.add(name.toLowerCase());
    const input = (await p.input!.text()).replace(/\r\n/g, "\n");
    const output = (await p.output!.text()).replace(/\r\n/g, "\n");
    if (input.length > MAX_TEST_CHARS || output.length > MAX_TEST_CHARS) {
      return { rows: [], skipped, error: `Test "${name}" quá lớn (mỗi file tối đa 1 triệu ký tự).` };
    }
    total += input.length + output.length;
    if (total > MAX_TOTAL_CHARS) {
      return { rows: [], skipped, error: "Tổng dung lượng bộ test vượt 6 MB. Hãy tạo bài với ít test hơn rồi thêm dần ở mục Test." };
    }
    rows.push({ key: nextKey++, name, input, output, sample: name.toLowerCase().startsWith("sample") });
  }
  // Test vi du dung truoc, moi nhom xep theo ten (01, 02, ..., 10).
  rows.sort((a, b) => Number(b.sample) - Number(a.sample) || a.name.localeCompare(b.name, undefined, { numeric: true }));
  return { rows, skipped, error: "" };
}

const LABEL: React.CSSProperties = {
  fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 600, color: "var(--color-text-muted)",
  textTransform: "uppercase", letterSpacing: "0.09em", marginBottom: 4, display: "block",
};
const INPUT: React.CSSProperties = {
  width: "100%", height: 34, padding: "0 10px", background: "var(--color-bg-base)",
  border: "1px solid var(--color-border)", borderRadius: 7, color: "var(--color-text-primary)",
  fontFamily: "var(--font-mono)", fontSize: 12.5, outline: "none", boxSizing: "border-box",
};
const AREA: React.CSSProperties = { ...INPUT, height: "auto", padding: "8px 10px", lineHeight: 1.6, resize: "vertical" };

/**
 * Modal "Them bai tap moi". Backend chi ho tro TAO bai (POST /api/admin/problems),
 * khong co API sua/xoa - nen day la form tao moi, khong phai form sua.
 */
export default function ProblemEditor({ topics, onClose, onCreated }: { topics: Topic[]; onClose: () => void; onCreated: () => void }) {
  const [id, setId] = useState("");
  const [title, setTitle] = useState("");
  const [statement, setStatement] = useState("");
  const [timeLimitMs, setTimeLimitMs] = useState("1000");
  const [memoryLimitMb, setMemoryLimitMb] = useState("64");
  const [comparator, setComparator] = useState("token");
  const [totalPoints, setTotalPoints] = useState("100");
  const [topic, setTopic] = useState("");
  const [difficulty, setDifficulty] = useState(0);
  const [tests, setTests] = useState<TestRow[]>([newTestRow(true, "sample01"), newTestRow(false, "01")]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);

  const folderInput = useRef<HTMLInputElement>(null);
  const filesInput = useRef<HTMLInputElement>(null);
  const [folderNote, setFolderNote] = useState("");

  /** Nap bo test tu thu muc (hoac nhieu file) nguoi dung chon; thay cho cac dong test con trong. */
  async function loadTestFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;
    setError("");
    setFolderNote("");
    const loaded = await readTestFolder(files);
    if (loaded.error) { setError(loaded.error); return; }
    setTests((rows) => {
      // Giu lai cac test da nhap tay co noi dung; dong con trong thi bo. Trung ten thi ban tu thu muc thay the.
      const names = new Set(loaded.rows.map((t) => t.name.toLowerCase()));
      const kept = rows.filter((t) => (t.input.trim() || t.output.trim()) && !names.has(t.name.trim().toLowerCase()));
      return [...kept, ...loaded.rows].slice(0, MAX_TESTS);
    });
    const samples = loaded.rows.filter((t) => t.sample).length;
    setFolderNote(`Đã nạp ${loaded.rows.length} test (${samples} test ví dụ, ${loaded.rows.length - samples} test ẩn)`
      + (loaded.skipped > 0 ? `, bỏ qua ${loaded.skipped} file không ghép được cặp input / output.` : "."));
  }

  function patchTest(key: number, patch: Partial<TestRow>) {
    setTests((rows) => rows.map((t) => (t.key === key ? { ...t, ...patch } : t)));
  }
  function removeTest(key: number) {
    setTests((rows) => rows.filter((t) => t.key !== key));
  }

  function validate(): string {
    const cleanId = id.trim().toUpperCase();
    if (!/^[A-Z0-9_-]{1,30}$/.test(cleanId)) return "Mã bài chỉ gồm chữ in hoa, số, gạch dưới hoặc gạch ngang (tối đa 30 ký tự).";
    if (!title.trim()) return "Tên bài không được để trống.";
    if (!statement.trim()) return "Nội dung đề không được để trống.";
    if (!(Number(timeLimitMs) >= 100 && Number(timeLimitMs) <= 60000)) return "Giới hạn thời gian phải từ 100 đến 60000 ms.";
    if (!(Number(memoryLimitMb) >= 8 && Number(memoryLimitMb) <= 2048)) return "Giới hạn bộ nhớ phải từ 8 đến 2048 MB.";
    if (!(Number(totalPoints) > 0)) return "Tổng điểm phải lớn hơn 0.";
    if (tests.length === 0) return "Bài tập phải có ít nhất một test.";

    const seen = new Set<string>();
    for (const t of tests) {
      const name = t.name.trim();
      if (!/^[A-Za-z0-9_-]{1,64}$/.test(name)) return `Tên test "${name}" chỉ được gồm chữ, số, gạch dưới hoặc gạch ngang.`;
      if (seen.has(name.toLowerCase())) return `Tên test "${name}" bị trùng.`;
      seen.add(name.toLowerCase());
      const looksSample = name.toLowerCase().startsWith("sample");
      if (t.sample && !looksSample) return `Test ví dụ "${name}" phải có tên bắt đầu bằng "sample".`;
      if (!t.sample && looksSample) return `Test ẩn "${name}" không được đặt tên bắt đầu bằng "sample".`;
      if (!t.input.trim()) return `Test "${name}" chưa có input.`;
      if (!t.output.trim()) return `Test "${name}" chưa có output.`;
    }
    return "";
  }

  async function doCreate() {
    setError("");
    const problem = validate();
    if (problem) { setError(problem); return; }
    setBusy(true);
    try {
      await api.createProblem({
        id: id.trim().toUpperCase(),
        title: title.trim(),
        statement,
        timeLimitMs: Number(timeLimitMs),
        memoryLimitMb: Number(memoryLimitMb),
        comparator,
        totalPoints: Number(totalPoints),
        topic,
        difficulty,
        tests: tests.map((t) => ({ name: t.name.trim(), input: t.input, output: t.output, sample: t.sample })),
      });
      setSaved(true);
      onCreated();
      setTimeout(onClose, 700);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(28,20,16,0.5)", zIndex: 999, display: "flex", alignItems: "flex-start", justifyContent: "center", overflowY: "auto", padding: "32px 16px" }}>
      <div style={{ background: "var(--color-bg-panel)", border: "1px solid var(--color-border)", borderRadius: 14, width: "100%", maxWidth: 760, boxShadow: "0 24px 64px rgba(28,20,16,0.18)", marginBottom: 32 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "20px 28px", borderBottom: "1px solid var(--color-border)" }}>
          <h2 style={{ fontFamily: "var(--font-serif)", fontSize: 20, fontWeight: 700, color: "var(--color-maroon)", margin: 0, letterSpacing: "-0.02em" }}>New Problem</h2>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--color-text-muted)", padding: 6, borderRadius: 5, display: "flex" }}>
            <svg width="15" height="15" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="1" y1="1" x2="9" y2="9" /><line x1="9" y1="1" x2="1" y2="9" /></svg>
          </button>
        </div>

        <div style={{ padding: "24px 28px", display: "flex", flexDirection: "column", gap: 14 }}>
          {error && (
            <div style={{ padding: "10px 14px", borderRadius: 7, fontSize: 12.5, background: "var(--color-red-bg)", border: "1px solid var(--color-red-border)", color: "#C62828", fontFamily: "var(--font-mono)", whiteSpace: "pre-wrap" }}>{error}</div>
          )}
          {saved && (
            <div style={{ padding: "10px 14px", borderRadius: 7, fontSize: 12.5, background: "var(--color-green-bg)", border: "1px solid var(--color-green-border)", color: "var(--color-green)", fontFamily: "var(--font-mono)" }}>Đã tạo bài tập.</div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: 10 }}>
            <div>
              <label style={LABEL}>Mã bài</label>
              <input value={id} onChange={(e) => setId(e.target.value.toUpperCase())} placeholder="P004" style={INPUT} />
            </div>
            <div>
              <label style={LABEL}>Tên bài</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Tổng hai số nguyên" style={{ ...INPUT, fontFamily: "var(--font-sans)" }} />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 160px", gap: 10 }}>
            <div>
              <label style={LABEL}>Chủ đề trong lộ trình</label>
              <select value={topic} onChange={(e) => setTopic(e.target.value)} style={{ ...INPUT, fontFamily: "var(--font-sans)" }}>
                <option value="">(Chưa phân loại — hiện ở "Bài khác")</option>
                {topics.map((t) => <option key={t.id} value={t.id}>{t.order}. {t.name}</option>)}
              </select>
            </div>
            <div>
              <label style={LABEL}>Độ khó</label>
              <select value={difficulty} onChange={(e) => setDifficulty(Number(e.target.value))} style={{ ...INPUT, fontFamily: "var(--font-sans)" }}>
                <option value={0}>Chưa đánh giá</option>
                {[1, 2, 3].map((d) => <option key={d} value={d}>{DIFFICULTY_META[d].label}</option>)}
              </select>
            </div>
          </div>

          <div>
            <label style={LABEL}>Nội dung đề (statement.txt)</label>
            <textarea value={statement} onChange={(e) => setStatement(e.target.value)} spellCheck={false} rows={6}
              placeholder={"Cho hai số nguyên a và b.\n\nInput : một dòng chứa hai số a và b.\nOutput: một số duy nhất là a + b."}
              style={AREA} />
          </div>

          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <div style={{ width: 150 }}>
              <label style={LABEL}>Thời gian (ms)</label>
              <input value={timeLimitMs} onChange={(e) => setTimeLimitMs(e.target.value)} inputMode="numeric" style={INPUT} />
            </div>
            <div style={{ width: 150 }}>
              <label style={LABEL}>Bộ nhớ (MB)</label>
              <input value={memoryLimitMb} onChange={(e) => setMemoryLimitMb(e.target.value)} inputMode="numeric" style={INPUT} />
            </div>
            <div style={{ width: 150 }}>
              <label style={LABEL}>Tổng điểm</label>
              <input value={totalPoints} onChange={(e) => setTotalPoints(e.target.value)} inputMode="numeric" style={INPUT} />
            </div>
            <div>
              <label style={LABEL}>Kiểu so sánh</label>
              <Select value={comparator} options={COMPARATORS} onChange={(v) => setComparator(v || "token")} placeholder="Chọn kiểu so sánh" />
            </div>
          </div>

          <div style={{ borderTop: "1px solid var(--color-border)", paddingTop: 14, display: "flex", flexDirection: "column", gap: 12 }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <span style={LABEL as React.CSSProperties}>Bộ test ({tests.length})</span>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
                {/* webkitdirectory chua co trong kieu cua React nen truyen qua spread. */}
                <input ref={folderInput} type="file" multiple style={{ display: "none" }}
                  {...({ webkitdirectory: "", directory: "" } as object)} onChange={(e) => void loadTestFiles(e)} />
                <input ref={filesInput} type="file" multiple accept=".in,.inp,.out,.ans,.ok" style={{ display: "none" }}
                  onChange={(e) => void loadTestFiles(e)} />
                <button onClick={() => folderInput.current?.click()} style={ADD_BTN} title="Chọn cả một thư mục chứa các file test">↑ Tải thư mục test</button>
                <button onClick={() => filesInput.current?.click()} style={ADD_BTN} title="Chọn nhiều file .in / .out cùng lúc">↑ Tải nhiều file test</button>
                <button onClick={() => setTests((r) => [...r, newTestRow(true, `sample${r.length + 1}`)])} style={ADD_BTN}>+ Test ví dụ</button>
                <button onClick={() => setTests((r) => [...r, newTestRow(false, String(r.length + 1).padStart(2, "0"))])} style={ADD_BTN}>+ Test ẩn</button>
              </div>
            </div>

            <div style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--color-text-muted)", lineHeight: 1.7 }}>
              Test ví dụ hiển thị cho thí sinh (tên phải bắt đầu bằng <b>sample</b>). Test ẩn chỉ dùng để chấm.
              <br />
              Tải thư mục: mỗi test là một cặp file cùng tên, ví dụ <b>01.in</b> + <b>01.out</b> (nhận cả .inp, .ans),
              hoặc mỗi test một thư mục con như <b>Test01/BAI.INP</b> + <b>Test01/BAI.OUT</b>.
            </div>
            {folderNote && <div style={{ fontSize: 12.5, color: "var(--color-green)" }}>{folderNote}</div>}

            {tests.map((t, index) => (
              <div key={t.key} style={{ border: "1px solid var(--color-border)", borderRadius: 8, background: "var(--color-bg-raised)", padding: 12, display: "flex", flexDirection: "column", gap: 10 }}>
                <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                  <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--color-text-muted)", minWidth: 30 }}>#{index + 1}</span>
                  <div style={{ width: 180 }}>
                    <input value={t.name} onChange={(e) => patchTest(t.key, { name: e.target.value })} placeholder="tên file test" style={INPUT} />
                  </div>
                  <label style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--color-text-secondary)", display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}>
                    <input type="checkbox" checked={t.sample} onChange={(e) => {
                      const sample = e.target.checked;
                      const name = t.name.trim();
                      const looksSample = name.toLowerCase().startsWith("sample");
                      const nextName = sample
                        ? (looksSample ? name : `sample_${name}`)
                        : (looksSample ? name.replace(/^sample_?/i, "") || `${index + 1}` : name);
                      patchTest(t.key, { sample, name: nextName });
                    }} />
                    Test ví dụ
                  </label>
                  <div style={{ flex: 1 }} />
                  <button onClick={() => removeTest(t.key)} disabled={tests.length <= 1} style={{ ...ADD_BTN, opacity: tests.length <= 1 ? 0.5 : 1, cursor: tests.length <= 1 ? "not-allowed" : "pointer" }}>Xoá</button>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <div>
                    <label style={LABEL}>Input</label>
                    <textarea value={t.input} onChange={(e) => patchTest(t.key, { input: e.target.value })} spellCheck={false} rows={3} placeholder="1 2" style={AREA} />
                  </div>
                  <div>
                    <label style={LABEL}>Output mong đợi</label>
                    <textarea value={t.output} onChange={(e) => patchTest(t.key, { output: e.target.value })} spellCheck={false} rows={3} placeholder="3" style={AREA} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 10, padding: "16px 28px", borderTop: "1px solid var(--color-border)" }}>
          <button onClick={onClose} style={{ height: 36, padding: "0 20px", background: "none", border: "1px solid var(--color-border)", borderRadius: 7, color: "var(--color-text-muted)", fontSize: 13, cursor: "pointer", fontFamily: "var(--font-sans)" }}>Cancel</button>
          <button onClick={() => void doCreate()} disabled={busy} style={{ height: 36, padding: "0 24px", background: saved ? "var(--color-green)" : "var(--color-maroon)", border: "none", borderRadius: 7, color: "#FAF7F2", fontSize: 13, fontWeight: 700, cursor: busy ? "not-allowed" : "pointer", opacity: busy ? 0.7 : 1, fontFamily: "var(--font-sans)" }}>
            {busy ? "Đang tạo..." : saved ? "Saved!" : "Create problem"}
          </button>
        </div>
      </div>
    </div>
  );
}

const ADD_BTN: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 5, height: 28, padding: "0 11px", background: "none",
  border: "1px solid var(--color-border)", borderRadius: 6, color: "var(--color-text-muted)", fontSize: 11.5,
  cursor: "pointer", fontFamily: "var(--font-sans)",
};
