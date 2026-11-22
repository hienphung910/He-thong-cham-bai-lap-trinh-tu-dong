import { useEffect, useRef, useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, INPUT, Page, SimNote, Tag, errorText } from "../../shared/kit";
import CodeEditor from "../judge/CodeEditor";
import ChatBox from "../community/ChatBox";
import Whiteboard from "./Whiteboard";
import type { LanguageInfo } from "../../shared/types";
import type { RoomState, RunResult, Stroke } from "../../shared/platformTypes";

const POLL_MS = 1500;

/**
 * Phong hoc truc tuyen (Module 14) gom: live coding (17), cung lap trinh (18),
 * bang trang va gio tay (19), camera / micro (16), chat cua phong (30).
 *
 * Ban mo phong: khong co ket noi realtime. Cu 1.5 giay trinh duyet hoi may chu
 * trang thai phong mot lan (ai dang co mat, ma nguon, net ve moi) roi ve lai.
 */
export default function Room({ meetingId, languages, onLeave }: {
  meetingId: string; languages: LanguageInfo[]; onLeave: () => void;
}) {
  const [room, setRoom] = useState<RoomState | null>(null);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"code" | "board">("code");

  // ---- ma nguon dung chung ----
  const [code, setCode] = useState("");
  const [language, setLanguage] = useState("Java");
  const versionRef = useRef(-1);      // phien ban ma nguon cua may chu ma minh dang hien
  const editSeqRef = useRef(0);       // tang 1 moi lan minh go phim
  const dirtyRef = useRef(false);     // minh co thay doi chua gui len may chu
  const sendTimerRef = useRef<number | undefined>(undefined);
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [running, setRunning] = useState(false);

  // ---- bang trang ----
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const knownStrokesRef = useRef(0);  // so net da nhan tu may chu

  // Hoi trang thai phong lien tuc cho den khi roi phong.
  useEffect(() => {
    let alive = true;
    async function poll() {
      try {
        const state = await papi.room(meetingId, knownStrokesRef.current);
        if (!alive) return;
        setRoom(state);
        setError("");

        // Ma nguon: chi lay ban cua may chu khi minh khong co thay doi nao dang cho gui.
        if (state.codeVersion !== versionRef.current && !dirtyRef.current) {
          versionRef.current = state.codeVersion;
          setCode(state.code);
          setLanguage(state.language);
        }

        // Bang trang: may chu chi gui cac net moi ke tu vi tri strokesFrom.
        if (state.strokesFrom < knownStrokesRef.current) setStrokes(state.strokes);          // bang da bi xoa
        else if (state.strokes.length > 0) setStrokes((old) => [...old, ...state.strokes]);
        knownStrokesRef.current = state.strokeCount;
      } catch (e) {
        if (alive) setError(errorText(e));
      }
    }
    void poll();
    const timer = setInterval(() => void poll(), POLL_MS);
    return () => { alive = false; clearInterval(timer); window.clearTimeout(sendTimerRef.current); };
  }, [meetingId]);

  /** Go ma: hien ngay o may minh, doi ngung go 400 ms roi moi gui len may chu. */
  function editCode(nextCode: string, nextLanguage: string) {
    setCode(nextCode);
    setLanguage(nextLanguage);
    dirtyRef.current = true;
    const seq = ++editSeqRef.current;
    window.clearTimeout(sendTimerRef.current);
    sendTimerRef.current = window.setTimeout(async () => {
      try {
        await papi.roomCode(meetingId, nextCode, nextLanguage);
        // Trong luc gui minh khong go them gi -> coi nhu da dong bo, lan hoi sau se nhan lai ban cua may chu.
        if (editSeqRef.current === seq) dirtyRef.current = false;
      } catch (e) {
        dirtyRef.current = false;
        setError(errorText(e));
      }
    }, 400);
  }

  async function act(work: () => Promise<unknown>) {
    try { await work(); } catch (e) { setError(errorText(e)); }
  }

  async function runCode() {
    setRunning(true);
    setRunResult(null);
    try { setRunResult(await papi.run({ language, code, input: "" })); }
    catch (e) { setError(errorText(e)); }
    finally { setRunning(false); }
  }

  if (!room) {
    return (
      <Page title="Phòng học" actions={<Btn kind="ghost" onClick={onLeave}>← Rời phòng</Btn>}>
        <ErrorBox text={error} />
        {!error && <span style={{ fontSize: 13, color: "#9E9E9E" }}>Đang vào phòng...</span>}
      </Page>
    );
  }

  return (
    <Page title={room.meeting.title} subtitle={`${room.meeting.classroomName} · Chủ phòng: ${room.meeting.hostName}`}
      actions={<>
        <Btn kind={room.handRaised ? "primary" : "ghost"} onClick={() => void act(() => papi.roomHand(meetingId))}>
          {room.handRaised ? "✋ Hạ tay" : "✋ Giơ tay"}
        </Btn>
        <Btn kind="ghost" onClick={onLeave}>← Rời phòng</Btn>
      </>}>
      <SimNote>
        Phòng học đồng bộ bằng cách hỏi máy chủ mỗi {POLL_MS / 1000} giây (polling), không phải kết nối thời gian thực.
        Camera chỉ hiện hình của chính bạn, không truyền tới người khác.
      </SimNote>
      <ErrorBox text={error} />

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        {/* ---- cot trai: ma nguon / bang trang ---- */}
        <div style={{ flex: "3 1 520px", minWidth: 0, display: "flex", flexDirection: "column", gap: 16 }}>
          <Card right={<>
            <Btn small kind={tab === "code" ? "primary" : "ghost"} onClick={() => setTab("code")}>Mã nguồn</Btn>
            <Btn small kind={tab === "board" ? "primary" : "ghost"} onClick={() => setTab("board")}>Bảng trắng</Btn>
          </>} title={tab === "code" ? (room.collaborative ? "Cùng lập trình" : "Live coding") : "Bảng trắng"}>
            {tab === "code" ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                  <select style={{ ...INPUT, width: 140 }} value={language} disabled={!room.canEditCode}
                    onChange={(e) => editCode(code, e.target.value)}>
                    {languages.map((l) => <option key={l.name} value={l.name}>{l.name}</option>)}
                  </select>
                  <Tag color={room.canEditCode ? "green" : "gray"}>{room.canEditCode ? "Bạn được sửa" : "Chỉ xem"}</Tag>
                  {room.isHost && (
                    <Btn small kind="ghost" onClick={() => void act(() => papi.roomCollab(meetingId, !room.collaborative))}>
                      {room.collaborative ? "Tắt cùng lập trình" : "Cho cả lớp cùng sửa"}
                    </Btn>
                  )}
                  <span style={{ flex: 1 }} />
                  <Btn small disabled={running || !code.trim()} onClick={() => void runCode()}>{running ? "Đang chạy..." : "▶ Chạy thử"}</Btn>
                </div>
                <CodeEditor value={code} readOnly={!room.canEditCode} minHeight={300} maxHeight={420}
                  onChange={(v) => editCode(v, language)} placeholder="Chủ phòng gõ mã ở đây, cả lớp sẽ thấy..." />
                {runResult && (
                  <pre style={{ margin: 0, padding: "8px 12px", background: "#FAFAFA", border: "1px solid #E0E0E0", borderRadius: 6, fontFamily: "var(--font-mono)", fontSize: 12, whiteSpace: "pre-wrap", maxHeight: 160, overflow: "auto" }}>
                    {runResult.compileError || `${runResult.stdout ?? ""}${runResult.stderr ?? ""}` || "(không có output)"}
                  </pre>
                )}
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                <Whiteboard strokes={strokes} onDraw={(s) => void act(() => papi.roomStroke(meetingId, s.color, s.points))} />
                {room.meeting.canManage && (
                  <div><Btn small kind="danger" onClick={() => void act(() => papi.roomClearBoard(meetingId))}>Xoá bảng</Btn></div>
                )}
              </div>
            )}
          </Card>
        </div>

        {/* ---- cot phai: nguoi tham gia, camera, chat ---- */}
        <div style={{ flex: "1 1 280px", minWidth: 260, display: "flex", flexDirection: "column", gap: 16 }}>
          <Card title={`Đang có mặt (${room.participants.length})`}>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {room.participants.map((p) => (
                <div key={p.id} style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#43A047" }} />
                  <b>{p.fullName}</b>
                  {p.host && <Tag color="red">Chủ phòng</Tag>}
                  {p.handRaised && <Tag color="orange">✋ giơ tay</Tag>}
                </div>
              ))}
            </div>
          </Card>
          <Card title="Camera và micro"><Camera /></Card>
          <Card title="Chat của phòng"><ChatBox channel={`meeting-${meetingId}`} height={220} /></Card>
        </div>
      </div>
    </Page>
  );
}

