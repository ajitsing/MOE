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

import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.codebase.CodebaseCreationError;
import com.google.devtools.moe.client.migrations.Migrator;
import com.google.devtools.moe.client.parser.Parser;
import com.google.devtools.moe.client.parser.Parser.ParseError;
import com.google.devtools.moe.client.parser.RepositoryExpression;
import com.google.devtools.moe.client.project.ProjectContextFactory;
import com.google.devtools.moe.client.project.RepositoryConfig;
import com.google.devtools.moe.client.project.ScrubberConfig;
import com.google.devtools.moe.client.repositories.RepositoryType;
import com.google.devtools.moe.client.repositories.Revision;
import com.google.devtools.moe.client.repositories.RevisionMetadata;
import com.google.devtools.moe.client.writer.DraftRevision;
import com.google.devtools.moe.client.writer.Writer;
import com.google.devtools.moe.client.writer.WritingError;

import org.kohsuke.args4j.Option;

import java.util.List;

import javax.inject.Inject;

/**
 * Perform a single migration using command line flags.
 */
public class OneMigrationDirective extends Directive {
  @Option(
    name = "--from_repository",
    required = true,
    usage = "Repository expression to migrate from, e.g. 'internal(revision=3,4,5)'"
  )
  String fromRepository = "";

  @Option(
    name = "--to_repository",
    required = true,
    usage = "Repository expression to migrate to, e.g. 'public(revision=7)'"
  )
  String toRepository = "";

  private final Ui ui;
  private final DraftRevision.Factory revisionFactory;
  private final Migrator migrator;

  @Inject
  OneMigrationDirective(
      ProjectContextFactory contextFactory,
      Ui ui,
      DraftRevision.Factory revisionFactory,
      Migrator migrator) {
    super(contextFactory); // TODO(cgruber) Inject project context, not its factory
    this.ui = ui;
    this.revisionFactory = revisionFactory;
    this.migrator = migrator;
  }

  @Override
  protected int performDirectiveBehavior() {
    RepositoryExpression toRepoEx, fromRepoEx;
    try {
      toRepoEx = Parser.parseRepositoryExpression(toRepository);
      fromRepoEx = Parser.parseRepositoryExpression(fromRepository);
    } catch (ParseError e) {
      ui.error(e, "Couldn't parse expression");
      return 1;
    }
    RepositoryConfig repositoryConfig =
        context().config().getRepositoryConfig(toRepoEx.getRepositoryName());
    String toProjectSpace = repositoryConfig.getProjectSpace();
    List<Revision> revs = Revision.fromRepositoryExpression(fromRepoEx, context());

    Codebase sourceCodebase;
    try {
      sourceCodebase =
          new RepositoryExpression(fromRepoEx.getRepositoryName())
              .atRevision(revs.get(0).revId())
              .translateTo(toProjectSpace)
              .createCodebase(context());
    } catch (CodebaseCreationError e) {
      ui.error(e, "Error creating codebase");
      return 1;
    }

    Writer destination;
    try {
      destination = toRepoEx.createWriter(context());
    } catch (WritingError e) {
      ui.error(e, "Error writing to repo");
      return 1;
    }

    ui.info("Migrating '%s' to '%s'", fromRepoEx, toRepoEx);


    RepositoryType repositoryType = context.getRepository(revs.get(0).repositoryName());

    RevisionMetadata metadata =
        migrator.processMetadata(repositoryType.revisionHistory(), revs, null, revs.get(0));
    ScrubberConfig scrubber =
        context
            .config()
            .findScrubberConfig(fromRepoEx.getRepositoryName(), toRepoEx.getRepositoryName());
    metadata = migrator.possiblyScrubAuthors(metadata, scrubber);
    DraftRevision draftRevision =
        revisionFactory.create(
            sourceCodebase, destination, migrator.possiblyScrubAuthors(metadata, scrubber));

    if (draftRevision == null) {
      return 1;
    }

    ui.info("Created Draft Revision: " + draftRevision.getLocation());
    return 0;
  }

  @Override
  public String getDescription() {
    return "Performs a single migration";
  }
}
