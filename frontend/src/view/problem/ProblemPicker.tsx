import { useMemo, useState } from "react";
import { api } from "../../shared/api";
import { Btn, ErrorBox, INPUT, Modal, Tag, useLoad } from "../../shared/kit";
import { useAuth } from "../account/auth";
import { DIFFICULTY_META, OTHER_TOPIC, orderByPath, topicKey } from "./learningPath";
import type { Problem } from "../../shared/types";

const CELL: React.CSSProperties = { padding: "7px 12px", textAlign: "left", borderBottom: "1px solid #F0F0F0", fontSize: 13, color: "#424242" };
const HEAD: React.CSSProperties = {
  ...CELL, fontSize: 11, fontWeight: 700, color: "#757575", background: "var(--color-table-header-bg)",
  whiteSpace: "nowrap", position: "sticky", top: 0, zIndex: 1,
};

/**
 * Bang chon bai: giang vien tich cac bai muon them (cho lop, ...) thay vi go tung ma bai.
 * Chi liet ke bai giang vien DUNG duoc: bai kho chung va bai trong kho rieng cua chinh minh.
 * Loc duoc theo kho, chu de, do kho va tim theo ma / ten bai.
 */
export default function ProblemPicker({ title, excluded, onPick, onClose }: {
  title: string;
  /** Ma cac bai da co san: van hien trong bang nhung mo di va khong chon duoc. */
  excluded: string[];
  onPick: (ids: string[]) => void;
  onClose: () => void;
}) {
  const { user } = useAuth();
  const data = useLoad(() => Promise.all([api.problems(), api.topics().catch(() => [])]));
  const [search, setSearch] = useState("");
  const [source, setSource] = useState("");          // "" = moi kho, "shared", "mine"
  const [topic, setTopic] = useState("");            // "" = moi chu de, ma chu de, hoac OTHER_TOPIC
  const [difficulty, setDifficulty] = useState(-1);  // -1 = moi do kho
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const topics = data.data?.[1] ?? [];
  const known = useMemo(() => new Set(topics.map((t) => t.id)), [topics]);
  const has = useMemo(() => new Set(excluded), [excluded]);

  // Bai kho rieng cua giang vien khac (thay duoc vi cung day mot lop) khong dua vao lop khac duoc.
  const usable = useMemo(() => {
    const all = (data.data?.[0] ?? []).filter((p) => !p.owner || p.owner === user?.id);
    return orderByPath(all, topics);
  }, [data.data, topics, user]);
  const hasMine = usable.some((p) => !!p.owner);

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    return usable.filter((p) => {
      if (source === "shared" && p.owner) return false;
      if (source === "mine" && !p.owner) return false;
      if (topic && topicKey(p, known) !== topic) return false;
      if (difficulty >= 0 && p.difficulty !== difficulty) return false;
      return !q || p.id.toLowerCase().includes(q) || p.title.toLowerCase().includes(q);
    });
  }, [usable, search, source, topic, difficulty, known]);

  const pickable = shown.filter((p) => !has.has(p.id));
  const allShownPicked = pickable.length > 0 && pickable.every((p) => selected.has(p.id));

  // Cap nhat theo gia tri moi nhat (ham trong setState): hai lan bam lien nhau khong de mat lan truoc.
  function toggle(id: string) {
    setSelected((old) => {
      const next = new Set(old);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  /** O tich o dau bang: chon / bo chon moi bai dang hien (theo bo loc hien tai). */
  function toggleShown() {
    setSelected((old) => {
      const next = new Set(old);
      for (const p of pickable) {
        if (allShownPicked) next.delete(p.id);
        else next.add(p.id);
      }
      return next;
    });
  }

  const topicName = (p: Problem) => topics.find((t) => t.id === p.topic)?.name ?? "Bài khác";
  // Chu de chia theo lo trinh (cot "group" cua topics.txt) de danh sach chon de tim.
  const groups = [...new Set(topics.map((t) => t.group))];

  return (
    <Modal title={title} onClose={onClose} width={900}>
      <ErrorBox text={data.error} />
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input autoFocus style={{ ...INPUT, flex: 1, minWidth: 200 }} value={search} onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm theo mã hoặc tên bài" />
        {hasMine && (
          <select style={{ ...INPUT, width: 150 }} value={source} onChange={(e) => setSource(e.target.value)}>
            <option value="">Mọi kho bài</option>
            <option value="shared">Kho chung</option>
            <option value="mine">Kho của tôi</option>
          </select>
        )}
        <select style={{ ...INPUT, width: 240 }} value={topic} onChange={(e) => setTopic(e.target.value)}>
          <option value="">Mọi chủ đề</option>
          {groups.map((g) => (
            <optgroup key={g} label={g || "Chủ đề"}>
              {topics.filter((t) => t.group === g).map((t) => <option key={t.id} value={t.id}>{t.order}. {t.name}</option>)}
            </optgroup>
          ))}
          <option value={OTHER_TOPIC}>Bài khác (chưa phân loại)</option>
        </select>
        <select style={{ ...INPUT, width: 140 }} value={difficulty} onChange={(e) => setDifficulty(Number(e.target.value))}>
          <option value={-1}>Mọi độ khó</option>
          {[1, 2, 3].map((d) => <option key={d} value={d}>{DIFFICULTY_META[d].label}</option>)}
          <option value={0}>Chưa đánh giá</option>
        </select>
      </div>

      <div style={{ overflow: "auto", maxHeight: "52vh", border: "1px solid #E0E0E0", borderRadius: 8 }}>
        <table style={{ width: "100%", borderCollapse: "collapse", background: "#fff" }}>
          <thead>
            <tr>
              <th style={{ ...HEAD, width: 36 }}>
                <input type="checkbox" checked={allShownPicked} disabled={pickable.length === 0} onChange={toggleShown}
                  title="Chọn / bỏ chọn mọi bài đang hiện" />
              </th>
              {["Mã", "Tên bài", "Chủ đề", "Độ khó", ""].map((h, i) => <th key={i} style={HEAD}>{h}</th>)}
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 && (
              <tr><td colSpan={6} style={{ ...CELL, textAlign: "center", color: "#9E9E9E", padding: 28 }}>
                {data.data ? "Không có bài nào khớp." : "Đang tải danh sách bài..."}
              </td></tr>
            )}
            {shown.map((p) => {
              const already = has.has(p.id);
              const picked = selected.has(p.id);
              const diff = DIFFICULTY_META[p.difficulty];
              return (
                <tr key={p.id} onClick={already ? undefined : () => toggle(p.id)} style={{
                  cursor: already ? "default" : "pointer", opacity: already ? 0.5 : 1,
                  background: picked ? "var(--color-red-light)" : undefined,
                }}>
                  <td style={CELL}><input type="checkbox" checked={picked} disabled={already} readOnly /></td>
                  <td style={{ ...CELL, fontFamily: "var(--font-mono)", fontWeight: 700, whiteSpace: "nowrap" }}>{p.id}</td>
                  <td style={CELL}>{p.title}</td>
                  <td style={{ ...CELL, whiteSpace: "nowrap" }}>{topicName(p)}</td>
                  <td style={CELL}>
                    {diff
                      ? <span style={{ fontSize: 11, fontWeight: 700, padding: "1px 8px", borderRadius: 10, color: diff.color, background: diff.bg, whiteSpace: "nowrap" }}>{diff.label}</span>
                      : "—"}
                  </td>
                  <td style={{ ...CELL, whiteSpace: "nowrap" }}>
                    {already ? <Tag color="gray">Đã có</Tag> : p.owner ? <Tag color="purple">Kho của tôi</Tag> : ""}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div style={{ display: "flex", gap: 10, alignItems: "center", justifyContent: "flex-end", flexWrap: "wrap" }}>
        <span style={{ flex: 1, fontSize: 12.5, color: "#757575" }}>
          {shown.length} bài đang hiện · đã chọn <b style={{ color: "#212121" }}>{selected.size}</b>
        </span>
        {selected.size > 0 && <Btn kind="ghost" onClick={() => setSelected(new Set())}>Bỏ chọn hết</Btn>}
        <Btn disabled={selected.size === 0} onClick={() => onPick([...selected])}>Thêm {selected.size || ""} bài</Btn>
      </div>
    </Modal>
  );
}
