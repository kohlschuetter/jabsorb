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

import java.util.Arrays;

import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.test.ITest;

/**
 * This test implements some of Jabsorb tests.
 */
public class ClientTestCase extends ServerTestBase {
  final ClassResolver classResolver = ClassResolver.withDefaults();

  TransportRegistry registry;

  @Override
  protected void setUp() throws Exception {
    super.setUp(); // Makes sure jabsorb server tests are running at this URL

    registry = new TransportRegistry();
  }

  TransportRegistry getRegistry() {
    if (registry == null)
      registry = new TransportRegistry(); // Standard registry by default
    return registry;
  }

  /**
   * Test for invalid URL
   */
  public void testBadClient() {
    Client badClient = new Client(registry.createSession("http://non-existing-server:99"),
        classResolver);
    try {
      ITest badTest = badClient.openProxy("test", ITest.class);
      badTest.voidFunction();
      fail();
    } catch (ClientException err) {
      // Cool, we got error!
    }
  }

  public void testStandardSession() {
    Client client = new Client(getRegistry().createSession(getServiceRootURL() + "/JSON-RPC"),
        classResolver);
    ITest test = client.openProxy("test", ITest.class);
    basicClientTest(test);
  }

  HTTPSession newHTTPSession(String url) {
    try {
      TransportRegistry reg = getRegistry();
      // Note: HTTPSession is not registered by default. Normally you would
      // register during initialization. In this test, we are testing different
      // states of the registry, hence we register it here and clean up afterwards
      HTTPSession.register(reg);
      // Note: will not work without registering HTTPSession, see #setUp()
      return (HTTPSession) getRegistry().createSession(url);
    } finally {
      // Modified the registry; let's clean up after ourselves. Next call
      // to getRegistry will create a new one
      registry = null;
    }
  }

  public void testHTTPSession() {
    Client client = new Client(newHTTPSession(getServiceURL()), classResolver);
    ITest test = client.openProxy("test", ITest.class);
    basicClientTest(test);
  }

  void basicClientTest(ITest test) {
    test.voidFunction();
    assertEquals("hello", test.echo("hello"));
    assertEquals(1234, test.echo(1234));
    int[] ints = {1, 2, 3};
    assertTrue(Arrays.equals(ints, test.echo(ints)));
    String[] strs = {"foo", "bar", "baz"};
    assertTrue(Arrays.equals(strs, test.echo(strs)));
    ITest.Wiggle wiggle = new ITest.Wiggle();
    assertEquals(wiggle.toString(), test.echo(wiggle).toString());
    ITest.Waggle waggle = new ITest.Waggle(1);
    assertEquals(waggle.toString(), test.echo(waggle).toString());
    assertEquals('?', test.echoChar('?'));
    Integer into = 1234567890;
    assertEquals(into, test.echoIntegerObject(into));
    Long longo = 1099511627776L;
    assertEquals(longo, test.echoLongObject(longo));
    Float floato = 3.3F;
    assertEquals(floato, test.echoFloatObject(floato));
    Double doublo = 3.1415926;
    assertEquals(doublo, test.echoDoubleObject(doublo));
  }

  // // TODO run embedded proxy server (is Jetty capable of working like a proxy?) to really test
  // // proxy.
  // // Right now, we are just testing that the proxy parameters are being set
  // public void testProxyConfiguration() {
  // HTTPSession proxiedSession = newHTTPSession(getServiceURL());
  // int proxyPort = 40888; // hopefully, the port is unused
  // proxiedSession.setProxy("localhost", proxyPort);
  // Client client = new Client(proxiedSession, classResolver);
  // ITest proxyObject = client.openProxy("test", ITest.class);
  // try {
  // proxyObject.voidFunction();
  // } catch (ClientException ex) {
  // if (!(ex.getCause() instanceof ConnectException))
  // fail("expected ConnectException, got " + ex.getCause().getClass().getName());
  // }
  // }

  String getServiceURL() {
    return getServiceRootURL() + "/JSON-RPC";
  }
}
