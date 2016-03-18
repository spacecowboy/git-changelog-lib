package se.bjurr.gitchangelog.internal.settings;

import se.bjurr.gitchangelog.api.model.CommitLabel;

import static com.google.common.base.Preconditions.checkNotNull;

public class SettingsLabel {
 /**
  * Name of the label. This is the {@link CommitLabel#getName()}. For example "Bug" or "Feature".
  * It supports variables like:<br>
  * <code>${PATTERN_GROUP}</code><br>
  * <code>${PATTERN_GROUP_1}</code><br>
  */
 private final String name;
 /**
  * Regular expression that is evaluated in commit comment.
  */
 private final String pattern;

 public SettingsLabel(String name, String pattern) {
  this.name = checkNotNull(name, "name");
  this.pattern = checkNotNull(pattern, "pattern");
 }

 public String getName() {
  return name;
 }

 public String getPattern() {
  return pattern;
 }
}
