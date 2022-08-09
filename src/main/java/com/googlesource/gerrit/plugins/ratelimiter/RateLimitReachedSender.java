// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.ratelimiter;

import com.google.common.io.CharStreams;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.send.EmailArguments;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;

public class RateLimitReachedSender extends OutgoingEmail {
  public interface Factory {
    RateLimitReachedSender create(IdentifiedUser user, String emailmessage, boolean acquirePermit);
  }

  private final IdentifiedUser user;
  private final String emailMessage;
  private final MessageIdGenerator messageIdGenerator;
  private final Configuration configuration;
  private final boolean acquirePermit;

  @AssistedInject
  public RateLimitReachedSender(
      EmailArguments args,
      MessageIdGenerator messageIdGenerator,
      Configuration configuration,
      @Assisted IdentifiedUser user,
      @Assisted String emailMessage,
      @Assisted boolean acquirePermit) {
    super(args, "RateLimitReached");
    this.messageIdGenerator = messageIdGenerator;
    this.configuration = configuration;
    this.acquirePermit = acquirePermit;
    this.user = user;
    this.emailMessage = emailMessage;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] " + emailMessage);
    setMessageId(
        messageIdGenerator.fromReasonAccountIdAndTimestamp(
            "rate_limit_reached", user.getAccountId(), TimeUtil.now()));
    add(RecipientType.TO, user.getAccountId());
  }

  @Override
  protected boolean shouldSendMessage() {
    return user.getEffectiveGroups().containsAnyOf(configuration.getRecipients());
  }

  @Override
  protected void format() throws EmailException {
    appendText(soyUseTextTemplate("RateLimiterEmailFormat"));
    if (useHtml()) {
      appendHtml(soyUseHtmlTemplate("RateLimiterEmailFormatHTML"));
    }
  }

  private String soyUseTextTemplate(String template) {
    SoySauce.Renderer renderer = getRenderer(template);
    return renderer.renderText().get();
  }

  private String soyUseHtmlTemplate(String template) {
    SoySauce.Renderer renderer = getRenderer(template);
    return renderer.renderHtml().get().toString();
  }

  private SoySauce.Renderer getRenderer(String template) {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    String content;

    try (Reader r =
        new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(
                    this.getClass().getResourceAsStream("/" + template + ".soy"))))) {
      content = CharStreams.toString(r);
    } catch (IOException err) {
      throw new ProvisionException(
          "Failed to read template file " + "/resources/" + template + ".soy", err);
    }
    builder.add(content, "/" + template + ".soy");
    String renderedTemplate =
        new StringBuilder("com.googlesource.gerrit.plugins.ratelimiter.")
            .append(template)
            .append(".")
            .append(template)
            .toString();
    SoySauce.Renderer renderer =
        builder.build().compileTemplates().renderTemplate(renderedTemplate).setData(soyContext);
    return renderer;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("userNameEmail", getUserNameEmailFor(user.getAccountId()));
    soyContextEmailData.put("log", emailMessage);
  }

  private String getEmail() {
    return user.getAccount().preferredEmail();
  }
}
