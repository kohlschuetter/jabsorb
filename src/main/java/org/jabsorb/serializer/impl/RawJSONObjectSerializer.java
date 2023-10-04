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
package org.jabsorb.serializer.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Formats the Java JSONObject object.
 */
public class RawJSONObjectSerializer extends AbstractSerializer {
  /**
   * Classes that this can serialise.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(JSONObject.class);

  /**
   * Classes that this can serialise to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONObject.class);

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
    JSONObject jsonIn = (JSONObject) o;
    JSONObject jsonOut = new JSONObject();
    String key = null;
    try {
      Iterator<String> i = jsonIn.keys();
      while (i.hasNext()) {
        key = i.next();

        Object j = ser.marshall(state, o, jsonIn.opt(key), key);
        jsonOut.put(key, j);
      }
    } catch (JSONException | MarshallException e) {
      throw new MarshallException("JSONObject key " + key + " " + e.getMessage(), e);
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
