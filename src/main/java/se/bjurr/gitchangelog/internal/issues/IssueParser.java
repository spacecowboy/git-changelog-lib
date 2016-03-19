package se.bjurr.gitchangelog.internal.issues;

import org.slf4j.Logger;
import se.bjurr.gitchangelog.api.exceptions.GitChangelogIntegrationException;
import se.bjurr.gitchangelog.internal.git.model.GitCommit;
import se.bjurr.gitchangelog.internal.integrations.github.GitHubHelper;
import se.bjurr.gitchangelog.internal.integrations.github.GitHubIssue;
import se.bjurr.gitchangelog.internal.integrations.jira.JiraClient;
import se.bjurr.gitchangelog.internal.integrations.jira.JiraIssue;
import se.bjurr.gitchangelog.internal.model.ParsedIssue;
import se.bjurr.gitchangelog.internal.model.ParsedLabel;
import se.bjurr.gitchangelog.internal.settings.IssuesUtil;
import se.bjurr.gitchangelog.internal.settings.Settings;
import se.bjurr.gitchangelog.internal.settings.SettingsIssue;
import se.bjurr.gitchangelog.internal.settings.SettingsLabel;

import java.util.*;
import java.util.regex.Matcher;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Ordering.usingToString;
import static java.util.regex.Pattern.compile;
import static org.slf4j.LoggerFactory.getLogger;
import static se.bjurr.gitchangelog.internal.integrations.github.GitHubServiceFactory.getGitHubService;
import static se.bjurr.gitchangelog.internal.integrations.jira.JiraClientFactory.createJiraClient;
import static se.bjurr.gitchangelog.internal.settings.SettingsIssueType.GITHUB;
import static se.bjurr.gitchangelog.internal.settings.SettingsIssueType.JIRA;

public class IssueParser {
 private static final Logger LOG = getLogger(IssueParser.class);

 private final List<GitCommit> commits;
 private final Settings settings;

 public IssueParser(Settings settings, List<GitCommit> commits) {
  this.settings = settings;
  this.commits = commits;
 }

 public Settings getSettings() {
  return settings;
 }

 public List<GitCommit> getCommits() {
  return commits;
 }

 public Pair<Set<ParsedLabel>, List<ParsedIssue>> parseForIssues() {
  Map<String, ParsedIssue> foundIssues = newHashMap();
  LinkedHashSet<ParsedLabel> labels = new LinkedHashSet<>();

  GitHubHelper gitHubHelper = null;
  if (settings.getGitHubApi().isPresent()) {
   gitHubHelper = new GitHubHelper(getGitHubService(settings.getGitHubApi().get(), settings.getGitHubToken()));
  }

  JiraClient jiraClient = null;
  if (settings.getJiraServer().isPresent()) {
   jiraClient = createJiraClient(settings.getJiraServer().get());
   if (settings.getJiraUsername().isPresent()) {
    jiraClient.withBasicCredentials(settings.getJiraUsername().get(), settings.getJiraPassword().get());
   }
  }

  List<SettingsIssue> patterns = new IssuesUtil(settings).getIssues();

  for (GitCommit gitCommit : commits) {
   Set<ParsedLabel> commitLabels = parseForLabelsInCommit(settings.getLabels(), gitCommit);
   deduplicate(labels, commitLabels);
   labels.addAll(commitLabels);
   parseForIssuesInCommit(foundIssues, gitHubHelper, jiraClient, patterns, gitCommit, commitLabels);
  }
  // todo return labels
  return new Pair<Set<ParsedLabel>, List<ParsedIssue>>(labels, usingToString().sortedCopy(foundIssues.values()));
  //return usingToString().sortedCopy(foundIssues.values());
 }

 private void deduplicate(LinkedHashSet<ParsedLabel> superSet, Set<ParsedLabel> set) {
  for (ParsedLabel superLabel: superSet) {
   if (set.contains(superLabel)) {
    set.remove(superLabel);
    set.add(superLabel);
   }
  }
 }

 private Set<ParsedLabel> parseForLabelsInCommit(List<SettingsLabel> labelPatterns, GitCommit gitCommit) {
  LinkedHashSet<ParsedLabel> labels = new LinkedHashSet<>();
  for (SettingsLabel labelPattern: labelPatterns) {
   Matcher matcher = compile(labelPattern.getPattern()).matcher(gitCommit.getMessage());
   while (matcher.find()) {
    String matched = matcher.group();
    String name = labelPattern.getName()
      .replaceAll("\\$\\{PATTERN_GROUP\\}", matched);
    for (int i = 0; i <= matcher.groupCount(); i++) {
     name = name.replaceAll("\\$\\{PATTERN_GROUP_" + i + "\\}", firstNonNull(matcher.group(i), ""));
    }
    labels.add(new ParsedLabel(name));
   }
  }
  return labels;
 }

