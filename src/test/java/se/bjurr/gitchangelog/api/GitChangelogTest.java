package se.bjurr.gitchangelog.api;

import static com.google.common.io.Resources.getResource;
import static se.bjurr.gitchangelog.api.GitChangelogApi.gitChangelogApiBuilder;

import java.io.File;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

public class GitChangelogTest {
 private static Logger LOG = Logger.getLogger(GitChangelogTest.class.getSimpleName());

 private String repoRoot;

 @Before
 public void before() throws Exception {
  repoRoot = getRepoRoot(new File(getResource("github-issues.json").toURI()));
 }

 @Test
 public void createFullChangelog() throws Exception {
  String toFile = repoRoot + "/CHANGELOG.md";
  gitChangelogApiBuilder()//
    .withSettings(new File(repoRoot + "/changelog.json").toURI().toURL())//
    .toFile(toFile);
  LOG.info("Wrote " + toFile);
 }

 private String getRepoRoot(File file) {
  if (file.getParentFile().getPath().endsWith("git-changelog-lib")) {
   return file.getParentFile().getPath();
  }
  return getRepoRoot(file.getParentFile());
 }
}
