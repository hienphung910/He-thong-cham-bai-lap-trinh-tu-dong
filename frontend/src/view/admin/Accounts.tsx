import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, INPUT, Modal, Page, Table, Tag, errorText, useLoad } from "../../shared/kit";
import type { BasicUser } from "../../shared/platformTypes";
import { useAuth } from "../account/auth";

const ROLE_COLOR: Record<string, string> = { ADMIN: "red", TEACHER: "purple", STUDENT: "blue" };
const ROLE_LABEL: Record<string, string> = { ADMIN: "Quản trị viên", TEACHER: "Giảng viên", STUDENT: "Học sinh" };

/**
 * Module 1 (tai khoan va phan quyen): xem moi tai khoan,
 * loc theo vai tro, tao tai khoan giang vien / hoc sinh / quan tri. Chi quan tri vien.
 *
 * Tai khoan chua dang nhap duoc nam o muc "Tai khoan chua hoat dong", moi dong ghi ro trang thai:
 * "Cho duyet" (giang vien tu dang ky - nut Duyet / Tu choi) hoac "Dang khoa" (bi quan tri vien khoa -
 * nut Mo khoa / Xoa). Giang vien gui kem file minh chung thi bam Xem de mo file ngay trong trang; file
 * bi xoa khoi may chu sau khi duyet hoac tu choi. Tai khoan dang dung thi doi vai tro, khoa, xoa duoc
 * o bang duoi.
 */
