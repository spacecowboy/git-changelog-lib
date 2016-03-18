package se.bjurr.gitchangelog.internal.model;


import se.bjurr.gitchangelog.internal.git.model.GitCommit;
import se.bjurr.gitchangelog.internal.model.interfaces.IGitCommitReferer;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

public class ParsedLabel implements IGitCommitReferer {
 private final List<GitCommit> gitCommits = newArrayList();
 private final String name;

 public ParsedLabel(String name) {
  this.name = checkNotNull(name);
 }

 @Override
 public GitCommit getGitCommit() {
  return checkNotNull(gitCommits.get(0), name);
 }

 @Override
 public String getName() {
  return name;
 }

 public List<GitCommit> getGitCommits() {
  return gitCommits;
 }

 @Override
 public String toString() {
  return name;
 }

 @Override
 public boolean equals(Object obj) {
  if (obj.getClass() != getClass()) {
   return false;
  }
  return name.equals(((ParsedIssue) obj).getName());
 }

 public void addCommit(GitCommit gitCommit) {
  this.gitCommits.add(gitCommit);
 }
}
