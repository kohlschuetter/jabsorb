package org.jabsorb.client;

import java.net.URI;

/**
 * A factory used to create transport sessions. Register with #registerTransport.
 */
public interface SessionFactory {
  /**
   * Creates the new session
   *
   * @param uri URI used to open this session
   * @return The new session
   */
  Session newSession(URI uri);
}