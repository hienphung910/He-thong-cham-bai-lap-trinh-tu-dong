import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Page, Table, Tag, errorText, fmtTime, useLoad } from "../../shared/kit";
import ProblemChip from "../../shared/ProblemChip";
import type { ProblemComment } from "../../shared/platformTypes";

/**
 * Quan tri vien duyet binh luan duoi cac bai tap. Binh luan cho duyet chi tac gia thay;
 * duyet thi moi nguoi thay, tu choi thi binh luan bi xoa va tac gia nhan thong bao.
 */
export default function CommentModeration() {
  const list = useLoad(() => papi.allComments());
  const [error, setError] = useState("");
  const pending = (list.data ?? []).filter((c) => c.status === "PENDING");
  const approved = (list.data ?? []).filter((c) => c.status === "APPROVED");

  async function act(work: () => Promise<unknown>) {
    setError("");
    try { await work(); list.reload(); } catch (e) { setError(errorText(e)); }
  }

  function row(c: ProblemComment) {
    return [
      <span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <ProblemChip id={c.problemId} title={c.problemTitle} />
        <span style={{ fontSize: 12, color: "#757575" }}>{c.problemTitle}</span>
      </span>,
      <span><b>{c.authorName}</b><div style={{ fontSize: 12, color: "#9E9E9E" }}>{c.authorRole}</div></span>,
      <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word", maxWidth: 520, lineHeight: 1.5 }}>{c.text}</div>,
      fmtTime(c.createdAt),
      c.status === "PENDING" ? (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <Btn small onClick={() => void act(() => papi.approveComment(c.id))}>Duyệt</Btn>
          <Btn small kind="danger" onClick={() => void act(() => papi.deleteComment(c.id))}>Từ chối</Btn>
        </div>
      ) : (
        <Btn small kind="danger" onClick={() => { if (window.confirm("Xoá bình luận đã duyệt này?")) void act(() => papi.deleteComment(c.id)); }}>Xoá</Btn>
      ),
    ];
  }

  const HEAD = ["Bài", "Người gửi", "Nội dung", "Thời điểm", ""];

  return (
    <Page title="Duyệt bình luận" subtitle="Bình luận dưới bài tập chỉ hiện cho mọi người sau khi được duyệt.">
      <ErrorBox text={error || list.error} />
      <Card title="Chờ duyệt" right={<Tag color={pending.length > 0 ? "orange" : "gray"}>{pending.length} bình luận</Tag>}>
        <Table head={HEAD} empty="Không có bình luận nào đang chờ duyệt." rows={pending.map(row)} />
      </Card>
      <Card title="Đã duyệt" right={<Tag color="green">{approved.length} bình luận</Tag>}>
        <Table head={HEAD} empty="Chưa có bình luận nào được duyệt." rows={approved.map(row)} />
      </Card>
    </Page>
  );
}
