import { useEffect, useRef } from "react";
import { Compartment, EditorState, type Extension } from "@codemirror/state";
import { EditorView, keymap, placeholder as cmPlaceholder } from "@codemirror/view";
import { indentWithTab } from "@codemirror/commands";
import { HighlightStyle, indentUnit, syntaxHighlighting } from "@codemirror/language";
import { basicSetup } from "codemirror";
import { tags as t } from "@lezer/highlight";

/**
 * O soan ma nguon dung CodeMirror 6: co so dong, to mau cu phap, tu thut dong, dong ngoac,
 * phim Tab thut dong thay vi nhay sang o khac. Bang mau lay theo giao dien toi mac dinh cua
 * Visual Studio Code (Dark+) de nguoi dung thay quen mat.
 *
 * Che do readOnly dung lai dung component nay de hien ma nguon da nop (khong sua duoc).
 */

/**
 * Moi ngon ngu mot bo phan tich cu phap, nap bang import() dong: Vite tach moi ngon ngu thanh
 * mot file rieng va trinh duyet chi tai ngon ngu dang duoc chon. Ten khoa khop voi ten ngon
 * ngu trong LanguageRegistry ben Java.
 */
const LANGUAGE_MODES: Record<string, () => Promise<Extension>> = {
  Java: () => import("@codemirror/lang-java").then((m) => m.java()),
  "C++": () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
  Python: () => import("@codemirror/lang-python").then((m) => m.python()),
  JavaScript: () => import("@codemirror/lang-javascript").then((m) => m.javascript()),
};

/** Nen, con tro, vung chon, cot so dong - theo Dark+ cua VS Code. */
const editorTheme = EditorView.theme(
  {
    "&": {
      backgroundColor: "#1E1E1E",
      color: "#D4D4D4",
      fontSize: "13px",
      border: "1px solid #333333",
      borderRadius: "6px",
      overflow: "hidden",
    },
    "&.cm-focused": { outline: "none", borderColor: "var(--color-accent)" },
    ".cm-content": { fontFamily: "var(--font-mono)", padding: "10px 0", caretColor: "#AEAFAD" },
    ".cm-scroller": { fontFamily: "var(--font-mono)", lineHeight: "1.6", overflow: "auto" },
    ".cm-gutters": {
      backgroundColor: "#1E1E1E",
      color: "#858585",
      border: "none",
      paddingRight: "4px",
    },
    ".cm-activeLineGutter": { backgroundColor: "transparent", color: "#C6C6C6" },
    ".cm-activeLine": { backgroundColor: "rgba(255, 255, 255, 0.04)" },
    ".cm-cursor, .cm-dropCursor": { borderLeftColor: "#AEAFAD" },
    "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection": {
      backgroundColor: "#264F78",
    },
    ".cm-matchingBracket, &.cm-focused .cm-matchingBracket": {
      backgroundColor: "rgba(255, 255, 255, 0.1)",
      outline: "1px solid #888888",
    },
    ".cm-placeholder": { color: "#6B6B6B" },
    ".cm-tooltip": { backgroundColor: "#252526", border: "1px solid #454545", color: "#D4D4D4" },
    ".cm-tooltip-autocomplete ul li[aria-selected]": { backgroundColor: "#04395E", color: "#FFFFFF" },
    ".cm-foldPlaceholder": { backgroundColor: "#3A3D41", border: "none", color: "#D4D4D4" },
  },
  { dark: true },
);

/** Mau to cu phap theo Dark+ cua VS Code. */
const highlightStyle = HighlightStyle.define([
  { tag: [t.controlKeyword, t.moduleKeyword, t.operatorKeyword], color: "#C586C0" },
  { tag: [t.keyword, t.modifier, t.definitionKeyword, t.self, t.bool, t.null, t.atom], color: "#569CD6" },
  { tag: [t.string, t.special(t.string), t.character], color: "#CE9178" },
  { tag: [t.escape, t.regexp], color: "#D7BA7D" },
  { tag: [t.number], color: "#B5CEA8" },
  { tag: [t.comment, t.lineComment, t.blockComment, t.docComment], color: "#6A9955" },
  { tag: [t.function(t.variableName), t.function(t.propertyName), t.macroName], color: "#DCDCAA" },
  { tag: [t.typeName, t.className, t.namespace, t.standard(t.typeName)], color: "#4EC9B0" },
  { tag: [t.variableName, t.propertyName, t.labelName], color: "#9CDCFE" },
  { tag: [t.definition(t.variableName)], color: "#9CDCFE" },
  { tag: [t.constant(t.variableName)], color: "#4FC1FF" },
  { tag: [t.operator, t.punctuation, t.separator, t.bracket], color: "#D4D4D4" },
  { tag: [t.meta, t.annotation, t.processingInstruction], color: "#C586C0" },
  { tag: t.invalid, color: "#F44747" },
]);

