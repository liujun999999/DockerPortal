<template>
  <div ref="host" class="code-editor" :style="{ height }"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap, placeholder as cmPlaceholder } from '@codemirror/view'
import { HighlightStyle, indentUnit, syntaxHighlighting } from '@codemirror/language'
import { indentWithTab } from '@codemirror/commands'
import { yaml } from '@codemirror/lang-yaml'
import { tags as t } from '@lezer/highlight'

/**
 * 带行号与语法着色的代码编辑器，用于 compose / 配置文件这类多行文本。
 * 替代 el-input textarea：纯文本框既没有行号，也无法区分 key 与 value，
 * 排查几十行 compose 时只能靠肉眼数列。
 */
const props = withDefaults(defineProps<{
  modelValue?: string
  language?: 'yaml' | 'text'
  height?: string
  placeholder?: string
  readonly?: boolean
}>(), {
  modelValue: '',
  language: 'text',
  height: '320px',
  placeholder: '',
  readonly: false,
})

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const host = ref<HTMLElement>()
let view: EditorView | null = null

// basicSetup 自带的默认配色是 fallback 样式，这里注册的显式配色优先级更高
const yamlHighlight = HighlightStyle.define([
  { tag: [t.propertyName, t.definition(t.propertyName)], color: '#2b6cb0' },      // key:
  { tag: [t.string, t.special(t.string)], color: '#2f855a' },                     // "quoted value"
  { tag: t.content, color: '#8a5a00' },                                           // 裸值
  { tag: t.lineComment, color: '#a0a2a7', fontStyle: 'italic' },
  { tag: [t.separator, t.punctuation, t.brace, t.squareBracket], color: '#909399' }, // : - , { }
  { tag: [t.keyword, t.typeName, t.labelName, t.attributeValue], color: '#6b46c1' }, // 指令/锚点/标签
  { tag: t.meta, color: '#b6b9bf' },                                              // --- 文档分隔线
])

const editorTheme = EditorView.theme({
  '&': {
    height: '100%',
    fontSize: '13px',
    color: '#303133',
    backgroundColor: '#fff',
    border: '1px solid #dcdfe6',
    borderRadius: '4px',
    overflow: 'hidden',
  },
  '&.cm-focused': { outline: 'none', borderColor: '#409eff' },
  '.cm-scroller': {
    fontFamily: 'Menlo, Monaco, Consolas, monospace',
    lineHeight: '1.6',
    overflow: 'auto',
  },
  '.cm-content': { caretColor: '#409eff', padding: '4px 0' },
  '.cm-gutters': {
    backgroundColor: '#f7f8fa',
    color: '#b1b3b8',
    border: 'none',
    borderRight: '1px solid #ebeef5',
  },
  '.cm-lineNumbers .cm-gutterElement': { padding: '0 6px 0 10px', minWidth: '30px' },
  '.cm-activeLine': { backgroundColor: '#f2f6fc' },
  '.cm-activeLineGutter': { backgroundColor: '#e9eef7', color: '#606266' },
  '.cm-placeholder': { color: '#a8abb2' },
})

function buildExtensions() {
  const ext = [
    basicSetup,
    editorTheme,
    syntaxHighlighting(yamlHighlight),
    EditorView.lineWrapping,
    EditorState.tabSize.of(2),
    indentUnit.of('  '),
    // 默认 Tab 是插入制表符，YAML 用空格更稳妥；同时让 Tab 不会跳出编辑器
    keymap.of([indentWithTab]),
    EditorView.updateListener.of((u) => {
      if (u.docChanged) emit('update:modelValue', u.state.doc.toString())
    }),
  ]
  if (props.language === 'yaml') ext.push(yaml())
  if (props.placeholder) ext.push(cmPlaceholder(props.placeholder))
  if (props.readonly) ext.push(EditorState.readOnly.of(true), EditorView.editable.of(false))
  return ext
}

onMounted(() => {
  // 传了 state 时 EditorView 会忽略 extensions 配置项，扩展必须自己挂到 state 上
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({ doc: props.modelValue || '', extensions: buildExtensions() }),
  })
})

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})

// 外部整体替换内容（例如载入模板）时同步进文档；值相同则跳过，避免打断光标
watch(() => props.modelValue, (val) => {
  if (!view) return
  const text = val || ''
  if (view.state.doc.toString() === text) return
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } })
})

// 语言与只读是扩展集的一部分，变化时重建 state
watch(() => [props.language, props.readonly] as const, () => {
  view?.setState(EditorState.create({ doc: props.modelValue || '', extensions: buildExtensions() }))
})
</script>

<style scoped>
.code-editor {
  width: 100%;
}
</style>
