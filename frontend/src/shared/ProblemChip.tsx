import { useNav } from "./nav";

/** Nhan nho hien ma bai; bam vao thi mo trang lam bai. done = true thi to xanh kem dau tich. */
export default function ProblemChip({ id, title, done }: { id: string; title?: string; done?: boolean }) {
  const { openProblem } = useNav();
  return (
    <button onClick={() => openProblem(id)} title={title ? `${id} — ${title}` : id} style={{
      display: "inline-flex", alignItems: "center", gap: 4, height: 24, padding: "0 9px", borderRadius: 12,
      border: `1px solid ${done ? "#A5D6A7" : "#E0E0E0"}`, background: done ? "#E8F5E9" : "#FAFAFA",
      color: done ? "#2E7D32" : "var(--color-red)", fontFamily: "var(--font-mono)", fontSize: 11.5,
      fontWeight: 700, cursor: "pointer",
    }}>
      {done && "✓"} {id}
    </button>
  );
}
