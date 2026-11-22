import { papi } from "../../shared/platformApi";
import { Card, ErrorBox, Grid, Page, Stat, Table, Tag, useLoad } from "../../shared/kit";
import { useAuth } from "../account/auth";

/**
 * Module 38 (huy hieu, diem thuong, thanh tich). Diem thuong: moi bai da giai duoc
 * 10 / 20 / 40 diem theo do kho. Huy hieu va hang deu tinh tu lich su nop bai.
 */
export default function Achievements() {
  const { user } = useAuth();
  const profile = useLoad(() => papi.myProfile());
  const board = useLoad(() => papi.pointBoard());
  const p = profile.data;

  return (
    <Page title="Thành tích" subtitle="Điểm thưởng, hạng và huy hiệu của bạn.">
      <ErrorBox text={profile.error || board.error} />
      {p && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <Stat label="Điểm thưởng" value={p.points} />
            <Stat label="Hạng" value={p.rankName} color="var(--color-purple)" />
            <Stat label="Chuỗi ngày học" value={`${p.streak} ngày`} color="var(--color-orange)" />
            <Stat label="Huy hiệu" value={`${p.badges.filter((b) => b.earned).length}/${p.badges.length}`} color="var(--color-green)" />
          </div>
          <Card title="Huy hiệu">
            <Grid min={210}>
              {p.badges.map((b) => (
                <div key={b.name} style={{
                  border: `1px solid ${b.earned ? "#FFD54F" : "#E0E0E0"}`, borderRadius: 8, padding: "12px 14px",
                  background: b.earned ? "#FFFDE7" : "#FAFAFA", opacity: b.earned ? 1 : 0.6,
                }}>
                  <div style={{ fontSize: 22 }}>{b.earned ? "🏅" : "🔒"}</div>
                  <div style={{ fontSize: 14, fontWeight: 700, color: "#212121" }}>{b.name}</div>
                  <div style={{ fontSize: 12, color: "#757575" }}>{b.description}</div>
                </div>
              ))}
            </Grid>
          </Card>
        </>
      )}
      <Card title="Bảng điểm thưởng">
        <Table head={["#", "Người dùng", "Điểm", "Hạng", "Số bài"]} empty="Chưa ai giải được bài nào."
          rows={(board.data ?? []).map((r) => [
            r.rank,
            <span style={{ fontWeight: r.username === user?.username ? 800 : 500 }}>{r.fullName} <span style={{ color: "#9E9E9E" }}>@{r.username}</span></span>,
            <b>{r.points}</b>, <Tag color="purple">{r.rankName}</Tag>, r.solved,
          ])} />
      </Card>
    </Page>
  );
}
