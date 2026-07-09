// Juml 汎用バグハント・ワークフロー（参照テンプレート）
//
// 【推奨: インライン方式】この環境では Workflow({name,args}) の args が届かないことが
// あるため、本ファイルの本体 (スキーマ〜return) をコピーし、先頭の `a`(=args) 依存部を
// 実値の定数 (TARGET / FILES / LENSES) に置き換えて Workflow({ script }) で起動するのが
// 確実。手順は orchestrate スキル参照。
//
// 【args 方式 (args が届く環境のみ)】:
//   Workflow({ name: "bug-hunt", args: {
//     target: "UML エディタ (PlantUML 編集タブ)",        // 必須: 対象の説明
//     files: ["src/main/java/juml/app/uml/PumlSourcePanel.java"], // 必須: 対象ファイル
//     tests: ["..."], lenses: ["【正確性】...", ...], context: "..." // 任意
//   }})
// 返り値: { confirmed: [...], rest: [...], rejectedCount, rejectedTitles }
//   confirmed = 敵対的検証を生き残った bug。rest = ux / test-gap (検証なし・参考)。
export const meta = {
  name: 'bug-hunt',
  description: '対象コードを観点別レンズで並列調査し、敵対的検証で確定バグだけを返す',
  whenToUse: '「バグゼロにして」「徹底的に調べて」「多角的にレビューして」など、特定領域のバグを網羅的に洗い出したいとき。結果の修正はメインループが行う。',
  phases: [
    { title: 'Find', detail: '観点別ファインダーを並列起動' },
    { title: 'Verify', detail: 'bug 判定を敵対的に検証 (偽陽性排除)' },
  ],
}

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          line: { type: 'integer' },
          title: { type: 'string' },
          detail: { type: 'string' },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          kind: { type: 'string', enum: ['bug', 'ux', 'test-gap'] },
          repro: { type: 'string' },
        },
        required: ['file', 'title', 'detail', 'severity', 'kind', 'repro'],
      },
    },
  },
  required: ['findings'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    isReal: { type: 'boolean' },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
    reason: { type: 'string' },
  },
  required: ['isReal', 'confidence', 'reason'],
}

// args は object で渡るのが基本だが、呼び出し経路によっては JSON 文字列で届くことが
// あるため両対応する (堅牢性)。
let a = args || {}
if (typeof a === 'string') {
  try { a = JSON.parse(a) } catch (e) { a = {} }
}
if (!a.target || !Array.isArray(a.files) || a.files.length === 0) {
  throw new Error('args.target (説明) と args.files (対象ファイル配列) は必須です')
}

const COMMON = `対象リポジトリ: /home/user/Juml (Java Swing アプリ "Juml")。
調査対象: ${a.target}
対象ソース:
${a.files.map(f => '- ' + f).join('\n')}
${Array.isArray(a.tests) && a.tests.length ? '既存テスト:\n' + a.tests.map(f => '- ' + f).join('\n') : ''}
${a.context ? '追加の前提:\n' + a.context : ''}

ルール:
- 必ず Read でコードを読み、行番号つきの根拠を示すこと。推測で報告しない。
- 「実際に誤動作するもの」だけを kind=bug とする。スタイル/好みは報告しない。
- 既にテストで守られている正常動作を「バグ」と誤認しないこと (該当テストを確認)。
- 見つからなければ findings: [] を返してよい。数を稼ぐための水増しは厳禁。
- 最終出力は StructuredOutput ツールで返す。`

// 標準レンズ (args.lenses で置き換え可能)
const DEFAULT_LENSES = [
  '【正確性】境界条件 (off-by-one / null / 空 / 範囲外)、変換・写像ロジックの誤り、例外の握り潰し',
  '【状態・ライフサイクル】フラグの立て漏らし/消し漏らし、開閉・保存・切替時の状態遷移、重複生成、リソースリーク',
  '【スレッディング / EDT】EDT 外からの Swing 操作、SwingWorker の競合・キャンセル、I/O による UI フリーズ、リスナー再入',
  '【リソース・I/O・i18n】ファイル読み書き (文字コード/BOM/改行)、Messages キーの英日欠落、MessageFormat 引数不一致',
  '【使い勝手】機能欠陥に近い UX 問題のみ (導線欠如・ショートカット不動作・フォーカス喪失)。好みは kind=ux, severity=low',
]
const lensList = Array.isArray(a.lenses) && a.lenses.length ? a.lenses : DEFAULT_LENSES

phase('Find')
log(`${lensList.length} レンズのファインダーを並列起動`)
const rounds = await parallel(lensList.map((lens, i) => () =>
  agent(COMMON + `\n\nあなたのレンズ: ${lens}\nこのレンズに該当する問題だけを深く探せ。`,
    { label: `find:${i + 1}`, phase: 'Find', schema: FINDINGS_SCHEMA, effort: 'high' })))

const all = rounds.filter(Boolean).flatMap(r => r.findings || [])
log(`raw findings: ${all.length}`)

const seen = new Map()
for (const f of all) {
  const key = f.file + '::' + f.title.replace(/\s+/g, '').slice(0, 60)
  if (!seen.has(key)) seen.set(key, f)
}
const unique = [...seen.values()]
log(`deduped: ${unique.length}`)

phase('Verify')
const bugs = unique.filter(f => f.kind === 'bug')
const rest = unique.filter(f => f.kind !== 'bug')
const verified = await parallel(bugs.map(f => () =>
  agent(`対象リポジトリ: /home/user/Juml。次のバグ報告を【反証】しろ。コードを Read で読み、
報告が誤読・仕様どおり・既存テストで守られている・実際には到達しない、のいずれかなら isReal=false。
本当に誤動作する具体的シナリオを自分で組み立てられた場合のみ isReal=true。迷ったら false。
--- 報告 ---
file: ${f.file}
line: ${f.line || '?'}
title: ${f.title}
detail: ${f.detail}
repro: ${f.repro}`,
    { label: `verify:${f.title.slice(0, 30)}`, phase: 'Verify', schema: VERDICT_SCHEMA, effort: 'high' })
    .then(v => ({ ...f, verdict: v }))))

const confirmed = verified.filter(Boolean).filter(f => f.verdict && f.verdict.isReal)
const rejected = verified.filter(Boolean).filter(f => f.verdict && !f.verdict.isReal)
log(`bug 確定: ${confirmed.length} / 棄却: ${rejected.length} / ux・test-gap: ${rest.length}`)
return { confirmed, rest, rejectedCount: rejected.length, rejectedTitles: rejected.map(r => r.title) }
