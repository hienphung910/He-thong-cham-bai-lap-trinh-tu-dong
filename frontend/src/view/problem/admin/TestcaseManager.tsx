import { useState } from "react";
import { papi } from "../../../shared/platformApi";
import { Btn, ErrorBox, Field, INPUT, Modal, Row, TEXTAREA, Table, Tag, errorText, useLoad } from "../../../shared/kit";
import type { Problem } from "../../../shared/types";
import type { TestcaseItem } from "../../../shared/platformTypes";

const MONO: React.CSSProperties = { fontFamily: "var(--font-mono)", fontSize: 12, whiteSpace: "pre-wrap", maxHeight: 90, overflow: "auto", display: "block" };

/**
 * Module 7 (quan ly bo test): xem toan bo test cua mot bai (ca test an), them va xoa test.
 * Chi quan tri vien mo duoc hop thoai nay, vi day la noi duy nhat hien test an.
 */
export default function TestcaseManager({ problem, onClose }: { problem: Problem; onClose: () => void }) {
  const loaded = useLoad(() => papi.testcases(problem.id), [problem.id]);
  /** Danh sach moi nhat: may chu tra lai sau moi lan them / xoa. */
  const [changed, setChanged] = useState<TestcaseItem[] | null>(null);
  const [form, setForm] = useState({ name: "", input: "", output: "", sample: false });
  const [error, setError] = useState("");
  const tests = changed ?? loaded.data ?? [];

  async function act(work: () => Promise<TestcaseItem[]>) {
    setError("");
    try { setChanged(await work()); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Modal title={`Bộ test của ${problem.id} — ${problem.title}`} onClose={onClose} width={860}>
      <ErrorBox text={error || loaded.error} />
      <Table head={["Tên test", "Loại", "Điểm", "Input", "Output", ""]} empty="Đang tải..."
        rows={tests.map((t) => [
          <b>{t.name}</b>,
          <Tag color={t.sample ? "blue" : "gray"}>{t.sample ? "Ví dụ" : "Ẩn"}</Tag>,
          Number(t.points.toFixed(2)),
          <code style={MONO}>{t.input}</code>,
          <code style={MONO}>{t.output}</code>,
          <Btn small kind="danger" disabled={tests.length <= 1}
            onClick={() => { if (window.confirm(`Xoá test "${t.name}"?`)) void act(() => papi.deleteTestcase(problem.id, t.name)); }}>Xoá</Btn>,
        ])} />

      <b style={{ fontSize: 13 }}>Thêm test</b>
      <Row>
        <div style={{ width: 200 }}>
          <Field label={form.sample ? "Tên (bắt đầu bằng sample)" : "Tên (ví dụ 08)"}>
            <input style={INPUT} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
        </div>
        <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, height: 34 }}>
          <input type="checkbox" checked={form.sample} onChange={(e) => setForm({ ...form, sample: e.target.checked })} /> Là test ví dụ (học sinh nhìn thấy)
        </label>
      </Row>
      <Row>
        <Field label="Input"><textarea style={{ ...TEXTAREA, fontFamily: "var(--font-mono)" }} value={form.input} onChange={(e) => setForm({ ...form, input: e.target.value })} /></Field>
        <Field label="Output đúng"><textarea style={{ ...TEXTAREA, fontFamily: "var(--font-mono)" }} value={form.output} onChange={(e) => setForm({ ...form, output: e.target.value })} /></Field>
      </Row>
      <div>
        <Btn onClick={() => void act(async () => {
          const next = await papi.addTestcase(problem.id, form);
          setForm({ name: "", input: "", output: "", sample: false });
          return next;
        })}>Thêm test</Btn>
      </div>
      <div style={{ fontSize: 12, color: "#9E9E9E" }}>Điểm của bài được chia đều lại cho các test sau mỗi lần thêm hoặc xoá.</div>
    </Modal>
  );
}
