// Juml ペルソナ探索ワークフロー (ADR-0002 / ADR-0003)
//
// ペルソナ・エージェント (haiku/sonnet のワーカー) が探索ハーネス GuiMonkey と CLI を実際に動かし、
// バグ / 使い勝手 / 不足機能を採取 → bug は sonnet が敵対的に検証 → usability / missing-feature は
// sonnet が製品バックログに集約する。修正・commit・バックログ更新は司令塔 (メインループ =
// opus/fable) が直列に行う。
//
// 起動:  Workflow({ name: "persona-explore" })                     … 既定 4 ペルソナ / seed 42
//        Workflow({ name: "persona-explore", args: { seed: 7, round: 2, known: ["..."],
//                   personas: ["newcomer", "power-user"] } })       … args が届く環境のみ
//        args が届かない環境ではこのファイルの本体をコピーし DEFAULTS を書き換えて Workflow({ script })
// 返り値: { confirmed, backlog, rejectedTitles, raw: {findings, personas} }
export const meta = {
  name: 'persona-explore',
  description: 'ペルソナ・エージェントが Juml を実際に動かし、確定バグと成長バックログ (使い勝手/不足機能) を返す',
  whenToUse: '「ペルソナで触って」「使い勝手や不足機能を洗い出して」「ランダムテストして」「アプリを育てたい」など、実操作ベースで成長の種を集めたいとき。修正はメインループが行う。',
  phases: [
    { title: 'Explore', detail: 'ペルソナごとにハーネス + CLI を実行 (haiku/sonnet)' },
    { title: 'Verify', detail: 'bug 判定を敵対的に検証 (sonnet, 偽陽性排除)' },
    { title: 'Triage', detail: 'usability / missing-feature をバックログ項目へ集約 (sonnet)' },
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
          kind: { type: 'string', enum: ['bug', 'usability', 'missing-feature'] },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          title: { type: 'string', description: '1 行の日本語タイトル (重複判定に使う)' },
          detail: { type: 'string' },
          repro: { type: 'string', description: '再現手順 (コマンド行 / シナリオ+seed / 操作列)' },
          evidence: { type: 'string', description: 'report.json の scenario/kind、PNG パス、出力抜粋' },
        },
        required: ['kind', 'severity', 'title', 'detail', 'repro', 'evidence'],
      },
    },
    notes: { type: 'string', description: 'ペルソナとしての総評 (1〜3 行)' },
  },
  required: ['findings'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    isReal: { type: 'boolean' },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
    reason: { type: 'string' },
    fixHint: { type: 'string', description: '本物なら、どのクラス/メソッドが原因かの見立て' },
  },
  required: ['isReal', 'confidence', 'reason'],
}

const BACKLOG_SCHEMA = {
  type: 'object',
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          kind: { type: 'string', enum: ['usability', 'missing-feature'] },
          title: { type: 'string' },
          personas: { type: 'array', items: { type: 'string' } },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          rationale: { type: 'string', description: 'なぜ価値があるか (複数ペルソナが困った等)' },
          acceptance: { type: 'string', description: '受け入れ条件 (何ができれば解決か)' },
          effort: { type: 'string', enum: ['S', 'M', 'L'] },
          sources: { type: 'array', items: { type: 'string' }, description: '元になった所見タイトル' },
        },
        required: ['kind', 'title', 'personas', 'severity', 'rationale', 'acceptance', 'effort', 'sources'],
      },
    },
  },
  required: ['items'],
}

// ── 既定値 (args が届かない環境ではここを書き換えてインライン起動する) ──────────────
const DEFAULTS = {
  round: 1,
  seed: 42,
  fuzz: 60,
  known: [],            // 既知の所見タイトル (docs/persona-backlog.md と前ラウンドの confirmed)。重複を落とす
  personas: ['newcomer', 'power-user', 'android-dev', 'aosp-analyst'],
}

