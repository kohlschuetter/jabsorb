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
package org.jabsorb.client.async;

import java.io.Closeable;
import java.util.concurrent.Future;

import org.json.JSONObject;

/**
 * An {@link AsyncClient} session.
 *
 * @author matthijs
 */
public interface AsyncSession extends Closeable {
  /**
   * Asynchronously sends the given JSON request.
   *
   * @param request The request.
   * @return The response future.
   */
  Future<JSONObject> send(JSONObject request);

  /**
   * Asynchronously sends the given JSON request, using the given callback.
   *
   * @param request The request.
   * @param callback the callback.
   * @return The response future.
   */
  Future<JSONObject> send(JSONObject request,
      AsyncResultCallback<AsyncSession, JSONObject, JSONObject> callback);

  @Override
  void close();
}
