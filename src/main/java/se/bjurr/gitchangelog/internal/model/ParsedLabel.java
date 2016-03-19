package se.bjurr.gitchangelog.internal.model;


import se.bjurr.gitchangelog.internal.git.model.GitCommit;
import se.bjurr.gitchangelog.internal.model.interfaces.IGitCommitReferer;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

public class ParsedLabel {
 private final List<ParsedIssue> issues = newArrayList();
 private final String name;

 public ParsedLabel(String name) {
  this.name = checkNotNull(name);
 }

 public String getName() {
  return name;
 }

 public List<ParsedIssue> getIssues() {
  return issues;
 }

 @Override
 public String toString() {
  return name;
 }

 @Override
 public int hashCode() {
  return name.hashCode();
 }

 @Override
 public boolean equals(Object obj) {
  if (obj.getClass() != getClass()) {
   return false;
  }
  return name.equals(((ParsedLabel) obj).getName());
 }

 public void addIssue(ParsedIssue parsedIssue) {
  this.issues.add(parsedIssue);
 }
}
