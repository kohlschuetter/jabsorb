/*
 * jabsorb - a Java to JavaScript Advanced Object Request Broker
 * http://www.jabsorb.org
 *
 * Copyright 2007-2009 The jabsorb team
 *
 * based on original code from
 * JSON-RPC-Java - a JSON-RPC to Java Bridge with dynamic invocation
 *
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
package org.jabsorb.client;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry of transports serving JSON-RPC-Client
 */
public class TransportRegistry {
  /**
   * Maps schemes (eg "http") to session factories
   */
  private final Map<String, SessionFactory> registry;

  /**
   * Creates a new TransportRegistry
   */
  public TransportRegistry() {
    this.registry = new HashMap<String, SessionFactory>();
  }

  /**
   * Create a session from 'uriString' using one of registered transports.
   *
   * @param uriString The uri of the session
   * @return a URLConnectionSession
   */
  public Session createSession(String uriString) {
    try {
      URI uri = new URI(uriString);
      SessionFactory found = registry.get(uri.getScheme());
      if (found != null) {
        return found.newSession(uri);
      }
      // Fallback
      return new URLConnectionSession(uri.toURL());
    } catch (Exception e) {
      throw new ClientError(e);
    }
  }

  /**
   * Register a factory for a transport type
   *
   * @param scheme The transport type
   * @param factory The session factory for the scheme
   */
  public void registerTransport(String scheme, SessionFactory factory) {
    registry.put(scheme, factory);
  }
}
