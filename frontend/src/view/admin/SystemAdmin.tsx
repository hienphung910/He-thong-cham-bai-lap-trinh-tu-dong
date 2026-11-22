import { useEffect, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Field, INPUT, Page, SimNote, Table, errorText, fmtTime, useLoad } from "../../shared/kit";
import { money } from "../profile/Billing";

/**
 * Module 39 (quan tri he thong): thong tin may chu, cau hinh chung (thong bao toan he thong,
 * cho phep tu dang ky). Kem bang doanh thu cua Module 34 (thanh toan mo phong).
 */
export default function SystemAdmin({ onSettingsChanged }: { onSettingsChanged: () => void }) {
  const info = useLoad(() => papi.systemInfo());
  const settings = useLoad(() => papi.settings());
  const payments = useLoad(() => papi.allPayments());
  const [announcement, setAnnouncement] = useState("");
  const [allowRegister, setAllowRegister] = useState(true);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  // Co cau hinh tu may chu thi dua vao cac o nhap.
  useEffect(() => {
    if (!settings.data) return;
    setAnnouncement(settings.data.announcement);
    setAllowRegister(settings.data.allowRegister);
  }, [settings.data]);

  async function save() {
    setError("");
    setSaved(false);
    try {
      await papi.updateSettings({ announcement, allowRegister });
      setSaved(true);
      onSettingsChanged();
    } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Quản trị hệ thống">
      <ErrorBox text={error || info.error || settings.error || payments.error} />
      <Card title="Cấu hình">
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <Field label="Thông báo chung (hiện ở đầu mọi trang; để trống thì ẩn)">
            <input style={INPUT} value={announcement} onChange={(e) => setAnnouncement(e.target.value)} />
          </Field>
          <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13.5 }}>
            <input type="checkbox" checked={allowRegister} onChange={(e) => setAllowRegister(e.target.checked)} />
            Cho phép học sinh tự đăng ký tài khoản
          </label>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <Btn onClick={() => void save()}>Lưu cấu hình</Btn>
            {saved && <span style={{ fontSize: 13, color: "#2E7D32" }}>Đã lưu.</span>}
          </div>
        </div>
      </Card>

      <Card title="Thông tin máy chủ" right={<Btn small kind="ghost" onClick={info.reload}>Tải lại</Btn>}>
        <Table head={["Mục", "Giá trị"]} rows={(info.data ?? []).map((r) => [<b>{r.label}</b>, r.value])} />
      </Card>

      <Card title={`Giao dịch gói dịch vụ — tổng ${money(payments.data?.revenue ?? 0)}`}>
        <SimNote>Số liệu thanh toán là mô phỏng, không có tiền thật.</SimNote>
        <div style={{ height: 10 }} />
        <Table head={["Mã", "Người mua", "Gói", "Số tiền", "Thời điểm"]} empty="Chưa có giao dịch nào."
          rows={(payments.data?.payments ?? []).map((p) => [p.id, p.buyerName, p.planName, money(p.amount), fmtTime(p.createdAt)])} />
      </Card>
    </Page>
  );
}
