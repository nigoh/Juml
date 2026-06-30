// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

/**
 * AIDL インタフェースと、その {@code Stub} を継承して実装する Java クラスとの紐付け 1 件分。
 */
public final class AidlBinding {

    private final String aidlInterfaceFqn;
    private final String implementationFqn;
    private final String implementationFile;

    public AidlBinding(String aidlInterfaceFqn, String implementationFqn,
                        String implementationFile) {
        this.aidlInterfaceFqn = aidlInterfaceFqn == null ? "" : aidlInterfaceFqn;
        this.implementationFqn = implementationFqn == null ? "" : implementationFqn;
        this.implementationFile = implementationFile == null ? "" : implementationFile;
    }

    public String getAidlInterfaceFqn() {
        return aidlInterfaceFqn;
    }

    public String getImplementationFqn() {
        return implementationFqn;
    }

    public String getImplementationFile() {
        return implementationFile;
    }

    @Override
    public String toString() {
        return aidlInterfaceFqn + " ⇐ " + implementationFqn
                + (implementationFile.isEmpty() ? "" : " (" + implementationFile + ")");
    }
}
