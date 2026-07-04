// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 開いているプロジェクトの git リポジトリを<b>閲覧のみ</b>で参照する JGit ラッパー。
 *
 * <p>コミット履歴・ブランチ/タグ一覧・コミット diff・ファイル履歴・blame の読み取り API
 * だけを公開し、書き込み系 (commit / push / checkout 等) は一切呼ばない。呼び出しは
 * ブロッキング IO を含むため、GUI からは {@link javax.swing.SwingWorker} 経由で使うこと。</p>
 */
public final class GitRepoService implements AutoCloseable {

    /** 履歴読み込みの既定上限 (巨大リポジトリで GUI が固まらないように)。 */
    public static final int DEFAULT_LOG_LIMIT = 300;

    private final Repository repo;

    private GitRepoService(Repository repo) {
        this.repo = repo;
    }

    /**
     * {@code root} またはその上位ディレクトリから {@code .git} を探して開く。
     *
     * @return リポジトリが見つからなければ null
     */
    public static GitRepoService open(File root) {
        if (root == null || !root.isDirectory()) {
            return null;
        }
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(root);
            if (builder.getGitDir() == null) {
                return null;
            }
            return new GitRepoService(builder.build());
        } catch (IOException ex) {
            return null;
        }
    }

    /** 作業ツリーのルートディレクトリ。bare リポジトリなら null。 */
    public File workTree() {
        try {
            return repo.getWorkTree();
        } catch (org.eclipse.jgit.errors.NoWorkTreeException ex) {
            return null;
        }
    }

    /** HEAD のブランチ名 (detached HEAD のときは短縮 SHA)。 */
    public String currentBranch() throws IOException {
        String branch = repo.getBranch();
        return branch != null ? branch : "HEAD";
    }

    /** ローカルブランチ名の一覧 (refs/heads/ を除いた短縮名)。 */
    public List<String> localBranches() throws GitAPIException {
        return branchNames(false);
    }

    /** リモート追跡ブランチ名の一覧 (refs/remotes/ を除いた短縮名)。 */
    public List<String> remoteBranches() throws GitAPIException {
        return branchNames(true);
    }

    private List<String> branchNames(boolean remote) throws GitAPIException {
        List<String> out = new ArrayList<>();
        try (Git git = new Git(repo)) {
            org.eclipse.jgit.api.ListBranchCommand cmd = git.branchList();
            if (remote) {
                cmd.setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE);
            }
            for (Ref ref : cmd.call()) {
                out.add(Repository.shortenRefName(ref.getName()));
            }
        }
        return out;
    }

    /** タグ名の一覧 (refs/tags/ を除いた短縮名)。 */
    public List<String> tags() throws GitAPIException {
        List<String> out = new ArrayList<>();
        try (Git git = new Git(repo)) {
            for (Ref ref : git.tagList().call()) {
                out.add(Repository.shortenRefName(ref.getName()));
            }
        }
        return out;
    }

    /** 指定 ref (ブランチ/タグ/SHA) のコミット履歴を新しい順に最大 {@code limit} 件返す。 */
    public List<CommitInfo> log(String ref, int limit) throws GitAPIException, IOException {
        return logInternal(ref, null, limit);
    }

    /** 指定ファイル (リポジトリ相対パス) に触れたコミットの履歴を返す。 */
    public List<CommitInfo> fileLog(String ref, String relPath, int limit)
            throws GitAPIException, IOException {
        return logInternal(ref, relPath, limit);
    }

    private List<CommitInfo> logInternal(String ref, String relPath, int limit)
            throws GitAPIException, IOException {
        ObjectId start = repo.resolve(ref != null ? ref : Constants.HEAD);
        List<CommitInfo> out = new ArrayList<>();
        if (start == null) {
            return out; // 空リポジトリ (コミットなし)
        }
        try (Git git = new Git(repo)) {
            org.eclipse.jgit.api.LogCommand cmd = git.log().add(start).setMaxCount(limit);
            if (relPath != null && !relPath.isEmpty()) {
                cmd.addPath(relPath);
            }
            for (RevCommit c : cmd.call()) {
                out.add(toInfo(c));
            }
        }
        return out;
    }

    private static CommitInfo toInfo(RevCommit c) {
        PersonIdent author = c.getAuthorIdent();
        return new CommitInfo(
                c.getName(),
                c.abbreviate(7).name(),
                c.getShortMessage(),
                c.getFullMessage(),
                author != null ? author.getName() : "?",
                author != null ? author.getWhen() : new Date(c.getCommitTime() * 1000L));
    }

    /** 指定コミットの変更ファイル一覧 (第 1 親との差分。初回コミットは空ツリーと比較)。 */
    public List<FileChange> changesOf(String sha) throws IOException {
        List<FileChange> out = new ArrayList<>();
        for (DiffEntry e : diffEntries(sha, null)) {
            out.add(new FileChange(e.getChangeType().name(),
                    e.getNewPath(), e.getOldPath()));
        }
        return out;
    }

    /**
     * 指定コミットの unified diff テキストを返す。
     *
     * @param sha  対象コミット
     * @param path リポジトリ相対パス (null なら全ファイル)
     */
    public String diffOf(String sha, String path) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (DiffFormatter fmt = new DiffFormatter(buf)) {
            fmt.setRepository(repo);
            if (path != null && !path.isEmpty()) {
                fmt.setPathFilter(PathFilter.create(path));
            }
            fmt.format(diffEntries(sha, path));
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private List<DiffEntry> diffEntries(String sha, String path) throws IOException {
        ObjectId id = repo.resolve(sha);
        if (id == null) {
            return List.of();
        }
        // CanonicalTreeParser.reset(ObjectReader, ...) は渡した reader を所有しない
        // (呼び出し側が閉じる責務)。閉じ忘れると diff 表示のたびに ObjectReader の
        // ネイティブ資源が積み上がるため、try-with-resources で確実に閉じる。
        try (RevWalk walk = new RevWalk(repo);
             org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
            RevCommit commit = walk.parseCommit(id);
            AbstractTreeIterator oldTree;
            if (commit.getParentCount() > 0) {
                RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                CanonicalTreeParser p = new CanonicalTreeParser();
                p.reset(reader, parent.getTree());
                oldTree = p;
            } else {
                oldTree = new EmptyTreeIterator();
            }
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, commit.getTree());
            try (DiffFormatter fmt = new DiffFormatter(
                    java.io.OutputStream.nullOutputStream())) {
                fmt.setRepository(repo);
                fmt.setDetectRenames(true);
                if (path != null && !path.isEmpty()) {
                    fmt.setPathFilter(PathFilter.create(path));
                }
                return fmt.scan(oldTree, newTree);
            }
        }
    }

    /**
     * 指定 rev (SHA / ブランチ / タグ) 時点のファイル内容を UTF-8 テキストとして返す。
     * その rev にファイルが存在しなければ null。
     */
    public String fileContentAt(String rev, String relPath) throws IOException {
        ObjectId id = repo.resolve(rev);
        if (id == null || relPath == null || relPath.isEmpty()) {
            return null;
        }
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(id);
            try (org.eclipse.jgit.treewalk.TreeWalk tw =
                         org.eclipse.jgit.treewalk.TreeWalk.forPath(
                                 repo, relPath, commit.getTree())) {
                if (tw == null) {
                    return null;
                }
                ObjectId blob = tw.getObjectId(0);
                return new String(repo.open(blob).getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /** 指定コミットの第 1 親の SHA を返す。親がなければ (初回コミット等) null。 */
    public String parentOf(String rev) throws IOException {
        ObjectId id = repo.resolve(rev);
        if (id == null) {
            return null;
        }
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(id);
            if (commit.getParentCount() == 0) {
                return null;
            }
            return commit.getParent(0).getId().getName();
        }
    }

    /** 指定ファイルの blame (各行を最後に変更したコミット) を返す。 */
    public List<BlameLine> blame(String ref, String relPath)
            throws GitAPIException, IOException {
        List<BlameLine> out = new ArrayList<>();
        ObjectId start = repo.resolve(ref != null ? ref : Constants.HEAD);
        if (start == null) {
            return out;
        }
        try (Git git = new Git(repo)) {
            BlameResult result = git.blame()
                    .setFilePath(relPath)
                    .setStartCommit(start)
                    .setFollowFileRenames(true)
                    .call();
            if (result == null) {
                return out;
            }
            int lines = result.getResultContents().size();
            for (int i = 0; i < lines; i++) {
                RevCommit c = result.getSourceCommit(i);
                PersonIdent who = result.getSourceAuthor(i);
                out.add(new BlameLine(
                        c != null ? c.abbreviate(7).name() : "???????",
                        who != null ? who.getName() : "?",
                        who != null ? who.getWhen() : null,
                        result.getResultContents().getString(i)));
            }
        }
        return out;
    }

    /** 絶対パスをリポジトリ相対 (git 内部表記の '/' 区切り) に変換する。外なら null。 */
    public String relativize(File file) {
        File tree = workTree();
        if (tree == null || file == null) {
            return null;
        }
        String base = tree.getAbsolutePath();
        String target = file.getAbsolutePath();
        if (!target.startsWith(base + File.separator)) {
            return null;
        }
        return target.substring(base.length() + 1).replace(File.separatorChar, '/');
    }

    @Override
    public void close() {
        repo.close();
    }

    /** コミット 1 件分の表示用メタデータ (不変)。 */
    public static final class CommitInfo {
        public final String sha;
        public final String shortSha;
        public final String shortMessage;
        public final String fullMessage;
        public final String author;
        public final Date when;

        CommitInfo(String sha, String shortSha, String shortMessage,
                   String fullMessage, String author, Date when) {
            this.sha = sha;
            this.shortSha = shortSha;
            this.shortMessage = shortMessage;
            this.fullMessage = fullMessage;
            this.author = author;
            this.when = when;
        }
    }

    /** コミット内の変更ファイル 1 件 (不変)。 */
    public static final class FileChange {
        /** ADD / MODIFY / DELETE / RENAME / COPY。 */
        public final String changeType;
        public final String path;
        public final String oldPath;

        FileChange(String changeType, String path, String oldPath) {
            this.changeType = changeType;
            this.path = path;
            this.oldPath = oldPath;
        }

        /** 一覧表示用: {@code M path} / {@code R old -> new} 形式。 */
        public String display() {
            String letter = changeType.isEmpty() ? "?" : changeType.substring(0, 1);
            if ("RENAME".equals(changeType) || "COPY".equals(changeType)) {
                return letter + "  " + oldPath + " -> " + path;
            }
            return letter + "  " + ("DELETE".equals(changeType) ? oldPath : path);
        }
    }

    /** blame の 1 行分 (不変)。 */
    public static final class BlameLine {
        public final String shortSha;
        public final String author;
        public final Date when;
        public final String content;

        BlameLine(String shortSha, String author, Date when, String content) {
            this.shortSha = shortSha;
            this.author = author;
            this.when = when;
            this.content = content;
        }
    }
}
