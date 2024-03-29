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

//CPD-OFF
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Serializes Hashtables.
 */
// TODO: why not use a map serialiser?
public class DictionarySerializer extends AbstractSerializer {
  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(//
      Hashtable.class, Dictionary.class); // NOPMD

  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONObject.class);

  private static final Set<String> SUPPORTED_JAVA_CLASSES = SERIALIZABLE_CLASSES.stream().map((
      c) -> c.getName()).collect(Collectors.toSet());

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (super.canSerialize(clazz, jsonClazz) || ((jsonClazz == null
        || jsonClazz == JSONObject.class) && Dictionary.class.isAssignableFrom(clazz)));
  }

  @Override
  public Collection<Class<?>> getJSONClasses() {
    return JSON_CLASSES;
  }

  @Override
  public Collection<Class<?>> getSerializableClasses() {
    return SERIALIZABLE_CLASSES;
  }

  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    Dictionary<?, ?> ht = (Dictionary<?, ?>) o;
    JSONObject obj = new JSONObject();
    JSONObject mapdata = new JSONObject();

    marshallHints(obj, o);
    try {
      obj.put("map", state.push(o, mapdata, "map"));
      state.getProcessedObject(mapdata).setSerialized(mapdata);
    } catch (JSONException e) {
      throw new MarshallException("Could not put data" + e.getMessage(), e);
    }
    Object key = null;

    try {
      Enumeration<?> en = ht.keys();
      while (en.hasMoreElements()) {
        key = en.nextElement();
        if (key == null) {
          continue;
        }
        String keyString = key.toString(); // only support String keys

        Object json = ser.marshall(state, mapdata, ht.get(key), keyString);
        mapdata.put(keyString, json);
      }
    } catch (JSONException | MarshallException e) {
      throw new MarshallException("map key " + key + " " + e.getMessage(), e);
    } finally {
      state.pop();
    }
    return obj;
  }

  // TODO: try unMarshall and unMarshall share 90% code. Put in into an
  // intermediate function.
  // TODO: Also cache the result somehow so that an unmarshall
  // following a tryUnmarshall doesn't do the same work twice!
  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONObject jso = (JSONObject) o;
    String javaClass;
    try {
      javaClass = jso.getString(JSONSerializer.JAVA_CLASS_FIELD);
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read javaClass", e);
    }
    if (javaClass == null) {
      throw new UnmarshallException("no type hint");
    }
    if (!SUPPORTED_JAVA_CLASSES.contains(javaClass)) {
      throw new UnmarshallException("not a Dictionary");
    }
    JSONObject jsonmap;
    try {
      jsonmap = jso.getJSONObject("map");
    } catch (JSONException e) {
      throw new UnmarshallException("map missing", e);
    }
    if (jsonmap == null) {
      throw new UnmarshallException("map missing");
    }
    ObjectMatch m = new ObjectMatch(-1);
    state.setSerialized(o, m);

    Iterator<String> i = jsonmap.keys();
    String key = null;
    try {
      while (i.hasNext()) {
        key = i.next();
        m.setMismatch(ser.tryUnmarshall(state, null, jsonmap.get(key)).max(m).getMismatch());
      }
    } catch (JSONException | UnmarshallException e) {
      throw new UnmarshallException("key " + key + " " + e.getMessage(), e);
    }

    return m;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONObject jso = (JSONObject) o;
    String javaClass;
    try {
      javaClass = jso.getString(JSONSerializer.JAVA_CLASS_FIELD);
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read javaClass", e);
    }
    if (javaClass == null) {
      throw new UnmarshallException("no type hint");
    }
    if (!SUPPORTED_JAVA_CLASSES.contains(javaClass)) {
      throw new UnmarshallException("not a Dictionary");
    }
    Hashtable<String, Object> ht = new Hashtable<String, Object>(); // NOPMD
    JSONObject jsonmap;
    try {
      jsonmap = jso.getJSONObject("map");
    } catch (JSONException e) {
      throw new UnmarshallException("map missing", e);
    }
    if (jsonmap == null) {
      throw new UnmarshallException("map missing");
    }

    state.setSerialized(o, ht);

    Iterator<String> i = jsonmap.keys();
    String key = null;
    try {
      while (i.hasNext()) {
        key = i.next();
        ht.put(key, ser.unmarshall(state, null, jsonmap.get(key)));
      }
    } catch (JSONException | UnmarshallException e) {
      throw new UnmarshallException("key " + key + " " + e.getMessage(), e);
    }
    return ht;
  }
}