/**
 * Module 16 (video / audio) - mo phong: bat camera va micro cua chinh may nay bang
 * getUserMedia de xem truoc. Khong co phan truyen hinh / tieng toi nguoi khac.
 */
function Camera() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [micOn, setMicOn] = useState(true);
  const [error, setError] = useState("");

  // Roi phong (hoac tat camera) thi tra lai camera va micro cho he dieu hanh.
  useEffect(() => () => { stream?.getTracks().forEach((t) => t.stop()); }, [stream]);

  async function turnOn() {
    setError("");
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      setStream(s);
      setMicOn(true);
      if (videoRef.current) videoRef.current.srcObject = s;
    } catch {
      setError("Không bật được camera / micro (chưa cấp quyền hoặc máy không có thiết bị).");
    }
  }

  function toggleMic() {
    stream?.getAudioTracks().forEach((t) => { t.enabled = !micOn; });
    setMicOn(!micOn);
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {/* muted: khong phat lai tieng cua chinh minh ra loa */}
      <video ref={videoRef} autoPlay muted playsInline style={{ width: "100%", aspectRatio: "4 / 3", background: "#212121", borderRadius: 6, display: stream ? "block" : "none" }} />
      {!stream && <div style={{ fontSize: 12.5, color: "#9E9E9E" }}>Camera đang tắt.</div>}
      <ErrorBox text={error} />
      <div style={{ display: "flex", gap: 8 }}>
        {stream
          ? <>
              <Btn small kind="ghost" onClick={() => setStream(null)}>Tắt camera</Btn>
              <Btn small kind="ghost" onClick={toggleMic}>{micOn ? "Tắt micro" : "Bật micro"}</Btn>
            </>
          : <Btn small onClick={() => void turnOn()}>Bật camera</Btn>}
      </div>
    </div>
  );
}
