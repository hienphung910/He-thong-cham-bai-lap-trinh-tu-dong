import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Row, SimNote, errorText } from "../../shared/kit";
import CodeEditor from "../judge/CodeEditor";
import type { Problem } from "../../shared/types";

interface Turn { question: string; answer: string[] }

/**
 * Module 28 (tro ly AI cho hoc sinh) - ban mo phong. May chu tra loi bang cac luat viet san
 * dua tren ket qua lan nop gan nhat, vai dau hieu trong ma nguon va tu khoa trong cau hoi.
 */
export default function Assistant({ problems }: { problems: Problem[] }) {
  const [problemId, setProblemId] = useState("");
  const [code, setCode] = useState("");
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const problem = problems.find((p) => p.id === problemId.trim().toUpperCase());

  async function ask() {
    setBusy(true);
    setError("");
    try {
      const reply = await papi.hint({ problemId, code, question });
      setTurns([...turns, { question: question || "(xin gợi ý cho bài này)", answer: reply.lines }]);
      setQuestion("");
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page title="Trợ lý AI lập trình" subtitle="Hỏi gợi ý cho một bài tập. Trợ lý chỉ gợi ý hướng làm, không đưa lời giải.">
      <SimNote>
        Đây không phải mô hình AI thật: câu trả lời được ghép từ các luật viết sẵn (theo verdict lần nộp gần nhất,
        dấu hiệu trong mã nguồn, từ khóa trong câu hỏi). Mã nguồn của bạn không được gửi ra ngoài máy chủ này.
      </SimNote>
      <ErrorBox text={error} />
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Card title="Câu hỏi" style={{ flex: "1 1 380px", minWidth: 0 }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <Row>
              <Field label="Mã bài (ví dụ K001)">
                <input style={INPUT} value={problemId} onChange={(e) => setProblemId(e.target.value)} list="assistant-problems" />
                <datalist id="assistant-problems">
                  {problems.slice(0, 400).map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
                </datalist>
              </Field>
            </Row>
            {problem && <div style={{ fontSize: 13, color: "#616161" }}>Bài: <b>{problem.title}</b></div>}
            <Field label="Mã nguồn hiện tại của bạn (không bắt buộc)">
              <CodeEditor value={code} onChange={setCode} minHeight={160} />
            </Field>
            <Field label="Câu hỏi (thử: gợi ý ý tưởng, vì sao bị TLE, làm sao tự test)">
              <input style={INPUT} value={question} onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && problemId.trim()) void ask(); }} />
            </Field>
            <div><Btn disabled={busy || !problemId.trim()} onClick={() => void ask()}>{busy ? "Đang trả lời..." : "Hỏi trợ lý"}</Btn></div>
          </div>
        </Card>
        <Card title="Trả lời" style={{ flex: "1 1 380px", minWidth: 0 }}>
          {turns.length === 0 && <span style={{ fontSize: 13, color: "#9E9E9E" }}>Chưa có câu hỏi nào.</span>}
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            {[...turns].reverse().map((t, i) => (
              <div key={i}>
                <div style={{ fontSize: 13, fontWeight: 700, color: "#1565C0", marginBottom: 4 }}>Bạn: {t.question}</div>
                <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13.5, color: "#424242", lineHeight: 1.6 }}>
                  {t.answer.map((line, j) => <li key={j}>{line}</li>)}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </Page>
  );
}
