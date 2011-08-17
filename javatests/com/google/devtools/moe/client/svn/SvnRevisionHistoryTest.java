// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.devtools.moe.client.svn;

import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.database.EquivalenceMatcher;
import com.google.devtools.moe.client.repositories.Revision;
import com.google.devtools.moe.client.repositories.RevisionMatcher;
import com.google.devtools.moe.client.repositories.RevisionMetadata;
import com.google.devtools.moe.client.testing.AppContextForTesting;
import com.google.devtools.moe.client.testing.DummyDb;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;

import java.util.List;

import javax.imageio.metadata.IIOMetadataNode;

/**
 * @author dbentley@google.com
 *
 */
public class SvnRevisionHistoryTest extends TestCase {

  public void testParseRevisions() {
    List<Revision> rs = SvnRevisionHistory.parseRevisions(
        "<log><logentry revision=\"1\"/></log>",
        "name");
    assertEquals(1, rs.size());
    assertEquals("1", rs.get(0).revId);
  }

  public void testGetHighestRevision() {
    AppContextForTesting.initForTest();
    IMocksControl control = EasyMock.createControl();
    CommandRunner cmd = control.createMock(CommandRunner.class);
    AppContext.RUN.cmd = cmd;

    try {
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "1", "-r", "HEAD:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"3\" /></log>");
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "1", "-r", "2:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"2\" /></log>");
    } catch (CommandException e) {
      throw new RuntimeException(e);
    }
    control.replay();
    SvnRevisionHistory history = new SvnRevisionHistory("internal_svn",
        "http://foo/svn/trunk/");
    Revision result = history.findHighestRevision("");
    assertEquals(result.revId, "3");

    result = history.findHighestRevision("2");
    assertEquals(result.revId, "2");
    control.verify();
  }

  public void testParseMetadata() {
    SvnRevisionHistory history = new SvnRevisionHistory("internal_svn",
        "http://foo/svn/trunk/");
    List<RevisionMetadata> rs = history.parseMetadata(
        "<log><logentry revision=\"2\"><author>uid@google.com</author>" +
        "<date>yyyy-mm-dd</date><msg>description</msg></logentry>" +
        "<logentry revision = \"1\"><author>user@google.com</author>" +
        "<date>zzzz-nn-ee</date><msg>message</msg></logentry></log>");
    assertEquals(2, rs.size());
    assertEquals("2", rs.get(0).id);
    assertEquals("uid@google.com", rs.get(0).author);
    assertEquals("yyyy-mm-dd", rs.get(0).date);
    assertEquals("description", rs.get(0).description);
    assertEquals(ImmutableList.of(new Revision("1", "internal_svn")), rs.get(0).parents);
    assertEquals("1", rs.get(1).id);
    assertEquals("user@google.com", rs.get(1).author);
    assertEquals("zzzz-nn-ee", rs.get(1).date);
    assertEquals("message", rs.get(1).description);
    assertEquals(ImmutableList.of(), rs.get(1).parents);
  }

  public void testGetMetadata() {
    AppContextForTesting.initForTest();
    IMocksControl control = EasyMock.createControl();
    CommandRunner cmd = control.createMock(CommandRunner.class);
    AppContext.RUN.cmd = cmd;

    try {
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "2", "-r", "3:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"3\">" +
                             "<author>uid@google.com</author>" +
                             "<date>yyyy-mm-dd</date>" +
                             "<msg>message</msg></logentry>" +
                             "<logentry revision =\"2\">" +
                             "<author>user@google.com</author>" +
                             "<date>zzzz-nn-ee</date>" +
                             "<msg>description</msg></logentry></log>");
    } catch (CommandException e) {
      throw new RuntimeException(e);
    }
    control.replay();
    SvnRevisionHistory history = new SvnRevisionHistory("internal_svn",
        "http://foo/svn/trunk/");
    RevisionMetadata result = history.getMetadata(new Revision("3", "internal_svn"));
    assertEquals("3", result.id);
    assertEquals("uid@google.com", result.author);
    assertEquals("yyyy-mm-dd", result.date);
    assertEquals("message", result.description);
    assertEquals(ImmutableList.of(new Revision("2", "internal_svn")), result.parents);
    control.verify();
  }
  
  public void testParseMetadataNodeList() {
    SvnRevisionHistory history = new SvnRevisionHistory("internal_svn",
    "http://foo/svn/trunk/");
    
    IIOMetadataNode nodelist = new IIOMetadataNode();
    
    IIOMetadataNode child = new IIOMetadataNode("author");
    child.setTextContent("user");
    nodelist.appendChild(child);
    
    // The XML parser often throws in random child nodes like this.
    child = new IIOMetadataNode("#text");
    nodelist.appendChild(child);
    
    child = new IIOMetadataNode("date");
    child.setTextContent("yyyy-mm-dd");
    nodelist.appendChild(child);
    
    child = new IIOMetadataNode("msg");
    child.setTextContent("description");
    nodelist.appendChild(child);
    
    RevisionMetadata result = history.parseMetadataNodeList("7", nodelist, 
        ImmutableList.of(new Revision("6", "internal")));
    
    RevisionMetadata expected = new RevisionMetadata("7", "user", "yyyy-mm-dd", "description", 
        ImmutableList.of(new Revision("6", "internal")));
    
    assertEquals(result, expected);
  }

  public void testFindNewRevisions() {
    AppContextForTesting.initForTest();
    IMocksControl control = EasyMock.createControl();
    CommandRunner cmd = control.createMock(CommandRunner.class);
    AppContext.RUN.cmd = cmd;
    DummyDb db = new DummyDb(false);

    // Mock call for findHighestRevision
    try {
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "1", "-r", "HEAD:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"3\">" +
                             "<author>uid@google.com</author>" +
                             "<date>yyyy-mm-dd</date>" +
                             "<msg>description</msg></logentry></log>");
    } catch (CommandException e) {
      throw new RuntimeException(e);
    }

    // revision 3 metadata
    try {
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "2", "-r", "3:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"3\">" +
                             "<author>uid@google.com</author>" +
                             "<date>yyyy-mm-dd</date>" +
                             "<msg>message</msg></logentry>" +
                             "<logentry revision =\"2\">" +
                             "<author>user@google.com</author>" +
                             "<date>zzzz-nn-ee</date>" +
                             "<msg>description</msg></logentry></log>");
    } catch (CommandException e) {
      throw new RuntimeException(e);
    }


    // revision 2 metadata
    try {
      expect(cmd.runCommand(
          "svn",
          ImmutableList.of("--no-auth-cache", "log", "--xml", "-l", "2", "-r", "2:1",
                           "http://foo/svn/trunk/"),
          "", "")).andReturn("<log><logentry revision=\"2\">" +
                             "<author>uid@google.com</author>" +
                             "<date>yyyy-mm-dd</date>" +
                             "<msg>description</msg></logentry></log>");
    } catch (CommandException e) {
      throw new RuntimeException(e);
    }

    control.replay();
    SvnRevisionHistory history = new SvnRevisionHistory("internal_svn",
        "http://foo/svn/trunk/");
    RevisionMatcher matcher = new EquivalenceMatcher("public", db);
    ImmutableList<Revision> newRevisions = ImmutableList.copyOf(
        history.findRevisions(null, matcher));
    assertEquals(2, newRevisions.size());
    assertEquals("internal_svn", newRevisions.get(0).repositoryName);
    assertEquals("3", newRevisions.get(0).revId);
    assertEquals("internal_svn", newRevisions.get(1).repositoryName);
    assertEquals("2", newRevisions.get(1).revId);
    control.verify();
  }
}