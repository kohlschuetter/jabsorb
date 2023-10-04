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
package com.kohlschutter.dumborb.serializer.impl;

import java.util.Collection;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;

import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Formats the Java JSONArray object.
 */
public class RawJSONArraySerializer extends AbstractSerializer {
  /**
   * Classes that this can serialise.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(JSONArray.class);

  /**
   * Classes that this can serialise to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONArray.class);

  @Override
  public Collection<Class<?>> getSerializableClasses() {
    return SERIALIZABLE_CLASSES;
  }

  @Override
  public Collection<Class<?>> getJSONClasses() {
    return JSON_CLASSES;
  }

  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    // reprocess the raw json in order to fixup circular references and duplicates
    JSONArray jsonIn = (JSONArray) o;
    JSONArray jsonOut = new JSONArray();

    int i = 0;
    try {
      int j = jsonIn.length();

      for (i = 0; i < j; i++) {
        Object json = ser.marshall(state, o, jsonIn.opt(i), i);
        jsonOut.put(i, json);
      }
    } catch (JSONException | MarshallException e) {
      throw new MarshallException("element " + i, e);
    }
    return jsonOut;
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    state.setSerialized(jso, ObjectMatch.OKAY);
    return ObjectMatch.OKAY;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    state.setSerialized(jso, jso);
    return jso;
  }
}
