/*
 * dumborb - a Java to JavaScript Advanced Object Request Broker
 *
 * Copyright 2022-2023 Christian Kohlschütter
 *
 * based on jabsorb Copyright 2007-2009 The jabsorb team
 * based on original code from
 * JSON-RPC-Java - a JSON-RPC to Java Bridge with dynamic invocation
 * Copyright Metaparadigm Pte. Ltd. 2004.
 * Michael Clark <michael@metaparadigm.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kohlschutter.dumborb.client;

import java.util.Set;

import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.ResourceFactory;

import com.kohlschutter.dumborb.JSONRPCBridge;
import com.kohlschutter.dumborb.JSONRPCServlet;
import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.test.ITest;

import junit.framework.TestCase;

/**
 * Test case that requires starting the jabsorb server
 */
public abstract class ServerTestBase extends TestCase {
  private static final JSONRPCBridge BRIDGE = new JSONRPCBridge(ClassResolver.withAllowedClasses(Set
      .of(ITest.Wiggle.class, ITest.Waggle.class)));

  /**
   * Encapsulate Jetty hosting server initialization so that we could start it only once during the
   * test run
   */
  static class ServerContext {
    public Server server;
    public WebAppContext context;
    public int port;

    public ServerContext() throws Exception {
      port = 8083;
      BRIDGE.registerObject("test", new com.kohlschutter.dumborb.test.Test());
      server = new Server(port);
      context = new WebAppContext(ResourceFactory.root().newResource(ServerContext.class
          .getResource("")), JABSORB_CONTEXT);
      ServletHolder jsonRpcServlet = new ServletHolder(new JSONRPCServlet(BRIDGE));
      // Based on the patch by http://code.google.com/u/cameron.taggart/
      // located at http://code.google.com/p/json-rpc-client/issues/detail?id=1
      jsonRpcServlet.setInitParameter("auto-session-bridge", "0");
      context.addServlet(jsonRpcServlet, "/*");
      context.setServer(server);
      server.setHandler(context);
      server.start();
    }
  }

  static ServerContext serverContext;

  static final String JABSORB_CONTEXT = "/jabsorb-trunk";

  @Override
  protected void setUp() throws Exception {
    // Prevent multiple startups of the server
    if (serverContext == null) {
      serverContext = new ServerContext();
    }
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public String getServiceRootURL() {
    return "http://localhost:" + Integer.toString(serverContext.port) + JABSORB_CONTEXT;
  }

}
