import { useState } from "react";
import { Btn } from "../../shared/kit";
import ProblemPicker from "./ProblemPicker";

/**
 * O "cac bai" cua mot form (tao ky thi, giao bai tap): bai duoc CHON TU BANG (ProblemPicker), khong go
 * ma bai bang tay. Cac bai da chon hien thanh nhan, bam dau x de bo tung bai.
 */
export default function ProblemField({ label, pickerTitle, ids, onChange }: {
  label: string;
  /** Tieu de cua bang chon bai. */
  pickerTitle: string;
  ids: string[];
  onChange: (ids: string[]) => void;
}) {
  const [picking, setPicking] = useState(false);
  return (
    // Khong dung <label> nhu Field: ben trong co nhieu nut, bam vao chu se kich nham nut dau tien.
    <div style={{ display: "flex", flexDirection: "column", gap: 4, fontSize: 12, fontWeight: 600, color: "#616161", flex: 1, minWidth: 220 }}>
      {label}
      <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap", minHeight: 36 }}>
        {ids.map((id) => (
          <span key={id} style={{
            display: "inline-flex", alignItems: "center", gap: 2, height: 26, padding: "0 4px 0 10px", borderRadius: 13,
            border: "1px solid #E0E0E0", background: "#FAFAFA", color: "var(--color-red)",
            fontFamily: "var(--font-mono)", fontSize: 11.5, fontWeight: 700,
          }}>
            {id}
            <button type="button" title={`Bỏ bài ${id}`} aria-label={`Bỏ bài ${id}`} onClick={() => onChange(ids.filter((x) => x !== id))}
              style={{ border: "none", background: "none", color: "#9E9E9E", cursor: "pointer", fontSize: 14, lineHeight: 1, padding: "0 4px" }}>×</button>
          </span>
        ))}
        <Btn small kind="ghost" onClick={() => setPicking(true)}>{ids.length > 0 ? "Chọn thêm" : "Chọn từ danh sách"}</Btn>
      </div>
      {picking && (
        <ProblemPicker title={pickerTitle} excluded={ids}
          onClose={() => setPicking(false)}
          onPick={(more) => { setPicking(false); onChange([...ids, ...more.filter((id) => !ids.includes(id))]); }} />
      )}
    </div>
  );
}
