# /juml-explore

Juml と AOSP/AAOS を組み合わせた UML 図化・解析コマンド。

## 説明

AOSP または AAOS ソースツリーの構造を Juml で可視化・解析するための統合コマンドです。
エージェント（戦略設計）とスキル（知識ベース）を自動的にロードして、ユーザゴールをサポートします。

## 使用方法

### 基本形

```
/juml-explore <layer> <topic> [options]
```

### パラメータ

| パラメータ | 必須 | 値 | 説明 |
|---|---|---|---|
| `<layer>` | ○ | `aosp`, `aaos` | AOSP 汎用か AAOS 自動車向けか |
| `<topic>` | ○ | 下記参照 | 解析対象の領域 |
| `[--help]` | - | - | ヘルプ表示 |

### layer: AOSP (汎用)

```
/juml-explore aosp --help
```

利用可能な topic:

| Topic | 用途 |
|---|---|
| `--build` | Soong / Android.bp / Gradle 依存 |
| `--partition` | system / vendor / product / odm 配置 |
| `--hal` | AIDL / HAL / HIDL インタフェース |
| `--sepolicy` | SELinux / sepolicy / 権限 |
| `--module <path>` | 任意 module をクラス図化 (パス指定) |
| `--method <Class.method>` | 特定メソッドのシーケンス図 |

### layer: AAOS (自動車向け)

```
/juml-explore aaos --help
```

利用可能な topic:

| Topic | 用途 |
|---|---|
| `--carservice` | CarService / CarManager アーキテクチャ |
| `--vhal` | Vehicle HAL (IVehicle AIDL) |
| `--ui` | CarSystemUI / CarLauncher / RRO |
| `--security` | MultiUser / permissions / SELinux |
| `--property <propId>` | 特定 property のフロー (e.g., PERF_VEHICLE_SPEED) |
| `--manager <type>` | 特定 Manager (e.g., audio, occupant, property) |

## 例

### AOSP

```
# Soong / Android.bp について知りたい
/juml-explore aosp --build

# frameworks/base のクラス図を出したい
/juml-explore aosp --module frameworks/base/services/core

# CarService.onCreate の呼び出しチェーンを見たい
/juml-explore aosp --method CarService.onCreate

# sepolicy のルールを調べたい
/juml-explore aosp --sepolicy
```

### AAOS

```
# CarService 全体のアーキテクチャを理解したい
/juml-explore aaos --carservice

# VHAL と CarPropertyService の通信フローを知りたい
/juml-explore aaos --vhal

# MultiUser / ロール分離を理解したい
/juml-explore aaos --security

# 特定 Vehicle Property (速度) の get/set フロー
/juml-explore aaos --property PERF_VEHICLE_SPEED

# CarAudioManager のアーキテクチャ
/juml-explore aaos --manager audio
```

## 動作フロー

1. **Layer と Topic を解析**: AOSP / AAOS、build / partition / HAL など
2. **関連エージェント・スキルを自動ロード**:
   - AOSP → `aosp-juml-explorer` agent + `aosp-juml-analyzer` skill
   - AAOS → `aaos-juml-explorer` agent + `aaos-juml-analyzer` skill
3. **ユーザゴールを親エージェントに伝達**: 「carservice のアーキテクチャを理解したい」など
4. **エージェントが解析ロジック設計を提案**:
   - どこを読むか (Grep / Read)
   - どの仮説を立てるか
   - Juml のどのオプション (−c, −Q, −M, −G) をなぜ使うか
   - 期待される図・出力
5. **ユーザが親エージェントのコマンド提案を実行**: SVG / PNG / PUML 生成

## 出力規約

すべての応答は `/home/user/Juml/CLAUDE.md` に準拠:

```
## 変更サマリー
- **English phrase**: 日本語説明
  目的: なぜこれをしたか
```

## 関連スキル・エージェント

- **Agent**: `.claude/agents/aosp-juml-explorer.md`
- **Agent**: `.claude/agents/aaos-juml-explorer.md`
- **Skill**: `.claude/skills/aosp-juml-analyzer/`
- **Skill**: `.claude/skills/aaos-juml-analyzer/`

## トラブルシューティング

### "Juml jar が見つからない"

```sh
cd /home/user/Juml && ./gradlew jar
```

### "AOSP path が見つからない"

エージェントが `~/AOSP/` を想定しています。別の場所の場合、コマンド実行時に path を指定してください。

### "jar メモリ不足 (OOM)"

エージェントが `-Xmx` オプション調整を提案します。

---

## 設計思想

**実行と設計の分業**:
- このコマンド + エージェント = 「何をするか」を設計
- 親エージェント / ユーザ = 実際に Juml を「実行」

エージェントが提案するコマンドをコピペして実行することで、
AOSP/AAOS の複雑な構造を段階的に理解できます。
