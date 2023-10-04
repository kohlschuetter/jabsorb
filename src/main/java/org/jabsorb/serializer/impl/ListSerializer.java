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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jabsorb.JSONSerializer;
import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Serializes lists.
 *
 * TODO: if this serializes a superclass does it need to also specify the subclasses?
 */
public class ListSerializer extends AbstractSerializer {
  private static final Map<Class<?>, Supplier<List<Object>>> CLASS_TO_CONSTRUCTOR = new HashMap<>();
  private static final Map<String, Supplier<List<Object>>> CLASSNAME_TO_CONSTRUCTOR =
      new HashMap<>();
  static {
    registerClass(List.class, ArrayList::new);
    registerClass(AbstractList.class, ArrayList::new);
    registerClass(ArrayList.class, ArrayList::new); // NOPMD

    registerClass(LinkedList.class, LinkedList::new); // NOPMD
    registerClass(Vector.class, Vector::new); // NOPMD
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

  private static <T extends List<?>> void registerClass(Class<T> klazz,
      Supplier<List<Object>> constructor) {
    CLASS_TO_CONSTRUCTOR.put(klazz, constructor);
    CLASSNAME_TO_CONSTRUCTOR.put(klazz.getName(), constructor);
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (super.canSerialize(clazz, jsonClazz) || ((jsonClazz == null
        || jsonClazz == JSONObject.class) && List.class.isAssignableFrom(clazz)));
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
    List<?> list = (List<?>) o;
    JSONObject obj = new JSONObject();
    JSONArray arr = new JSONArray();

    marshallHints(obj, o);
    try {
      obj.put("list", state.push(o, arr, "list"));
      state.getProcessedObject(arr).setSerialized(arr);
    } catch (JSONException e) {
      throw new MarshallException("Error setting list: " + e, e);
    }
    int index = 0;
    try {
      Iterator<?> i = list.iterator();
      while (i.hasNext()) {
        Object json = ser.marshall(state, arr, i.next(), index);
        arr.put(json);
        index++;
      }
    } catch (MarshallException e) {
      throw new MarshallException("element " + index, e);
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
  @SuppressWarnings("PMD.CyclomaticComplexity")
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
      throw new UnmarshallException("not a List");
    }
    JSONArray jsonlist;
    try {
      jsonlist = jso.getJSONArray("list");
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read list: " + e.getMessage(), e);
    }
    if (jsonlist == null) {
      throw new UnmarshallException("list missing");
    }
    int i = 0;
    ObjectMatch m = new ObjectMatch(-1);
    state.setSerialized(o, m);
    try {
      for (; i < jsonlist.length(); i++) {
        m.setMismatch(ser.tryUnmarshall(state, null, jsonlist.get(i)).max(m).getMismatch());
      }
    } catch (JSONException | UnmarshallException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage(), e);
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

    Supplier<List<Object>> supp = CLASSNAME_TO_CONSTRUCTOR.get(javaClass);
    if (supp == null) {
      throw new UnmarshallException("not a List");
    }
    List<Object> al = supp.get();

    JSONArray jsonlist;
    try {
      jsonlist = jso.getJSONArray("list");
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read list: " + e.getMessage(), e);
    }
    if (jsonlist == null) {
      throw new UnmarshallException("list missing");
    }
    state.setSerialized(o, al);
    int i = 0;
    try {
      for (; i < jsonlist.length(); i++) {
        al.add(ser.unmarshall(state, null, jsonlist.get(i)));
      }
    } catch (JSONException | UnmarshallException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage(), e);
    }
    return al;
  }
}
