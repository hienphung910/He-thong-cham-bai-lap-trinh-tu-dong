import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, Field, INPUT, Modal, Table, Tag, errorText } from "../../shared/kit";
import { PrintArea } from "../../shared/print";
import { downloadXlsx } from "../../shared/xlsx";
import type { CreatedAccount } from "../../shared/platformTypes";
import "./report.css";

/**
 * Tao tai khoan cho ca lop tu danh sach ho ten (dan tu Excel / Word, moi dong mot em) - lop pho thong
 * khong can email. Ket qua: ten dang nhap + mat khau tam, in thanh the cat phat cho tung em hoac tai
 * danh sach Excel. Mat khau chi tra ve mot lan nay (may chu chi luu ban bam).
 */
export default function BulkAccounts({ classroomId, classroomName, onClose, onCreated }: {
  classroomId: string; classroomName: string; onClose: () => void; onCreated: () => void;
}) {
  const [names, setNames] = useState("");
  const [className, setClassName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<CreatedAccount[] | null>(null);
  const lines = names.split(/\r?\n/).filter((l) => l.trim()).length;

  async function create() {
    setBusy(true);
    setError("");
    try {
      const res = await papi.bulkCreateStudents(classroomId, names, className.trim());
      setCreated(res.created);
      onCreated();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  function exportList(list: CreatedAccount[]) {
    downloadXlsx(`Tai khoan - ${classroomName}`, "Tài khoản",
      [["STT", "Họ và tên", "Tên đăng nhập", "Mật khẩu"], ...list.map((a, i) => [i + 1, a.fullName, a.username, a.password])],
      [6, 30, 18, 14]);
  }

  return (
    <Modal title={`Tạo tài khoản học sinh - ${classroomName}`} width={720} onClose={onClose}>
      {!created && (
        <>
          <Field label={`Họ và tên học sinh, mỗi dòng một em${lines > 0 ? ` (${lines} dòng)` : ""}`}>
            <textarea value={names} onChange={(e) => setNames(e.target.value)} rows={10}
              placeholder={"Nguyễn Văn An\nTrần Thị Bích Ngọc\n..."}
              style={{ ...INPUT, height: "auto", padding: "8px 10px", lineHeight: 1.5, resize: "vertical", fontFamily: "inherit" }} />
          </Field>
          <Field label="Lớp (tuỳ chọn)">
            <input style={{ ...INPUT, maxWidth: 200 }} value={className} onChange={(e) => setClassName(e.target.value)} placeholder="10A1" />
          </Field>
          <ErrorBox text={error} />
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <Btn kind="ghost" onClick={onClose}>Huỷ</Btn>
            <Btn disabled={busy || lines === 0} onClick={() => void create()}>{busy ? "Đang tạo..." : `Tạo ${lines || ""} tài khoản`}</Btn>
          </div>
        </>
      )}
      {created && (
        <>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <Tag color="green">Đã tạo {created.length} tài khoản</Tag>
            <Tag color="orange">Mật khẩu chỉ hiện lần này</Tag>
            <span style={{ flex: 1 }} />
            <Btn small kind="ghost" onClick={() => exportList(created)}>Tải danh sách Excel</Btn>
            <Btn small onClick={() => window.print()}>In thẻ đăng nhập</Btn>
          </div>
          <Table head={["Họ và tên", "Tên đăng nhập", "Mật khẩu"]}
            rows={created.map((a) => [
              a.fullName,
              <b style={{ fontFamily: "var(--font-mono)" }}>{a.username}</b>,
              <span style={{ fontFamily: "var(--font-mono)" }}>{a.password}</span>,
            ])} />
          <PrintArea><AccountCards accounts={created} classroomName={classroomName} /></PrintArea>
        </>
      )}
    </Modal>
  );
}

/** The dang nhap de in: moi the mot em, hai the mot hang, vien net dut de cat. */
export function AccountCards({ accounts, classroomName }: { accounts: CreatedAccount[]; classroomName: string }) {
  const site = window.location.host;
  return (
    <div className="ac-cards">
      {accounts.map((a) => (
        <div key={a.userId || a.username} className="ac-card">
          <div className="ac-card-brand">CodeHub - {classroomName}</div>
          <div className="ac-card-name">{a.fullName}</div>
          <table>
            <tbody>
              <tr><th>Tên đăng nhập</th><td>{a.username}</td></tr>
              <tr><th>Mật khẩu</th><td>{a.password}</td></tr>
            </tbody>
          </table>
          <div className="ac-card-url">Đăng nhập tại {site}</div>
        </div>
      ))}
    </div>
  );
}