// ペルソナ・カタログ: agentType は .claude/agents/persona-<name>.md。model は ADR-0003 の階層。
const CATALOG = {
  'newcomer': {
    model: 'haiku', effort: 'low',
    project: 'src/test/resources/samples/easypermissions', alt: 'src/test/resources/samples/layouts',
    scenarios: 'S2,S3,S1,S5,S13,S15,S21',
    cli: ['-c', '-q <Class.method> (--list-methods で候補を見てから)', '--summary'],
  },
  'power-user': {
    model: 'haiku', effort: 'low',
    project: 'src/main/java/juml/core/formats/java', alt: 'src/test/resources/samples/easypermissions',
    scenarios: 'S6,S16,S18,S9,S10,S11,S14,S21',
    cli: ['-c --preset minimal', '--function-list --function-list-format csv'],
  },
  'android-dev': {
    model: 'sonnet', effort: 'medium',
    project: 'src/test/resources/samples/layouts', alt: 'src/test/resources/samples/navigation',
    scenarios: 'S2,S3,S4,S13,S17,S21',
    cli: ['-m', '-M', '-d', '-D', '--nav-graph', '-G', '--screen-flow', '--action-map', '--settings', '-c --jetpack'],
  },
  'aosp-analyst': {
    model: 'sonnet', effort: 'medium',
    project: 'src/main/java', alt: 'src/test/resources/samples/aidl',
    scenarios: 'S2,S3,S9,S19,S7,S21',
    cli: ['--aidl-binding', '--android-bp', '--android-mk', '--selinux', '--vintf', '--partitions', '--insights', '--impact <FQN>', '--ref-find <FQN>'],
  },
}

let a = args || {}
if (typeof a === 'string') {
  try { a = JSON.parse(a) } catch (e) { a = {} }
}
const cfg = { ...DEFAULTS, ...a }
const known = new Set((cfg.known || []).map(t => norm(t)))
const personas = (cfg.personas || DEFAULTS.personas).filter(p => CATALOG[p])
if (personas.length === 0) {
  throw new Error('personas が空です: ' + Object.keys(CATALOG).join(', ') + ' から選ぶ')
}

function norm(t) {
  return String(t || '').replace(/\s+/g, '').slice(0, 60)
}

function explorePrompt(name, p, idx) {
  const seed = Number(cfg.seed) + idx
  const runName = `r${cfg.round}-${name}`
  return `対象リポジトリ: /home/user/Juml (Java Swing アプリ "Juml"、jar は build/libs/Juml.jar)。
あなたはペルソナ「${name}」として Juml を実際に動かし、観察した事実だけを報告する (ラウンド ${cfg.round})。

実行スクリプト (この通りに実行する):
1. GUI ハーネス:
   .claude/skills/persona-explore/scripts/run-monkey.sh ${runName} ${p.project} --alt ${p.alt} --persona ${name} --scenarios ${p.scenarios} --seed ${seed} --fuzz ${cfg.fuzz}
   出力は /tmp/juml-monkey/${runName}/ (report.json / stdout.log / shots/*.png)。
   ※ 15〜25 分かかる。timeout 1500 秒で打ち切られる。終わったら report.json を必ず読む。
2. CLI (出力先は /tmp/juml-monkey/${runName}/cli/ を mkdir -p して使う):
   ${p.cli.map(c => 'java -jar build/libs/Juml.jar ' + c + ' ' + p.project).join('\n   ')}
   終了コード・stderr・出力の中身 (空か / 期待した要素があるか) を見る。
3. shots/*.png を最低 3 枚 Read で見る。

報告ルール:
- report.json の findings は kind ごとに意味が違う: exception / uncaught / stderr / invariant / edt-stall /
  leak-* / applog-ERROR は bug 候補。applog-WARN は内容次第。dialog は「自動で閉じたダイアログ」で
  それ自体はバグではない。harness は「ハーネス側の限界」で報告不要 (ただし SvgPreviewPanel が
  見つからない等が本当に UI 状態の問題なら報告する)。
- 既知の所見 (次のリスト) と同じものは報告しない:
${[...known].length ? [...known].map(k => '  - ' + k).join('\n') : '  (なし)'}
- 各所見に repro と evidence を必ず付ける。数を稼がない。見つからなければ findings: [] でよい。
- 最終出力は StructuredOutput ツールで返す。`
}

phase('Explore')
log(`ラウンド ${cfg.round}: ${personas.length} ペルソナを起動 (seed=${cfg.seed}, fuzz=${cfg.fuzz})`)
// サブエージェント定義 (.claude/agents/persona-<name>.md) は Claude Code のセッション開始時に登録される。
// 同じセッション内で追加した直後は agentType が見つからないため、その場合はペルソナ定義を
// Read させる汎用エージェントにフォールバックする (定義ファイルが常に唯一の情報源)。
async function runPersona(name, i) {
  const p = CATALOG[name]
  const opts = { label: `persona:${name}`, phase: 'Explore', schema: FINDINGS_SCHEMA, model: p.model, effort: p.effort }
  try {
    return await agent(explorePrompt(name, p, i), { ...opts, agentType: `persona-${name}` })
  } catch (e) {
    log(`persona:${name}: agentType 未登録のため定義ファイル Read 方式にフォールバック (${String(e).slice(0, 80)})`)
    const pre = `まず .claude/agents/persona-${name}.md を Read し、その frontmatter 以下の本文に書かれた人物・役割・手順・出力ルールに従って振る舞え。\n\n`
    return await agent(pre + explorePrompt(name, p, i), opts)
  }
}

