import { useState } from "react";
import { papi } from "../../shared/platformApi";
import { Btn, Card, ErrorBox, Page, useLoad } from "../../shared/kit";
import ChatBox from "./ChatBox";

/**
 * Module 30 (chat va trao doi truc tuyen): kenh chung cua ca he thong va kenh rieng
 * cua tung lop minh tham gia. May chu kiem tra quyen vao kenh lop o moi request.
 */
export default function ChatPage() {
  const classes = useLoad(() => papi.classrooms());
  const [channel, setChannel] = useState("general");
  const channels = [
    { key: "general", label: "Kênh chung" },
    ...(classes.data ?? []).map((c) => ({ key: `class-${c.id}`, label: c.name })),
  ];
  const current = channels.find((c) => c.key === channel) ?? channels[0];

  return (
    <Page title="Chat">
      <ErrorBox text={classes.error} />
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {channels.map((c) => (
          <Btn key={c.key} small kind={c.key === current.key ? "primary" : "ghost"} onClick={() => setChannel(c.key)}>{c.label}</Btn>
        ))}
      </div>
      <Card title={current.label}>
        <ChatBox channel={current.key} height={420} />
      </Card>
    </Page>
  );
}
