/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.issue;

import com.google.common.base.Strings;
import org.sonar.api.ServerComponent;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.core.issue.workflow.IssueWorkflow;
import org.sonar.server.user.UserSession;

import java.util.List;
import java.util.Map;

public class TransitionAction extends Action implements ServerComponent {

  public static final String KEY = "do_transition";

  private final IssueWorkflow workflow;

  public TransitionAction(IssueWorkflow workflow) {
    super(KEY);
    this.workflow = workflow;
  }

  @Override
  public boolean verify(Map<String, Object> properties, List<Issue> issues, UserSession userSession) {
    return transition(properties) != null;
  }

  @Override
  public boolean execute(Map<String, Object> properties, Context context) {
    return workflow.doTransition((DefaultIssue) context.issue(), transition(properties), context.issueChangeContext());
  }

  private String transition(Map<String, Object> properties) {
    String param = (String) properties.get("transition");
    if (Strings.isNullOrEmpty(param)) {
      throw new IllegalArgumentException("Missing parameter : 'transition'");
    }
    return param;
  }

}