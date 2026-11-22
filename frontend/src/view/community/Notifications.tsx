import { useEffect } from "react";
import { papi } from "../../shared/platformApi";
import { Card, ErrorBox, Muted, Page, Tag, fmtTime, useLoad } from "../../shared/kit";

/**
 * Module 29 (thong bao): danh sach thong bao cua toi. May chu tu tao thong bao khi co
 * bai tap moi, buoi hoc moi, thanh toan, hoac khi giang vien gui thong bao cho lop.
 * Mo trang nay la danh dau da doc het.
 */
export default function Notifications({ onRead }: { onRead: () => void }) {
  const list = useLoad(() => papi.notifications());

  useEffect(() => {
    if (!list.data || list.data.unread === 0) return;
    papi.markNotificationsRead().then(onRead).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.data]);

  return (
    <Page title="Thông báo" subtitle="50 thông báo gần nhất của bạn.">
      <ErrorBox text={list.error} />
      {list.data?.items.length === 0 && <Muted>Bạn chưa có thông báo nào.</Muted>}
      {(list.data?.items ?? []).map((n) => (
        <Card key={n.id}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <b style={{ fontSize: 14, color: "#212121" }}>{n.title}</b>
            {!n.read && <Tag color="red">Mới</Tag>}
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "#9E9E9E" }}>{fmtTime(n.createdAt)}</span>
          </div>
          <div style={{ fontSize: 13.5, color: "#616161", marginTop: 4, whiteSpace: "pre-wrap" }}>{n.body}</div>
        </Card>
      ))}
    </Page>
  );
}
