// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.Objects;

/**
 * 画面遷移 1 件分の情報 (caller → target Activity)。
 *
 * <p>{@link IntentNavigationDetector} が検出した {@code startActivity},
 * {@code startActivityForResult}, {@code Intent.setClass} などの呼び出しを
 * 1 件の {@code ScreenTransition} として表す。</p>
 */
public final class ScreenTransition {

    /** 遷移の発生種別。 */
    public enum Kind {
        /** {@code startActivity(new Intent(ctx, X.class))}。 */
        START_ACTIVITY,
        /** {@code startActivityForResult(...)}。 */
        START_FOR_RESULT,
        /** {@code Intent.setClass(...)} 経由 (後で startActivity される想定)。 */
        SET_CLASS,
        /** Car App Library の {@code getScreenManager().push(new XxxScreen(...))} 等。 */
        SCREEN_PUSH,
        /** FragmentManager トランザクション {@code .replace/.add(container, new XxxFragment())}。 */
        FRAGMENT_TXN,
        /** Jetpack Navigation {@code findNavController().navigate(R.id...)} / NavDirections。 */
        NAV_ACTION,
        /** Compose Navigation {@code navController.navigate("route" / Screen.X.route)}。 */
        COMPOSE_NAVIGATE,
        /** その他の Intent 構築。 */
        OTHER;

        /**
         * 図やレポートに表示する、素人にも分かる日本語ラベルを返す。
         * 生の enum 名 (START_FOR_RESULT 等) の代わりに使う。
         */
        public String jpLabel() {
            switch (this) {
                case START_ACTIVITY:   return "画面を開く";
                case START_FOR_RESULT: return "結果を受け取る遷移";
                case SET_CLASS:        return "遷移先を準備";
                case SCREEN_PUSH:      return "画面を積む";
                case FRAGMENT_TXN:     return "画面を差し替え";
                case NAV_ACTION:       return "ナビゲーション遷移";
                case COMPOSE_NAVIGATE: return "Compose 画面遷移";
                case OTHER:
                default:               return "その他の遷移";
            }
        }

        /** 凡例用の 1 行説明 (何をしている遷移か + 元の API 名)。 */
        public String jpDescription() {
            switch (this) {
                case START_ACTIVITY:   return "別の画面へ普通に移動する (startActivity)";
                case START_FOR_RESULT: return "開いた画面から結果を返してもらう (startActivityForResult)";
                case SET_CLASS:        return "遷移先だけ先に指定 (Intent.setClass) — 後で開かれる想定";
                case SCREEN_PUSH:      return "Car App の画面を重ねて表示 (ScreenManager.push)";
                case FRAGMENT_TXN:     return "画面の一部 (Fragment) を入れ替える (add / replace)";
                case NAV_ACTION:       return "Jetpack Navigation での画面移動 (navigate)";
                case COMPOSE_NAVIGATE: return "Compose Navigation での画面移動 (navigate)";
                case OTHER:
                default:               return "上記以外の画面遷移";
            }
        }
    }

    private final String fromFqn;
    private final String fromMethod;
    private final String targetClassName;
    private final String file;
    private final int lineHint;
    private final Kind kind;

    public ScreenTransition(String fromFqn, String fromMethod, String targetClassName,
                              String file, int lineHint, Kind kind) {
        this.fromFqn = nz(fromFqn);
        this.fromMethod = nz(fromMethod);
        this.targetClassName = nz(targetClassName);
        this.file = nz(file);
        this.lineHint = lineHint;
        this.kind = kind == null ? Kind.OTHER : kind;
    }

    public String getFromFqn() { return fromFqn; }
    public String getFromMethod() { return fromMethod; }
    /** 遷移先のクラス名 (単純名もしくは FQN。ソース表現のまま)。 */
    public String getTargetClassName() { return targetClassName; }
    public String getFile() { return file; }
    public int getLineHint() { return lineHint; }
    public Kind getKind() { return kind; }

    /** 遷移先の単純名 ({@code com.x.Foo} → {@code Foo})。 */
    public String getTargetSimpleName() {
        int dot = targetClassName.lastIndexOf('.');
        return dot < 0 ? targetClassName : targetClassName.substring(dot + 1);
    }

    /** 遷移元の単純名。 */
    public String getFromSimpleName() {
        int dot = fromFqn.lastIndexOf('.');
        return dot < 0 ? fromFqn : fromFqn.substring(dot + 1);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenTransition)) return false;
        ScreenTransition that = (ScreenTransition) o;
        return lineHint == that.lineHint
                && Objects.equals(fromFqn, that.fromFqn)
                && Objects.equals(fromMethod, that.fromMethod)
                && Objects.equals(targetClassName, that.targetClassName)
                && Objects.equals(file, that.file)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromFqn, fromMethod, targetClassName, file, lineHint, kind);
    }

    @Override
    public String toString() {
        return kind + ": " + fromFqn + "." + fromMethod + " -> " + targetClassName;
    }
}
