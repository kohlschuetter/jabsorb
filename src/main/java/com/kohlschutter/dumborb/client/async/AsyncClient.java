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
package com.kohlschutter.dumborb.client.async;

import java.util.HashMap;
import java.util.Map;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.client.ClientException;
import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.serializer.request.fixups.FixupsCircularReferenceHandler;
import com.kohlschutter.dumborb.serializer.response.fixups.FixupCircRefAndNonPrimitiveDupes;

/**
 * A factory to create proxies for access to remote Jabsorb services.
 */
public class AsyncClient {

  /**
   * Maps proxy keys to proxies.
   */
  private final Map<Object, String> proxyMap;

  /**
   * The serializer instance to use.
   */
  private final JSONSerializer serializer;

  /**
   * The transport session to use for this connection.
   */
  private final AsyncSession session;

  /**
   * Create a client given a session.
   *
   * @param session transport session to use for this connection
   */
  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  public AsyncClient(final AsyncSession session, ClassResolver resolver) {
    try {
      this.session = session;
      this.proxyMap = new HashMap<Object, String>();
      // TODO: this might need a better way of initialising it
      this.serializer = new JSONSerializer(FixupCircRefAndNonPrimitiveDupes.class,
          new FixupsCircularReferenceHandler(), resolver);
      this.serializer.registerDefaultSerializers();
    } catch (final Exception e) {
      throw new ClientException(e);
    }
  }

  /**
   * Create a proxy for communicating with the remote service.
   *
   * @param key the remote object key
   * @param klass the class of the interface the remote object should adhere to
   * @return created proxy
   */
  public Object openProxy(final String key, final Class<?> klass) {
    final Object result = java.lang.reflect.Proxy.newProxyInstance(Thread.currentThread()
        .getContextClassLoader(), new Class<?>[] {klass, AsyncProxy.class}, new AsyncProxyHandler(
            key, session, serializer));

    proxyMap.put(result, key);
    return result;
  }

  /**
   * Dispose of the proxy that is no longer needed.
   *
   * @param proxy The proxy to close
   */
  public void closeProxy(final Object proxy) {
    proxyMap.remove(proxy);
  }

  // /**
  // * Allow access to the serializer.
  // *
  // * @return The serializer for this class
  // */
  // public JSONSerializer getSerializer() {
  // return serializer;
  // }
}
