---
paths:
  - "src/main/java/juml/core/formats/java/**"
  - "src/main/java/juml/core/formats/uml/**"
  - "src/test/java/juml/core/formats/java/**"
  - "src/test/java/juml/core/formats/uml/**"
---

# Java 解析パイプライン編集ルール（軽量ガードレール）

> このルールは Java 解析パイプライン（lexer → structure → PlantUML）の
> ソース/テストを編集するときだけ読み込まれます。
> **深い設計相談・大きめのロジック改善・バグ調査は `/java-analyze`
> （`java-analyst` サブエージェント）に委譲してください。** ここはあくまで
> 編集時に外してはいけない最小限の約束事だけを置きます。

## パイプラインの段（編集前に必ず意識する）

```
.java 文字列
  ↓ JavaLexer#tokenize()                 (juml.core.formats.java)
List<JavaToken>
  ↓ JavaStructureExtractor#extract() / extractHeadersOnly()   (juml.core.formats.uml)
List<JavaClassInfo>  →  ClassIndex (Stage A ヘッダのみ / Stage B 詳細)
  ↓ PlantUml*Diagram → PlantUmlRenderer
PlantUML テキスト → SVG/PNG
```

ある段に変更を入れたら、**下流（特に PlantUML 生成と ClassIndex 昇格）への波及**を確認すること。

## 守るべき約束事

- **既存の走査スタイルに合わせる**: `peek()` / `peek(n)` で先読み、`next()` で消費、
  `skipBlock()` で `{ }` を読み飛ばす。新しい走査ヘルパを安易に増やさない。
- **Lexer は完全な Java 仕様ではない**: 構造解析に必要なトークンのみ切り出す方針。
  キーワードは `isKw()`（`IDENT` かつ文字列一致）で判定し、コメントはトークン列に含めない
  （コメントは `JavaCommentScanner` 経由）。トークン種別を増やすときは影響範囲を明示する。
- **2 段階モードを壊さない**: 一覧用途は `extractHeadersOnly`（Stage A）、詳細図は
  `extract`（Stage B）。`JavaClassInfo.detailed` の意味を変えない。大規模 AOSP でのヒープ
  削減が目的なので、ヘッダ抽出に詳細パースを混ぜ込まない。
- **ClassIndex の並行性**: `put()` は `synchronized`、`detailedCache` は `ConcurrentHashMap`。
  Stage B 昇格はマルチスレッドから安全に呼べる前提を崩さない。
- **PlantUML テキスト生成**: `<` `>` `&` などのエスケープを忘れない。`skinparam` / `!theme` と
  `UmlOverrides` の関係を尊重する。
- **テストの追従**: ロジックを変えたら `src/test/java/juml/core/formats/**` の該当テストを
  同時に更新し、壊れたまま放置しない。
- **コメントは日本語の既存スタイルを維持**する。

## 字句レベル / 構造抽出の検証コマンド

- トークン化の確認: `/java-lex <file.java>`
- 構造抽出（クラス/メソッド/フィールド）の確認: `/java-struct <file.java>`
- 図生成ロジックの分析・改善案: `/java-diagram`