 private void parseForIssuesInCommit(Map<String, ParsedIssue> foundIssues, GitHubHelper gitHubHelper,
                                     JiraClient jiraClient, List<SettingsIssue> patterns, GitCommit gitCommit,
                                     Set<ParsedLabel> commitLabels) {
  boolean commitMappedToIssue = false;
  for (SettingsIssue issuePattern : patterns) {
   Matcher matcher = compile(issuePattern.getPattern()).matcher(gitCommit.getMessage());
   while (matcher.find()) {
    String matched = matcher.group();
    if (!foundIssues.containsKey(matched)) {
     try {
      ParsedIssue issue;
      if (issuePattern.getType() == GITHUB && gitHubHelper != null
        && gitHubHelper.getIssueFromAll(matched).isPresent()) {
       issue = putGitHubIssue(foundIssues, gitHubHelper, issuePattern, matched);
      } else if (issuePattern.getType() == JIRA && jiraClient != null && jiraClient.getIssue(matched).isPresent()) {
       issue = putJiraIssue(foundIssues, jiraClient, issuePattern, matched);
      } else {
       issue = putCustomIssue(foundIssues, issuePattern, matcher, matched);
      }
      for (ParsedLabel label: commitLabels) {
       label.addIssue(issue);
      }
     } catch (Exception e) {
      LOG.error("Will ignore issue \"" + matched + "\"", e);
     }
    }
    if (foundIssues.containsKey(matched)) {
     // When an exception was thrown above, the matched key may not have been
     // added
     foundIssues.get(matched).addCommit(gitCommit);
     commitMappedToIssue = true;
    }
   }
  }
  if (!commitMappedToIssue) {
   ParsedIssue noIssue = new ParsedIssue(settings.getNoIssueName(), null, null);
   if (!foundIssues.containsKey(noIssue.getName())) {
    foundIssues.put(noIssue.getName(), noIssue);
   }
   foundIssues.get(noIssue.getName()).addCommit(gitCommit);
  }
 }

 private ParsedIssue putGitHubIssue(Map<String, ParsedIssue> foundIssues, GitHubHelper gitHubHelper,
                                    SettingsIssue issuePattern, String matched) throws GitChangelogIntegrationException {
  GitHubIssue gitHubIssue = gitHubHelper.getIssueFromAll(matched).get();
  ParsedIssue parsedIssue = new ParsedIssue(//
    issuePattern.getName(),//
    gitHubIssue.getTitle(), //
    matched,//
    gitHubIssue.getLink());
  foundIssues.put(matched, parsedIssue);
  return parsedIssue;
 }

 private ParsedIssue putJiraIssue(Map<String, ParsedIssue> foundIssues, JiraClient jiraClient, SettingsIssue issuePattern,
                                  String matched) throws GitChangelogIntegrationException {
  JiraIssue jiraIssue = jiraClient.getIssue(matched).get();
  ParsedIssue parsedIssue = new ParsedIssue(//
    issuePattern.getName(),//
    jiraIssue.getTitle(), //
    matched,//
    jiraIssue.getLink());
  foundIssues.put(matched, parsedIssue);
  return parsedIssue;
 }

 private ParsedIssue putCustomIssue(Map<String, ParsedIssue> foundIssues, SettingsIssue issuePattern, Matcher matcher,
                                    String matched) {
  String link = issuePattern.getLink().or("") //
    .replaceAll("\\$\\{PATTERN_GROUP\\}", matched);
  for (int i = 0; i <= matcher.groupCount(); i++) {
   link = link.replaceAll("\\$\\{PATTERN_GROUP_" + i + "\\}", firstNonNull(matcher.group(i), ""));
  }
  ParsedIssue parsedIssue = new ParsedIssue(//
    issuePattern.getName(),//
    matched,//
    link);
  foundIssues.put(matched, parsedIssue);
  return parsedIssue;
 }

 public static class Pair<T1, T2> {
  public final T1 first;
  public final T2 second;

  public Pair(T1 first, T2 second) {
   this.first = first;
   this.second = second;
  }
 }
}
