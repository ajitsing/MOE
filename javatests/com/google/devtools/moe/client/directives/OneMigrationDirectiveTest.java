/*
 * Copyright (c) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.moe.client.directives;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.SystemCommandRunner;
import com.google.devtools.moe.client.migrations.Migrator;
import com.google.devtools.moe.client.repositories.Repositories;
import com.google.devtools.moe.client.repositories.RepositoryType;
import com.google.devtools.moe.client.testing.DummyRepositoryFactory;
import com.google.devtools.moe.client.testing.InMemoryProjectContextFactory;
import com.google.devtools.moe.client.testing.RecordingUi;
import com.google.devtools.moe.client.writer.DraftRevision;

import junit.framework.TestCase;

public class OneMigrationDirectiveTest extends TestCase {
  private final RecordingUi ui = new RecordingUi();
  private final SystemCommandRunner cmd = new SystemCommandRunner(ui);
  private final Repositories repositories =
      new Repositories(ImmutableSet.<RepositoryType.Factory>of(new DummyRepositoryFactory(null)));
  private final InMemoryProjectContextFactory contextFactory =
      new InMemoryProjectContextFactory(null, cmd, null, ui, repositories);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    contextFactory.projectConfigs.put(
        "moe_config.txt",
        "{\"name\":\"foo\",\"repositories\":{"
            + "\"int\":{\"type\":\"dummy\",\"project_space\":\"internal\"},"
            + "\"pub\":{\"type\":\"dummy\"}},"
            + "\"translators\":[{\"from_project_space\":\"internal\","
            + "\"to_project_space\":\"public\",\"steps\":[{\"name\":\"id_step\","
            + "\"editor\":{\"type\":\"identity\"}}]}]}");
  }

  public void testOneMigration() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            contextFactory,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), ui));
    d.setContextFileName("moe_config.txt");
    d.fromRepository = "int(revision=1000)";
    d.toRepository = "pub(revision=2)";
    assertEquals(0, d.perform());
    assertEquals(String.format("Created Draft Revision: %s", "/dummy/revision/pub"), ui.lastInfo);
  }

  public void testOneMigrationFailOnFromRevision() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            contextFactory,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), ui));
    d.setContextFileName("moe_config.txt");
    d.fromRepository = "x(revision=1000)";
    d.toRepository = "pub(revision=2)";
    try {
      d.perform();
      fail("OneMigrationDirective didn't fail on invalid repository 'x'.");
    } catch (MoeProblem expected) {
      assertEquals(
          "No such repository 'x' in the config. Found: [int, pub]", expected.getMessage());
    }
  }

  public void testOneMigrationFailOnToRevision() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            contextFactory,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), ui));
    d.setContextFileName("moe_config.txt");
    d.fromRepository = "int(revision=1000)";
    d.toRepository = "x(revision=2)";
    try {
      d.perform();
      fail("OneMigrationDirective didn't fail on invalid repository 'x'.");
    } catch (MoeProblem expected) {
      assertEquals(
          "No such repository 'x' in the config. Found: [int, pub]", expected.getMessage());
    }
  }
}
