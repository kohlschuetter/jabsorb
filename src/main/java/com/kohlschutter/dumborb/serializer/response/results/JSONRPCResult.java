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
package com.kohlschutter.dumborb.serializer.response.results;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.dumborb.JSONSerializer;

/**
 * Container for a JSON-RPC result message. This includes successful results, error results, and
 * remote exceptions results.
 */
public abstract class JSONRPCResult {

  /**
   * The id of the response.
   */
  private final Object id;

  /**
   * Creates a new JSONRPCResult without fixups (for backward compatibility to json-rpc and
   * json-rpc-java.
   *
   * @param id The id of the response.
   */
  public JSONRPCResult(Object id) {
    this.id = id;
  }

  /**
   * Creates a object for a result.
   *
   * @return A JSONObject which will be sent to the client
   * @throws JSONException If a problem occurs creating the object.
   */
  protected abstract JSONObject createOutput() throws JSONException;

  /**
   * CreateOuput implementation for this class.
   *
   * @return An object which will be sent to the client.
   * @throws JSONException If a problem occurs creating the object.
   */
  protected JSONObject createOutput0() throws JSONException {
    JSONObject o = new JSONObject();
    o.put(JSONSerializer.ID_FIELD, id);
    return o;
  }

  /**
   * Gets the id of the response.
   *
   * @return the id of the response.
   */
  public Object getId() {
    return id;
  }

  /**
   * Converts the result to a JSON string, optionally adding a {@code serverURL} parameter,
   * indicating that the receiving JSON-RPC client should switch over to using that new URL for
   * subsequent calls.
   *
   * The new serverURL can either be a fully qualified URL or a relative URL, such as
   * "/json?someArgs=123". In the latter case, the URL is to be updated relative to the current URL.
   *
   * @param newServerURL The new serverURL to be used by the client.
   * @return a string representation of this JSON object.
   * @throws JSONException on error.
   */
  public String toJSONString(String newServerURL) throws JSONException {
    JSONObject obj = createOutput();
    if (newServerURL != null) {
      obj.put("serverURL", newServerURL);
    }
    return obj.toString();
  }

  @Override
  public String toString() {
    return toJSONString(null);
  }
}
