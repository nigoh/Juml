#!/bin/sh

DIR=`dirname "$0"`

# 同梱 Graphviz dot バイナリを検索し、あれば PlantUML に渡す
GRAPHVIZ_PROP=""
OS=$(uname -s 2>/dev/null)
ARCH=$(uname -m 2>/dev/null)
case "$ARCH" in
    aarch64|arm64) ARCH_NORM="aarch64" ;;
    *)             ARCH_NORM="amd64" ;;
esac
case "$OS" in
    Darwin) PLATFORM="mac-$ARCH_NORM" ;;
    *)      PLATFORM="linux-$ARCH_NORM" ;;
esac
BUNDLED_DOT="$DIR/graphviz/$PLATFORM/dot"
if [ ! -x "$BUNDLED_DOT" ]; then
    BUNDLED_DOT="$DIR/graphviz/dot"
fi
if [ -x "$BUNDLED_DOT" ]; then
    GRAPHVIZ_PROP="-Dnet.sourceforge.plantuml.GRAPHVIZ_DOT=$BUNDLED_DOT"
fi

# メモリ削減のための JVM オプション。Juml は型名/FQN/PlantUML 断片など重複する
# 文字列を大量に保持するため、G1 の文字列重複排除でヒープを抑える (CPU コストは僅少)。
# 大規模プロジェクトでヒープ上限や GC を変えたい場合は JUML_JAVA_OPTS で上書きできる。
#   例: JUML_JAVA_OPTS="-Xmx2g" ./Juml.sh ~/MyApp
JUML_JAVA_OPTS="${JUML_JAVA_OPTS:--XX:+UseStringDeduplication}"

java $JUML_JAVA_OPTS $GRAPHVIZ_PROP -jar "$DIR/Juml.jar" "$@"
