// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ProjectLoader#isAospTree(File)} の浅い AOSP 検出ロジックを検証する。
 *
 * <p>検出が当たると {@code useAospDefaults} が有効化され、out/ や prebuilts/
 * など巨大な非ソースディレクトリが走査から除外される。誤検出 (通常の Gradle
 * プロジェクトを AOSP 扱い) しないことも併せて確認する。</p>
 */
public class ProjectLoaderAospDetectionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private void mkdirs(File root, String... rel) {
        for (String r : rel) {
            assertTrue(new File(root, r).mkdirs());
        }
    }

    @Test
    public void detectsByRepoDir() throws IOException {
        File root = tmp.newFolder("aosp-repo");
        mkdirs(root, ".repo");
        assertTrue(ProjectLoader.isAospTree(root));
    }

    @Test
    public void detectsByEnvsetupScript() throws IOException {
        File root = tmp.newFolder("aosp-env");
        mkdirs(root, "build");
        assertTrue(new File(root, "build/envsetup.sh").createNewFile());
        assertTrue(ProjectLoader.isAospTree(root));
    }

    @Test
    public void detectsBySoongDir() throws IOException {
        File root = tmp.newFolder("aosp-soong");
        mkdirs(root, "build/soong");
        assertTrue(ProjectLoader.isAospTree(root));
    }

    @Test
    public void detectsByThreeTopLevelMarkers() throws IOException {
        File root = tmp.newFolder("aosp-markers");
        mkdirs(root, "frameworks", "packages", "system");
        assertTrue(ProjectLoader.isAospTree(root));
    }

    @Test
    public void doesNotDetectTwoMarkers() throws IOException {
        File root = tmp.newFolder("two-markers");
        mkdirs(root, "packages", "system");
        assertFalse(ProjectLoader.isAospTree(root));
    }

    @Test
    public void doesNotDetectPlainGradleProject() throws IOException {
        File root = tmp.newFolder("gradle-app");
        mkdirs(root, "app/src/main/java", "gradle/wrapper");
        assertTrue(new File(root, "build.gradle").createNewFile());
        assertFalse(ProjectLoader.isAospTree(root));
    }

    @Test
    public void doesNotDetectNullOrMissing() {
        assertFalse(ProjectLoader.isAospTree(null));
        assertFalse(ProjectLoader.isAospTree(new File(tmp.getRoot(), "does-not-exist")));
    }
}
