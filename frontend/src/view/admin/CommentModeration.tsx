import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, ErrorBox, Page, Table, errorText, fmtTime, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import { confirmDialog } from "../../shared/dialog";

/**
 * Quan ly binh luan duoi cac bai tap. Binh luan khong con qua buoc duyet: may chu loc tu bay va ma
 * nguon loi giai luc gui, qua duoc thi hien ngay. Trang nay de xoa binh luan lot luoi: quan tri vien
 * thay moi binh luan, giang vien thay binh luan duoi bai trong kho rieng cua minh.
 */
export default function CommentModeration() {
  const list = useLoad(() => papi.allComments());
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const q = search.trim().toLowerCase();
  const shown = (list.data ?? []).filter((c) => !q || c.text.toLowerCase().includes(q)
    || c.authorName.toLowerCase().includes(q) || c.problemId.toLowerCase().includes(q));

  async function remove(id: string) {
    if (!(await confirmDialog({ title: "Xoá bình luận này?", message: "Người viết sẽ nhận được thông báo.", confirmLabel: "Xoá bình luận" }))) return;
    setError("");
    try { await papi.deleteComment(id); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  return (
    <Page title="Bình luận bài tập"
      actions={<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm theo nội dung, người viết, mã bài"
        style={{ height: 34, width: 300, padding: "0 12px", border: "1px solid #E0E0E0", borderRadius: 6, fontSize: 13, outline: "none" }} />}>
      <ErrorBox text={error || list.error} />
      <Table head={["Bài", "Người viết", "Nội dung", "Thời điểm", ""]} empty="Chưa có bình luận nào."
        rows={shown.map((c) => [
          <span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <ProblemChip id={c.problemId} title={c.problemTitle} />
            <span style={{ fontSize: 12, color: "#757575" }}>{c.problemTitle}</span>
          </span>,
          <span><b>{c.authorName}</b><div style={{ fontSize: 12, color: "#9E9E9E" }}>{c.authorRole}</div></span>,
          <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word", maxWidth: 520, lineHeight: 1.5 }}>{c.text}</div>,
          fmtTime(c.createdAt),
          <Btn small kind="danger" onClick={() => void remove(c.id)}>Xoá</Btn>,
        ])} />
    </Page>
  );
}
