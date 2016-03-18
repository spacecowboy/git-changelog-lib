package se.bjurr.gitchangelog.api.model;

import se.bjurr.gitchangelog.api.model.interfaces.IAuthors;
import se.bjurr.gitchangelog.api.model.interfaces.ICommits;
import se.bjurr.gitchangelog.api.model.interfaces.IIssues;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class Changelog implements ICommits, IAuthors, IIssues {
 private final List<Commit> commits;
 private final List<Tag> tags;
 private final List<Author> authors;
 private final List<Issue> issues;
 private final List<IssueType> issueTypes;
 private final List<IssueLabel> labels;

 public Changelog(List<Commit> commits, List<Tag> tags, List<Author> authors, List<Issue> issues,
                  List<IssueType> issueTypes, List<IssueLabel> issueLabels) {
  this.commits = checkNotNull(commits, "commits");
  this.tags = checkNotNull(tags, "tags");
  this.authors = checkNotNull(authors, "authors");
  this.issues = checkNotNull(issues, "issues");
  this.issueTypes = checkNotNull(issueTypes, "issueTypes");
  this.labels = checkNotNull(issueLabels, "labels");
 }

 @Override
 public List<Issue> getIssues() {
  return issues;
 }

 @Override
 public List<Author> getAuthors() {
  return authors;
 }

 @Override
 public List<Commit> getCommits() {
  return commits;
 }

 public List<Tag> getTags() {
  return tags;
 }

 public List<IssueType> getIssueTypes() {
  return issueTypes;
 }

 public List<IssueLabel> getIssueLabels() {
  return labels;
 }
}