const explored = await parallel(personas.map((name, i) => () =>
  runPersona(name, i).then(r => ({ name, result: r }))))

const raw = []
for (const e of explored.filter(Boolean)) {
  if (!e.result) {
    log(`persona:${e.name} は結果なし (skip/limit)`)
    continue
  }
  for (const f of e.result.findings || []) {
    raw.push({ ...f, persona: e.name })
  }
}
log(`raw findings: ${raw.length}`)

// 全ペルソナ横断で重複を落とす (barrier が必要な唯一の理由)。既知タイトルも除外。
const seen = new Map()
for (const f of raw) {
  const key = norm(f.title)
  if (known.has(key)) {
    continue
  }
  if (seen.has(key)) {
    seen.get(key).personas.push(f.persona)
  } else {
    seen.set(key, { ...f, personas: [f.persona] })
  }
}
const unique = [...seen.values()]
log(`deduped: ${unique.length} (known で除外: ${raw.length - unique.length - (raw.length - [...new Set(raw.map(f => norm(f.title)))].length)})`)

const bugs = unique.filter(f => f.kind === 'bug')
const growth = unique.filter(f => f.kind !== 'bug')

phase('Verify')
log(`bug 候補 ${bugs.length} 件を敵対的に検証`)
const verified = await parallel(bugs.map(f => () =>
  agent(`対象リポジトリ: /home/user/Juml。次のバグ報告を【反証】しろ。コードを Read で読み、必要なら
build/libs/Juml.jar や /tmp/juml-monkey 配下のレポート/スクリーンショットを見て再現を試みる。
報告が誤読・仕様どおり・既存テストで守られている・ハーネス側の限界 (dialog killer / Robot / Xvfb 由来)、
のいずれかなら isReal=false。本当に誤動作する具体的シナリオを自分で組み立てられた場合のみ isReal=true。迷ったら false。
--- 報告 (persona: ${f.personas.join(',')}) ---
title: ${f.title}
severity: ${f.severity}
detail: ${f.detail}
repro: ${f.repro}
evidence: ${f.evidence}`,
    { label: `verify:${f.title.slice(0, 30)}`, phase: 'Verify', schema: VERDICT_SCHEMA, model: 'sonnet', effort: 'high' })
    .then(v => ({ ...f, verdict: v }))))

const confirmed = verified.filter(Boolean).filter(f => f.verdict && f.verdict.isReal)
const rejected = verified.filter(Boolean).filter(f => f.verdict && !f.verdict.isReal)

phase('Triage')
let backlog = []
if (growth.length > 0) {
  const triaged = await agent(`対象: Juml (Java Swing の UML/Android 解析ツール)。ペルソナ探索で集まった
使い勝手 / 不足機能の所見を、製品バックログ項目に集約せよ (ラウンド ${cfg.round})。
- 同じ根本原因の所見は 1 項目にまとめ、sources に元タイトルを列挙する。
- 複数ペルソナが困ったものを優先。severity は「ユーザーが目的を達成できない=high / 回り道で達成=medium / 好み=low」。
- acceptance は検証可能な 1〜2 文 (例: 「ツールバーの図種ボタンが無効なとき、ツールチップに理由が出る」)。
- effort は S (1 ファイル・数十行) / M (数ファイル) / L (設計変更)。
- 数を増やさない。根拠の弱いもの (evidence が無い) は落とす。
--- 所見 ---
${growth.map((f, i) => `${i + 1}. [${f.kind}/${f.severity}] ${f.title} (persona: ${f.personas.join(',')})\n   detail: ${f.detail}\n   repro: ${f.repro}\n   evidence: ${f.evidence}`).join('\n')}`,
    { label: 'triage:backlog', phase: 'Triage', schema: BACKLOG_SCHEMA, model: 'sonnet', effort: 'medium' })
  backlog = triaged ? triaged.items : []
}
log(`bug 確定 ${confirmed.length} / 棄却 ${rejected.length} / バックログ項目 ${backlog.length}`)
return {
  confirmed,
  backlog,
  rejectedTitles: rejected.map(r => r.title + ' — ' + (r.verdict.reason || '').slice(0, 120)),
  raw: { findings: raw.length, personas: explored.filter(Boolean).map(e => ({ name: e.name, ok: !!e.result, notes: e.result && e.result.notes })) },
}
