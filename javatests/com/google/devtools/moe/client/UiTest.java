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

import junit.framework.TestCase;

public class UiTest extends TestCase {

  class NoOpUi extends Ui {
    NoOpUi() {
      this.fileSystem = new SystemFileSystem();
    }

    @Override
    public void info(String msg, Object... args) {}

    @Override
    public void error(String msg, Object... args) {}

    @Override
    public void error(Throwable e, String msg, Object... args) {}

    @Override
    public void debug(String msg, Object... args) {}
  }

  public void testStackHelpers() throws Exception {
    Ui ui = new NoOpUi();
    Ui.Task t = ui.pushTask("foo", "bar");
    ui.popTask(t, "");
    assertEquals("bar", t.description);

    t = ui.pushTask("foo", "bar");
    try {
      ui.popTask(new Ui.Task("baz", "quux"), "");
    } catch (MoeProblem expected) {
      return;
    }
    fail("Expected failure");
  }
}
