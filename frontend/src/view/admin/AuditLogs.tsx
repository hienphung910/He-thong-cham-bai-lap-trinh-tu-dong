import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, INPUT, Page, Table, Tag, fmtTime, useLoad } from "../../shared/kit";

/**
 * Module 40 (bao mat va nhat ky hoat dong): may chu ghi mot dong cho moi request lam thay doi
 * du lieu (POST / PUT / DELETE) va moi lan dang nhap, ke ca dang nhap sai. Chi quan tri vien.
 */
export default function AuditLogs() {
  const [typing, setTyping] = useState("");
  const [keyword, setKeyword] = useState("");
  const logs = useLoad(() => papi.audit(keyword), [keyword]);

  return (
    <Page title="Nhật ký hoạt động"
      actions={<>
        <input style={{ ...INPUT, width: 220 }} value={typing} onChange={(e) => setTyping(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") setKeyword(typing); }} placeholder="vd: admin, classrooms, Đăng nhập" />
        <Btn onClick={() => setKeyword(typing)}>Lọc</Btn>
        <Btn kind="ghost" onClick={logs.reload}>Tải lại</Btn>
      </>}>
      <ErrorBox text={logs.error} />
      <Table head={["Thời điểm", "Người dùng", "Hành động", "Chi tiết", "Kết quả"]} empty="Không có dòng nhật ký nào khớp."
        rows={(logs.data ?? []).map((l) => [
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 12 }}>{fmtTime(l.time)}:{l.time.slice(17, 19)}</span>,
          <b>{l.username || "(trống)"}</b>,
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 12 }}>{l.action}</span>,
          l.detail,
          <Tag color={l.success ? "green" : "red"}>{l.success ? "Thành công" : "Thất bại"}</Tag>,
        ])} />
    </Page>
  );
}