export default function Accounts() {
  const { user: me } = useAuth();
  const users = useLoad(() => papi.users());
  const [role, setRole] = useState("");
  const [error, setError] = useState("");
  // Chua dang nhap duoc: cho duyet xep truoc (viec can lam), dang khoa xep sau.
  const inactive = (users.data ?? []).filter((u) => u.approved === false);
  const isPending = (u: BasicUser) => u.status === "PENDING";
  const pending = [...inactive.filter(isPending), ...inactive.filter((u) => !isPending(u))];
  const pendingCount = inactive.filter(isPending).length;
  const lockedCount = inactive.length - pendingCount;
  const shown = (users.data ?? []).filter((u) => u.approved !== false && (role === "" || u.role === role));

  // File minh chung dang mo: tai ve dang base64 roi doi thanh blob URL de hien anh / PDF.
  const [viewing, setViewing] = useState<{ user: BasicUser; contentType: string; url: string } | null>(null);

  async function openEvidence(u: BasicUser) {
    setError("");
    try {
      const file = await papi.userEvidence(u.id);
      const bytes = Uint8Array.from(atob(file.data), (ch) => ch.charCodeAt(0));
      const url = URL.createObjectURL(new Blob([bytes], { type: file.contentType }));
      setViewing({ user: u, contentType: file.contentType, url });
    } catch (e) { setError(errorText(e)); }
  }

  function closeEvidence() {
    if (viewing) URL.revokeObjectURL(viewing.url);
    setViewing(null);
  }

  async function decide(work: () => Promise<unknown>) {
    setError("");
    try { await work(); } catch (e) { setError(errorText(e)); }
    users.reload();
  }

  return (
    <Page title="Tài khoản và giảng viên">
      <ErrorBox text={error || users.error} />
      {pending.length > 0 && (
        <Card title="Tài khoản chưa hoạt động" right={<span style={{ display: "inline-flex", gap: 6 }}>
          {pendingCount > 0 && <Tag color="orange">{pendingCount} chờ duyệt</Tag>}
          {lockedCount > 0 && <Tag color="red">{lockedCount} đang khoá</Tag>}
        </span>}>
          <Table head={["Tên đăng nhập", "Họ tên", "Vai trò", "Lớp / bộ môn", "Trạng thái", "Minh chứng", ""]}
            rows={pending.map((u) => [
              <b>{u.username}</b>, u.fullName, <Tag color={ROLE_COLOR[u.role]}>{u.roleName}</Tag>, u.detail || "-",
              isPending(u) ? <Tag color="orange">Chờ duyệt</Tag> : <Tag color="red">Đang khoá</Tag>,
              u.hasEvidence
                ? <Btn small kind="ghost" onClick={() => void openEvidence(u)}>Xem</Btn>
                : <span style={{ color: "#9E9E9E" }}>-</span>,
              <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                <Btn small onClick={() => void decide(() => papi.approveUser(u.id))}>{isPending(u) ? "Duyệt" : "Mở khoá"}</Btn>
                <Btn small kind="danger" onClick={() => {
                  const question = isPending(u)
                    ? `Từ chối đăng ký giảng viên của @${u.username}? Tài khoản sẽ bị xoá.`
                    : `Xoá hẳn tài khoản đang khoá @${u.username}?\n\nBài nộp của tài khoản này cũng bị xoá, không khôi phục được.`;
                  if (window.confirm(question)) void decide(() => papi.rejectUser(u.id));
                }}>{isPending(u) ? "Từ chối" : "Xoá"}</Btn>
              </div>,
            ])} />
        </Card>
      )}
      {viewing && (
        <Modal title={`Minh chứng - ${viewing.user.fullName} (@${viewing.user.username})`} onClose={closeEvidence} width={780}>
          {viewing.contentType === "application/pdf"
            ? <iframe src={viewing.url} title="Minh chứng" style={{ width: "100%", height: "68vh", border: "1px solid #E0E0E0", borderRadius: 6 }} />
            : (
              <div style={{ maxHeight: "68vh", overflow: "auto", textAlign: "center", background: "#FAFAFA", border: "1px solid #E0E0E0", borderRadius: 6 }}>
                <img src={viewing.url} alt="Minh chứng" style={{ maxWidth: "100%", display: "block", margin: "0 auto" }} />
              </div>
            )}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 12 }}>
            <Btn onClick={() => { const id = viewing.user.id; closeEvidence(); void decide(() => papi.approveUser(id)); }}>Duyệt</Btn>
          </div>
        </Modal>
      )}
      <div style={{ display: "flex", gap: 8 }}>
        {[["", "Tất cả"], ["STUDENT", "Học sinh"], ["TEACHER", "Giảng viên"], ["ADMIN", "Quản trị"]].map(([key, label]) => (
          <Btn key={key} small kind={role === key ? "primary" : "ghost"} onClick={() => setRole(key)}>{label}</Btn>
        ))}
      </div>
      <Table head={["Tên đăng nhập", "Họ tên", "Vai trò", "Lớp / bộ môn", ""]}
        rows={shown.map((u) => [
          <b>{u.username}</b>, u.fullName, <Tag color={ROLE_COLOR[u.role]}>{u.roleName}</Tag>, u.detail || "-",
          u.id === me?.id ? <span style={{ fontSize: 12, color: "#9E9E9E" }}>Tài khoản của bạn</span> : (
            <div style={{ display: "flex", gap: 6, justifyContent: "flex-end", alignItems: "center", flexWrap: "wrap" }}>
              <select title="Đổi vai trò" style={{ ...INPUT, width: 132, height: 28, fontSize: 12 }} value={u.role}
                onChange={(e) => {
                  const role = e.target.value;
                  if (window.confirm(`Đổi vai trò của @${u.username} thành ${ROLE_LABEL[role]}?\n\nTài khoản sẽ bị đăng xuất và rời khỏi các lớp đang tham gia.`)) {
                    void decide(() => papi.setUserRole(u.id, role));
                  }
                }}>
                {Object.keys(ROLE_LABEL).map((role) => <option key={role} value={role}>{ROLE_LABEL[role]}</option>)}
              </select>
              <Btn small kind="ghost" onClick={() => {
                if (window.confirm(`Khoá tài khoản @${u.username}? Tài khoản bị đăng xuất ngay và không đăng nhập lại được cho tới khi bạn kích hoạt lại.`)) {
                  void decide(() => papi.lockUser(u.id));
                }
              }}>Khoá</Btn>
              <Btn small kind="danger" onClick={() => {
                if (window.confirm(`Xoá hẳn tài khoản @${u.username}?\n\nBài nộp của tài khoản này cũng bị xoá, không khôi phục được.`)) {
                  void decide(() => papi.deleteUser(u.id));
                }
              }}>Xoá</Btn>
            </div>
          ),
        ])} />
    </Page>
  );
}
