---
description: PlantUML 図（クラス図/シーケンス図/パッケージ図）の生成ロジックを分析し、出力品質の改善案を提案する
allowed-tools: Read, Bash
---

## タスク

対象またはテーマ: `$ARGUMENTS`

`$ARGUMENTS` の解釈:
- `class` または空欄 → `PlantUmlClassDiagram` を分析
- `sequence` → `PlantUmlSequenceDiagram` を分析
- `package` → `PlantUmlPackageDiagram` を分析
- ファイルパス → そのファイルが生成する図のロジックを分析
- その他のキーワード（例: `aaos`, `legend`, `options`）→ 関連するロジックを検索して分析

### 手順

1. **対象のソースファイルを Read で確認する**

   主要なターゲット:
   - `src/main/java/juml/core/formats/uml/PlantUmlClassDiagram.java`
   - `src/main/java/juml/core/formats/uml/PlantUmlSequenceDiagram.java`
   - `src/main/java/juml/core/formats/uml/PlantUmlPackageDiagram.java`
   - `src/main/java/juml/core/formats/uml/PlantUmlClassLegend.java`
   - `src/main/java/juml/core/formats/uml/DiagramStyle.java`

2. **以下の観点で分析する**

   **クラス図 (PlantUmlClassDiagram)**:
   - `Options` フラグの組み合わせで意図しない出力が出ないか
   - `showUsageRelations` の「利用関係」判定ロジックの正確さ
   - `groupByPackage` 時のパッケージ名衝突（同名パッケージが複数モジュールに存在する場合）
   - 利用関係の上限制御 (`maxUsagePerClass`) の効果

   **シーケンス図 (PlantUmlSequenceDiagram)**:
   - `JavaMethodInfo.Block` の `IF/WHILE/FOR/TRY` から `alt/loop/opt` への変換精度
   - ネストした制御フローのインデントと `end` の対応
   - `receiver` が `this` / `super` / フィールド名の場合の lifeline 解決

   **パッケージ図 (PlantUmlPackageDiagram)**:
   - モジュール間の依存関係の矢印方向
   - 循環依存の検出と表示

   **共通**:
   - PlantUML 特殊文字（`<`, `>`, `"`, `\n`）のエスケープ漏れ
   - `UmlOverrides` による skinparam の上書きが図に与える影響

3. **改善案を日本語で報告する**

```
## 図生成ロジックレポート: <対象>

### 現在のロジック概要
- <処理の流れ>

### 出力品質の問題点
- <問題タイトル>: <説明>
  再現条件: <どういう入力で起きるか>
  影響: <図の見た目・正確さへの影響>

### 改善提案
- <提案タイトル>: <内容>
  実装ヒント: <変更すべきメソッドや処理の概要>
  優先度: 高 / 中 / 低

### 関連ファイル
- <ファイルパス>: <変更が必要な理由>
```
