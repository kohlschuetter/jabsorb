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
package com.kohlschutter.dumborb.serializer.request.flat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.dumborb.serializer.request.RequestParser;
import com.kohlschutter.dumborb.serializer.response.flat.FlatSerializerState;

/**
 * Reads a "flattened" json message, in which all objects exist as seperate keys on the main object,
 * to allow circular references, eg: {"result":"_1","_1":{"circRef":"_1"}}.
 *
 * @author William Becker
 */
public class FlatRequestParser extends RequestParser {
  /**
   * Parses an array.
   *
   * @author William Becker
   */
  private static final class FlatParser {
    /**
     * The indexes of objects that have been read already mapped to the objects.
     */
    private final Map<String, JSONObject> parsedObjects;

    /**
     * Creates a new ArrayParser.
     */
    public FlatParser() {
      parsedObjects = new HashMap<String, JSONObject>();
    }

    /**
     * Gets an object by its index.
     *
     * @param index The index of the object
     * @param jsonReq The object which maps indexes to objects
     * @return The object requested
     * @throws JSONException If the json cannot be read
     */
    public JSONObject getObject(String index, JSONObject jsonReq) throws JSONException {
      if (this.parsedObjects.containsKey(index)) {
        return this.parsedObjects.get(index);
      }
      Object o = jsonReq.get(index);
      if (isObjectIndex(o)) {
        return getObject0((String) o, jsonReq);
      }
      return getObject0(index, jsonReq);
    }

    /**
     * Reads an array with (potentially) indexes which point to objects and returns a properly
     * constructed array.
     *
     * @param array The array to parse
     * @param jsonReq The main object that contains indexes mapped to objects
     * @return An array that has all objects constructed
     * @throws JSONException If the json is improperly constructed
     */
    public JSONArray parseArray(JSONArray array, JSONObject jsonReq) throws JSONException {
      for (int i = 0; i < array.length(); i++) {
        Object o = array.get(i);
        if (isObjectIndex(o)) {
          array.put(i, getObject((String) o, jsonReq));
        } else if (o instanceof JSONArray) {
          array.put(i, parseArray((JSONArray) o, jsonReq));
        }
      }
      return array;
    }

    /**
     * Gets an object by its index.
     *
     * @param index The index of the object
     * @param jsonReq The object which maps indexes to objects
     * @return The object requested
     * @throws JSONException If the json cannot be read
     */
    private JSONObject getObject0(String index, JSONObject jsonReq) throws JSONException {
      if (this.parsedObjects.containsKey(index)) {
        return this.parsedObjects.get(index);
      }
      JSONObject o = jsonReq.getJSONObject(index);
      this.parsedObjects.put(index, o);
      Map<String, Object> newObjects = new TreeMap<String, Object>();

      for (Object key : o.keySet()) {
        String k = (String) key;
        Object v = o.get(k);
        if (isObjectIndex(v)) {
          Object ob = getObject((String) v, jsonReq);
          newObjects.put(k, ob);
        } else if (v instanceof JSONArray) {
          newObjects.put(k, parseArray((JSONArray) v, jsonReq));
        }
      }
      for (Entry<String, Object> e : newObjects.entrySet()) {
        o.put(e.getKey(), e.getValue());
      }
      return o;
    }

  }

  /**
   * Checks if the given object is an index which is mapped to an object.
   *
   * @param o The object to test
   * @return Whether the object is an index
   */
  public static boolean isObjectIndex(Object o) {
    if (o instanceof String) {
      String s = (String) o;
      if (s.startsWith(FlatSerializerState.INDEX_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Object unmarshall(final JSONObject object, final String key) throws JSONException {
    Object value = object.get(key);
    if (isObjectIndex(value)) {
      return this.unmarshallObject(object, (String) value);
    }
    return super.unmarshall(object, key);
  }

  @Override
  public JSONArray unmarshallArray(final JSONObject jsonReq, final String key)
      throws JSONException {
    return new FlatParser().parseArray(jsonReq.getJSONArray(key), jsonReq);
  }

  @Override
  public JSONObject unmarshallObject(JSONObject jsonReq, String key) throws JSONException {
    return new FlatParser().getObject(key, jsonReq);
  }
}
