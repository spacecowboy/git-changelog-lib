package se.bjurr.gitchangelog.api.model;


import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class IssueLabel {
 private final String name;
 private final List<Issue> issues;

 public IssueLabel(List<Issue> issues, String name) {
  this.issues = checkNotNull(issues, "issues");
  this.name = checkNotNull(name, "name");
 }

 public String getName() {
  return name;
 }

 public List<Issue> getIssues() {
  return issues;
 }

 @Override
 public String toString() {
  return "IssueLabel: " + name;
 }
}

