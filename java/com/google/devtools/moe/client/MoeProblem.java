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

package com.google.devtools.moe.client;

/**
 * A problem that we do not expect to routinely happen. They should end execution of MOE and require
 * intervention by moe-team.
 */
public class MoeProblem extends RuntimeException {
  // https://www.youtube.com/watch?v=xZ4tNmnuMgQ

  // TODO(cgruber): Figure out why this is public mutable and fix.
  public String explanation;
  private final Object[] args;

  // TODO(cgruber): Check not null and ensure no one is calling it that way.
  public MoeProblem(String explanation, Object... args) {
    this.explanation = explanation;
    this.args = args;
  }

  @Override
  public String getMessage() {
    return String.format(explanation, args);
  }
}