export default function CodeEditor({
  value,
  language,
  onChange,
  placeholder = "Dán mã nguồn của bạn vào đây...",
  minHeight = 360,
  maxHeight,
  readOnly = false,
}: {
  value: string;
  /** Ten ngon ngu (Java, C++, Python, JavaScript) de to mau; bo trong thi khong to mau. */
  language?: string;
  /** Bo qua khi readOnly: noi dung khong doi duoc nen khong bao gio duoc goi. */
  onChange?: (value: string) => void;
  placeholder?: string;
  minHeight?: number;
  /** Co thi khung chi cao toi day, ben trong tu cuon - dung cho che do xem lai. */
  maxHeight?: number;
  readOnly?: boolean;
}) {
  const host = useRef<HTMLDivElement | null>(null);
  const view = useRef<EditorView | null>(null);
  /** Cho phep thay rieng bo phan tich cu phap ma khong dung lai ca editor. */
  const languageSlot = useRef(new Compartment());
  /** Giu onChange trong ref de editor khong bi tao lai (mat vi tri con tro) moi lan component ve lai. */
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // Tao editor dung mot lan; value va language duoc dong bo o hai effect ben duoi.
  useEffect(() => {
    if (!host.current) return;
    const instance = new EditorView({
      state: EditorState.create({
        doc: value,
        extensions: [
          basicSetup,
          keymap.of([indentWithTab]),
          indentUnit.of("    "),
          EditorState.tabSize.of(4),
          editorTheme,
          syntaxHighlighting(highlightStyle),
          cmPlaceholder(placeholder),
          EditorView.theme({
            ".cm-scroller": {
              // Che do xem lai: khung chi cao vua noi dung, khong ep chieu cao toi thieu.
              ...(readOnly ? {} : { minHeight: `${minHeight}px` }),
              ...(maxHeight !== undefined ? { maxHeight: `${maxHeight}px` } : {}),
            },
          }),
          ...(readOnly ? [EditorState.readOnly.of(true), EditorView.editable.of(false)] : []),
          languageSlot.current.of([]),
          EditorView.updateListener.of((update) => {
            if (update.docChanged) onChangeRef.current?.(update.state.doc.toString());
          }),
        ],
      }),
      parent: host.current,
    });
    view.current = instance;
    return () => {
      instance.destroy();
      view.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Doi ngon ngu: nap bo phan tich cu phap roi thay dung phan do trong cau hinh.
  useEffect(() => {
    let cancelled = false;
    const load = language ? LANGUAGE_MODES[language] : undefined;
    if (!load) {
      view.current?.dispatch({ effects: languageSlot.current.reconfigure([]) });
      return;
    }
    load()
      .then((extension) => {
        if (cancelled || !view.current) return;
        view.current.dispatch({ effects: languageSlot.current.reconfigure(extension) });
      })
      .catch(() => {
        // Khong tai duoc thi o code khong to mau - van go va nop binh thuong.
      });
    return () => { cancelled = true; };
  }, [language]);

  // Dong bo tu ngoai vao (vi du bam "Chen code mau"). Chi ghi de khi noi dung that su khac,
  // neu khong moi lan go phim con tro se nhay ve dau.
  useEffect(() => {
    const instance = view.current;
    if (!instance) return;
    const current = instance.state.doc.toString();
    if (current === value) return;
    instance.dispatch({ changes: { from: 0, to: current.length, insert: value } });
  }, [value]);

  return <div ref={host} style={{ minWidth: 0 }} />;
}
