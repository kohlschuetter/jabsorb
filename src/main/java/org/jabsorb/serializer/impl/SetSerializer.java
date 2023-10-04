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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jabsorb.JSONSerializer;
import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Serializes Sets
 *
 * TODO: if this serializes a superclass does it need to also specify the subclasses?
 */
public class SetSerializer extends AbstractSerializer {
  private static final Map<Class<?>, Supplier<Set<Object>>> CLASS_TO_CONSTRUCTOR = new HashMap<>();
  private static final Map<String, Supplier<Set<Object>>> CLASSNAME_TO_CONSTRUCTOR =
      new HashMap<>();
  static {
    registerClass(Set.class, HashSet::new);
    registerClass(AbstractSet.class, HashSet::new);
    registerClass(HashSet.class, HashSet::new); // NOPMD
    registerClass(LinkedHashSet.class, LinkedHashSet::new); // NOPMD
    registerClass(TreeSet.class, TreeSet::new); // NOPMD
  }
  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Collections.unmodifiableSet(
      CLASS_TO_CONSTRUCTOR.keySet());

  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONObject.class);

  private static final Set<String> SUPPORTED_JAVA_CLASSES = SERIALIZABLE_CLASSES.stream().map((
      c) -> c.getName()).collect(Collectors.toSet());

  private static <T extends Set<?>> void registerClass(Class<T> klazz,
      Supplier<Set<Object>> constructor) {
    CLASS_TO_CONSTRUCTOR.put(klazz, constructor);
    CLASSNAME_TO_CONSTRUCTOR.put(klazz.getName(), constructor);
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (super.canSerialize(clazz, jsonClazz) || ((jsonClazz == null
        || jsonClazz == JSONObject.class) && Set.class.isAssignableFrom(clazz)));
  }

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
    Set<?> set = (Set<?>) o;

    JSONObject obj = new JSONObject();
    JSONObject setdata = new JSONObject();
    marshallHints(obj, o);
    try {
      obj.put("set", state.push(o, setdata, "set"));
      state.getProcessedObject(setdata).setSerialized(setdata);
    } catch (JSONException e) {
      throw new MarshallException("Could not set 'set': " + e.getMessage(), e);
    }
    Object key = null;
    Iterator<?> i = set.iterator();

    try {
      while (i.hasNext()) {
        key = i.next();
        if (key == null) {
          continue;
        }
        String keyString = key.toString(); // only support String keys
        Object json = ser.marshall(state, setdata, key, keyString);
        setdata.put(keyString, json);
      }
    } catch (JSONException | MarshallException e) {
      throw new MarshallException("set key " + key + e.getMessage(), e);
    } finally {
      state.pop();
    }
    return obj;
  }

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
      throw new UnmarshallException("not a Set");
    }
    JSONObject jsonset;
    try {
      jsonset = jso.getJSONObject("set");
    } catch (JSONException e) {
      throw new UnmarshallException("set missing", e);
    }

    if (jsonset == null) {
      throw new UnmarshallException("set missing");
    }

    ObjectMatch m = new ObjectMatch(-1);
    state.setSerialized(o, m);
    Iterator<?> i = jsonset.keys();
    String key = null;

    try {
      while (i.hasNext()) {
        key = (String) i.next();
        m.setMismatch(ser.tryUnmarshall(state, null, jsonset.get(key)).max(m).getMismatch());
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

    Supplier<Set<Object>> supp = CLASSNAME_TO_CONSTRUCTOR.get(javaClass);
    if (supp == null) {
      throw new UnmarshallException("not a Set");
    }

    Set<Object> abset = supp.get();

    JSONObject jsonset;
    try {
      jsonset = jso.getJSONObject("set");
    } catch (JSONException e) {
      throw new UnmarshallException("set missing", e);
    }

    if (jsonset == null) {
      throw new UnmarshallException("set missing");
    }

    Iterator<String> i = jsonset.keys();
    String key = null;
    state.setSerialized(o, abset);
    try {
      while (i.hasNext()) {
        key = i.next();
        Object setElement = jsonset.get(key);
        abset.add(ser.unmarshall(state, null, setElement));
      }
    } catch (UnmarshallException e) {
      throw new UnmarshallException("key " + i + e.getMessage(), e);
    } catch (JSONException e) {
      throw new UnmarshallException("key " + key + " " + e.getMessage(), e);
    }
    return abset;
  }

}
