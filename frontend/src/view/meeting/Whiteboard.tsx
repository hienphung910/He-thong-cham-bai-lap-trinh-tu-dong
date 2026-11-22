import { useEffect, useRef, useState } from "react";
import type { Stroke } from "../../shared/platformTypes";

const WIDTH = 760;
const HEIGHT = 400;
const COLORS = ["#212121", "#C62828", "#1565C0", "#2E7D32", "#F57F17"];

/** Ve mot net (mau + day diem "x,y x,y ...") len canvas. */
function drawStroke(ctx: CanvasRenderingContext2D, stroke: Stroke) {
  const points = stroke.points.trim().split(" ").map((p) => p.split(",").map(Number));
  if (points.length === 0) return;
  ctx.strokeStyle = stroke.color;
  ctx.lineWidth = 2.5;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.beginPath();
  ctx.moveTo(points[0][0], points[0][1]);
  for (const [x, y] of points.slice(1)) ctx.lineTo(x, y);
  // Net chi co mot diem (bam ma khong keo) thi ve mot cham.
  if (points.length === 1) ctx.lineTo(points[0][0] + 0.1, points[0][1] + 0.1);
  ctx.stroke();
}

/**
 * Bang trang (Module 19). Thanh phan nay chi lo viec ve:
 *  - strokes: toan bo net ve hien co (do trang cha lay tu may chu), ve lai moi khi doi;
 *  - onDraw : goi khi nguoi dung ve xong mot net, de trang cha gui len may chu.
 * Khong truyen onDraw thi bang chi de xem (dung cho ban ghi).
 */
export default function Whiteboard({ strokes, onDraw }: { strokes: Stroke[]; onDraw?: (stroke: Stroke) => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const currentRef = useRef<string[] | null>(null);    // cac diem cua net dang ve do
  const [color, setColor] = useState(COLORS[0]);

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    ctx.clearRect(0, 0, WIDTH, HEIGHT);
    for (const s of strokes) drawStroke(ctx, s);
    // Dang ve do ma bang duoc ve lai (co net moi cua nguoi khac) thi ve lai ca net dang do.
    if (currentRef.current) drawStroke(ctx, { color, points: currentRef.current.join(" ") });
  }, [strokes, color]);

  /** Doi toa do chuot tren man hinh thanh toa do tren canvas (canvas co the bi co gian). */
  function pointOf(e: React.MouseEvent<HTMLCanvasElement>): [number, number] {
    const rect = e.currentTarget.getBoundingClientRect();
    return [
      Math.round(((e.clientX - rect.left) / rect.width) * WIDTH),
      Math.round(((e.clientY - rect.top) / rect.height) * HEIGHT),
    ];
  }

  function begin(e: React.MouseEvent<HTMLCanvasElement>) {
    if (!onDraw) return;
    currentRef.current = [pointOf(e).join(",")];
  }

  function move(e: React.MouseEvent<HTMLCanvasElement>) {
    const current = currentRef.current;
    const ctx = canvasRef.current?.getContext("2d");
    if (!current || !ctx) return;
    current.push(pointOf(e).join(","));
    drawStroke(ctx, { color, points: current.slice(-2).join(" ") });   // ve ngay doan vua keo
  }

  function end() {
    const current = currentRef.current;
    currentRef.current = null;
    if (current && onDraw) onDraw({ color, points: current.join(" ") });
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {onDraw && (
        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
          <span style={{ fontSize: 12, color: "#757575" }}>Màu bút:</span>
          {COLORS.map((c) => (
            <button key={c} onClick={() => setColor(c)} title={c} style={{
              width: 22, height: 22, borderRadius: "50%", background: c, cursor: "pointer",
              border: color === c ? "3px solid #BDBDBD" : "3px solid #fff", boxShadow: "0 0 0 1px #E0E0E0",
            }} />
          ))}
        </div>
      )}
      <canvas ref={canvasRef} width={WIDTH} height={HEIGHT}
        onMouseDown={begin} onMouseMove={move} onMouseUp={end} onMouseLeave={end}
        style={{ width: "100%", maxWidth: WIDTH, aspectRatio: `${WIDTH} / ${HEIGHT}`, background: "#fff", border: "1px solid #E0E0E0", borderRadius: 6, cursor: onDraw ? "crosshair" : "default" }} />
    </div>
  );
}
