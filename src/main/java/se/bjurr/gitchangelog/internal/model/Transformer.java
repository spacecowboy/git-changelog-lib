package se.bjurr.gitchangelog.internal.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Multimap;
import se.bjurr.gitchangelog.api.model.*;
import se.bjurr.gitchangelog.internal.git.model.GitCommit;
import se.bjurr.gitchangelog.internal.git.model.GitTag;
import se.bjurr.gitchangelog.internal.issues.IssueParser;
import se.bjurr.gitchangelog.internal.settings.IssuesUtil;
import se.bjurr.gitchangelog.internal.settings.Settings;
import se.bjurr.gitchangelog.internal.settings.SettingsIssue;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static com.google.common.collect.Maps.newTreeMap;
import static com.google.common.collect.Multimaps.index;
import static java.util.TimeZone.getTimeZone;
import static java.util.regex.Pattern.compile;
import static se.bjurr.gitchangelog.internal.common.GitPredicates.ignoreCommits;

public class Transformer {

 public Transformer(Settings settings) {
  this.settings = settings;
 }

 private final Settings settings;

 public List<Tag> toTags(List<GitTag> gitTags) {

  Iterable<Tag> tags = transform(gitTags, new Function<GitTag, Tag>() {
   @Override
   public Tag apply(GitTag input) {
    List<GitCommit> gitCommits = input.getGitCommits();
    List<Commit> commits = toCommits(gitCommits);
    List<Author> authors = toAuthors(gitCommits);
    List<ParsedIssue> parsedIssues;
    parsedIssues = new IssueParser(settings, gitCommits).parseForIssues();
    List<Issue> issues = toIssues(parsedIssues);
    return new Tag(toReadableTagName(input.getName()), commits, authors, issues, toIssueTypes(parsedIssues));
   }
  });

  tags = filter(tags, new Predicate<Tag>() {
   @Override
   public boolean apply(Tag input) {
    return !input.getAuthors().isEmpty() && !input.getCommits().isEmpty();
   }
  });

  return newArrayList(tags);
 }

 private String toReadableTagName(String input) {
  Matcher matcher = compile(settings.getReadableTagName()).matcher(input);
  if (matcher.find()) {
   if (matcher.groupCount() == 0) {
    throw new RuntimeException("Pattern: \"" + settings.getReadableTagName() + "\" did not match any group in: \""
      + input + "\"");
   }
   return matcher.group(1);
  }
  return input;
 }

 public List<Commit> toCommits(Collection<GitCommit> from) {
  Iterable<GitCommit> filteredCommits = filter(from, ignoreCommits(settings.getIgnoreCommitsIfMessageMatches()));
  return newArrayList(transform(filteredCommits, new Function<GitCommit, Commit>() {
   @Override
   public Commit apply(GitCommit c) {
    return toCommit(c);
   }
  }));
 }

 public List<Issue> toIssues(List<ParsedIssue> issues) {
  Iterable<ParsedIssue> issuesWithCommits = filterWithCommits(issues);

  return newArrayList(transform(issuesWithCommits, parsedIssueToIssue()));
 }

 private Iterable<ParsedIssue> filterWithCommits(List<ParsedIssue> issues) {
  Iterable<ParsedIssue> issuesWithCommits = filter(issues, new Predicate<ParsedIssue>() {
   @Override
   public boolean apply(ParsedIssue input) {
    return !toCommits(input.getGitCommits()).isEmpty();
   }
  });
  return issuesWithCommits;
 }

 private Function<ParsedIssue, Issue> parsedIssueToIssue() {
  return new Function<ParsedIssue, Issue>() {
   @Override
   public Issue apply(ParsedIssue input) {
    List<GitCommit> gitCommits = input.getGitCommits();
    return new Issue(//
      toCommits(gitCommits), //
      toAuthors(gitCommits),//
      input.getName(), //
      input.getTitle().or(""), //
      input.getIssue(), //
      input.getLink(),
      input.getLabels());
   }
  };
 }

 private Commit toCommit(GitCommit gitCommit) {
  return new Commit(//
    gitCommit.getAuthorName(), //
    gitCommit.getAuthorEmailAddress(), //
    format(gitCommit.getCommitTime()), //
    gitCommit.getCommitTime().getTime(), //
    toMessage(settings.removeIssueFromMessage(), new IssuesUtil(settings).getIssues(), gitCommit.getMessage()), //
    gitCommit.getHash());
 }

 @VisibleForTesting
 String toMessage(boolean removeIssueFromMessage, List<SettingsIssue> issues, String message) {
  return removeIssuesFromString(removeIssueFromMessage, issues, message);
 }

 private String removeIssuesFromString(boolean removeIssueFromMessage, List<SettingsIssue> issues, String string) {
  if (removeIssueFromMessage) {
   for (SettingsIssue issue : issues) {
    string = string.replaceAll(issue.getPattern(), "");
   }
  }
  return string;
 }

 private String format(Date commitTime) {
  SimpleDateFormat df = new SimpleDateFormat(settings.getDateFormat());
  df.setTimeZone(getTimeZone(settings.getTimeZone()));
  return df.format(commitTime);
 }

 public List<Author> toAuthors(List<GitCommit> gitCommits) {
  final Multimap<String, GitCommit> commitsPerAuthor = index(gitCommits, new Function<GitCommit, String>() {
   @Override
   public String apply(GitCommit input) {
    return input.getAuthorEmailAddress() + "-" + input.getAuthorName();
   }
  });

  Iterable<String> authorsWithCommits = filter(commitsPerAuthor.keySet(), new Predicate<String>() {
   @Override
   public boolean apply(String input) {
    return toCommits(commitsPerAuthor.get(input)).size() > 0;
   }
  });

  return newArrayList(transform(authorsWithCommits, new Function<String, Author>() {
   @Override
   public Author apply(String input) {
    List<GitCommit> gitCommitsOfSameAuthor = newArrayList(commitsPerAuthor.get(input));
    List<Commit> commitsOfSameAuthor = toCommits(gitCommitsOfSameAuthor);
    return new Author(//
      commitsOfSameAuthor.get(0).getAuthorName(), //
      commitsOfSameAuthor.get(0).getAuthorEmailAddress(), //
      commitsOfSameAuthor);
   }
  }));
 }

 public List<IssueType> toIssueTypes(List<ParsedIssue> issues) {
  Map<String, List<Issue>> issuesPerName = newTreeMap();

  for (ParsedIssue parsedIssue : filterWithCommits(issues)) {
   if (!issuesPerName.containsKey(parsedIssue.getName())) {
    issuesPerName.put(parsedIssue.getName(), new ArrayList<Issue>());
   }
   issuesPerName.get(parsedIssue.getName())//
     .add(parsedIssueToIssue().apply(parsedIssue));
  }

  List<IssueType> issueTypes = newArrayList();
  for (String name : issuesPerName.keySet()) {
   issueTypes.add(new IssueType(issuesPerName.get(name), name));
  }
  return issueTypes;
 }

 public List<IssueLabel> toIssueLabels(List<ParsedIssue> issues) {
  Map<String, List<Issue>> labelIssues = newTreeMap();

  for (ParsedIssue issue : filterWithCommits(issues)) {
   for (String label : issue.getLabels()) {
    if (!labelIssues.containsKey(label)) {
     labelIssues.put(label, new ArrayList<Issue>());
    }
    labelIssues.get(label).add(parsedIssueToIssue().apply(issue));
   }
  }

  List<IssueLabel> result = new ArrayList<>();
  for (String label : labelIssues.keySet()) {
   result.add(new IssueLabel(labelIssues.get(label), label));
  }
  return result;
 }
}
