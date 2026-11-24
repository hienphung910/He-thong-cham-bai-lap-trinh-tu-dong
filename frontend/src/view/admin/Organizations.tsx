import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, Row, Table, errorText, useLoad } from "../../shared/kit";

/** Module 2 (quan ly trung tam, to chuc): moi to chuc gom nhieu lop hoc. Chi quan tri vien. */
export default function Organizations() {
  const list = useLoad(() => papi.organizations());
  const [form, setForm] = useState({ name: "", address: "", description: "" });
  const [error, setError] = useState("");

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Tổ chức">
      <ErrorBox text={error || list.error} />
      <Card title="Thêm tổ chức">
        <Row>
          <Field label="Tên tổ chức"><input style={INPUT} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="Địa chỉ"><input style={INPUT} value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></Field>
          <Field label="Mô tả"><input style={INPUT} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
          <Btn onClick={() => void act(async () => { await papi.createOrganization(form); setForm({ name: "", address: "", description: "" }); })}>Thêm</Btn>
        </Row>
      </Card>
      <Table head={["Mã", "Tên", "Địa chỉ", "Mô tả", "Số lớp", ""]} empty="Chưa có tổ chức nào."
        rows={(list.data ?? []).map((o) => [
          o.id, <b>{o.name}</b>, o.address || "—", o.description || "—", o.classCount,
          <Btn small kind="danger" onClick={() => { if (window.confirm(`Xoá tổ chức "${o.name}"?`)) void act(() => papi.deleteOrganization(o.id)); }}>Xoá</Btn>,
        ])} />
    </Page>
  );
}
