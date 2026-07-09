// Juml レンダリング一括スイープ・ワークフロー
// 実プロジェクト群 × 図種オプション群で Juml.jar の描画を総ざらいし、
// 失敗 (エラー SVG / UML-R エラー / 異常終了) だけを構造化して返す。
//
// 使い方 (メインループから):
//   Workflow({ name: "render-sweep", args: {
//     projects: ["/path/to/sampleA", "/path/to/sampleB"],   // 必須: 入力プロジェクト
//     jar: "build/libs/Juml.jar",                            // 任意: 既定はこの値
//     outBase: "/tmp/.../scratchpad/sweep",                  // 任意: 出力先
//     configs: [{ tag: "col", flags: "-c --color-relations" }] // 任意: 省略時は標準セット
//   }})
// 返り値: { failures: [{project, tag, flags, message}], totalRuns, projectsSwept }
//
// 事前条件: gradle jar 済み (SessionStart フックが jar の鮮度を報告する)。
export const meta = {
  name: 'render-sweep',
  description: 'プロジェクト群 × 図種オプション群で Juml.jar の描画を並列総ざらいし失敗だけ返す',
  whenToUse: '「全 UML を確認して」「描画バグを洗い出して」「サンプルで e2e して」など、レンダリングの回帰を実プロジェクトで面で検証したいとき。',
  phases: [
    { title: 'Sweep', detail: '1 プロジェクト = 1 エージェントで全構成を実行' },
  ],
}

const RESULT_SCHEMA = {
  type: 'object',
  properties: {
    failures: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          tag: { type: 'string' },
          flags: { type: 'string' },
          message: { type: 'string', description: 'stderr の FAILED/UML-R 行など失敗根拠' },
        },
        required: ['tag', 'flags', 'message'],
      },
    },
    runs: { type: 'integer', description: '実行した構成数' },
  },
  required: ['failures', 'runs'],
}

// args は object 基本だが JSON 文字列で届く経路もあるため両対応する (堅牢性)。
let a = args || {}
if (typeof a === 'string') {
  try { a = JSON.parse(a) } catch (e) { a = {} }
}
if (!Array.isArray(a.projects) || a.projects.length === 0) {
  throw new Error('args.projects (プロジェクトディレクトリ配列) は必須です')
}
const jar = a.jar || 'build/libs/Juml.jar'
const outBase = a.outBase || '/tmp/juml-render-sweep'

// 標準の図種オプションセット。過去に実バグを検出した構成 (--color-relations /
// --jetpack / preset) を必ず含める。
const DEFAULT_CONFIGS = [
  { tag: 'all', flags: '-A' },
  { tag: 'class', flags: '-c' },
  { tag: 'col', flags: '-c --color-relations' },
  { tag: 'jet', flags: '-c --jetpack --color-relations --interactive-svg' },
  { tag: 'det', flags: '-c --preset detailed --mark-external-supertypes' },
  { tag: 'er', flags: '--er-diagram' },
]
const configs = Array.isArray(a.configs) && a.configs.length ? a.configs : DEFAULT_CONFIGS

phase('Sweep')
log(`${a.projects.length} プロジェクト × ${configs.length} 構成をスイープ`)
const results = await parallel(a.projects.map((proj, i) => () =>
  agent(`あなたはレンダリング回帰の検証担当。リポジトリ /home/user/Juml で作業する。
対象プロジェクト: ${proj}
jar: ${jar} (リポジトリルートからの相対ならそのまま使う)
出力先: ${outBase}/p${i} (mkdir -p して使う)

以下の各構成について Bash で描画を実行し、失敗だけを報告しろ:
${configs.map(c => `- tag=${c.tag}: java -jar <jar> ${c.flags} -o <出力先>/${c.tag}.svg (${c.tag} が "all" のときは -o <出力先>/all ディレクトリ)`).join('\n')}

失敗判定 (いずれかで失敗):
1. 生成 SVG に "An error has occured" が含まれる (grep -rl で確認)
2. stderr に "FAILED" / "render failed" / "UML-R" のいずれかが含まれる
3. 期待した出力ファイルが 1 つも生成されない
注意: "--nav-graph" 系で "No Jetpack Navigation graphs" は失敗ではない (データ無しの正常終了)。
各実行の stderr は <出力先>/<tag>.err に保存して判定に使うこと。
失敗した構成だけを failures に入れ、message には stderr の該当行 (先頭 200 文字) を入れる。
全部成功なら failures: [] を返す。最終出力は StructuredOutput ツールで返す。`,
    { label: `sweep:${proj.split('/').filter(Boolean).pop()}`, phase: 'Sweep', schema: RESULT_SCHEMA, effort: 'low' })
    .then(r => ({ project: proj, ...r }))))

const ok = results.filter(Boolean)
const failures = ok.flatMap(r => (r.failures || []).map(f => ({ project: r.project, ...f })))
const totalRuns = ok.reduce((s, r) => s + (r.runs || 0), 0)
log(`スイープ完了: ${totalRuns} 実行 / 失敗 ${failures.length} 件`)
return { failures, totalRuns, projectsSwept: ok.length }
