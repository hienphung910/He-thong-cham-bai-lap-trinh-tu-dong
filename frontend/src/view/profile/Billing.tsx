import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Grid, Page, SimNote, Table, Tag, errorText, fmtTime, useLoad } from "../../shared/kit";

/** 99000 -> "99.000 đ" */
export function money(amount: number): string {
  return amount.toLocaleString("vi-VN") + " đ";
}

/**
 * Module 34 (goi dich vu va thanh toan) - ban mo phong: khong co cong thanh toan,
 * khong nhap thong tin the. Bam nut la may chu ghi nhan mot giao dich thanh cong.
 */
export default function Billing() {
  const billing = useLoad(() => papi.billing());
  const [error, setError] = useState("");
  const b = billing.data;

  async function pay(code: string, name: string) {
    if (!window.confirm(`Xác nhận thanh toán MÔ PHỎNG cho gói "${name}"? Không có tiền thật nào bị trừ.`)) return;
    setError("");
    try { await papi.pay(code); billing.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Gói dịch vụ" subtitle="Chọn gói phù hợp. Mỗi lần thanh toán có hiệu lực 30 ngày.">
      <SimNote>Thanh toán là mô phỏng: không kết nối ngân hàng hay ví điện tử, không yêu cầu thông tin thẻ, không có tiền thật.</SimNote>
      <ErrorBox text={error || billing.error} />
      {b && (
        <>
          <Grid min={240}>
            {b.plans.map((plan) => {
              const current = plan.code === b.currentPlan;
              return (
                <Card key={plan.code} title={plan.name} right={current && <Tag color="green">Đang dùng</Tag>}
                  style={{ borderColor: current ? "#A5D6A7" : "#E0E0E0" }}>
                  <div style={{ fontSize: 24, fontWeight: 800, color: "var(--color-red)" }}>
                    {plan.price === 0 ? "0 đ" : money(plan.price)}<span style={{ fontSize: 12, color: "#9E9E9E", fontWeight: 500 }}> / tháng</span>
                  </div>
                  <div style={{ fontSize: 13, color: "#616161", margin: "8px 0 12px", minHeight: 40 }}>{plan.features}</div>
                  {plan.price > 0 && <Btn onClick={() => void pay(plan.code, plan.name)}>{current ? "Gia hạn (mô phỏng)" : "Thanh toán (mô phỏng)"}</Btn>}
                </Card>
              );
            })}
          </Grid>
          <Card title="Lịch sử giao dịch của bạn">
            <Table head={["Mã", "Gói", "Số tiền", "Thời điểm", "Hết hạn", "Trạng thái"]} empty="Bạn chưa có giao dịch nào."
              rows={b.payments.map((p) => [
                p.id, p.planName, money(p.amount), fmtTime(p.createdAt), fmtTime(p.expiresAt),
                <Tag color={p.active ? "green" : "gray"}>{p.active ? "Còn hiệu lực" : "Đã hết hạn"}</Tag>,
              ])} />
          </Card>
        </>
      )}
    </Page>
  );
}
