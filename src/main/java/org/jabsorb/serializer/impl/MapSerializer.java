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
// CPD-OFF

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * Serializes Maps.
 */
// TODO: if this serializes a superclass does it need to also specify the subclasses?
public class MapSerializer extends AbstractSerializer {
  private static final Map<Class<?>, Supplier<Map<String, Object>>> CLASS_TO_CONSTRUCTOR =
      new HashMap<>();
  private static final Map<String, Supplier<Map<String, Object>>> CLASSNAME_TO_CONSTRUCTOR =
      new HashMap<>();

  static {
    registerClass(Map.class, HashMap::new);
    registerClass(AbstractMap.class, HashMap::new);
    registerClass(HashMap.class, HashMap::new); // NOPMD
    registerClass(LinkedHashMap.class, LinkedHashMap::new); // NOPMD
    registerClass(TreeMap.class, TreeMap::new); // NOPMD
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

  private static <T extends Map<?, ?>> void registerClass(Class<T> klazz,
      Supplier<Map<String, Object>> constructor) {
    CLASS_TO_CONSTRUCTOR.put(klazz, constructor);
    CLASSNAME_TO_CONSTRUCTOR.put(klazz.getName(), constructor);
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (super.canSerialize(clazz, jsonClazz) || ((jsonClazz == null
        || jsonClazz == JSONObject.class) && Map.class.isAssignableFrom(clazz)));
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
    Map<?, ?> map = (Map<?, ?>) o;
    JSONObject obj = new JSONObject();
    JSONObject mapdata = new JSONObject();
    marshallHints(obj, o);
    try {
      obj.put("map", state.push(o, mapdata, "map"));
      state.getProcessedObject(mapdata).setSerialized(mapdata);
    } catch (JSONException e) {
      throw new MarshallException("Could not add map to object: " + e.getMessage(), e);
    }
    Object key = null;
    try {
      Iterator<?> i = map.entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry<?, ?> ent = (Map.Entry<?, ?>) i.next();
        if (ent == null) {
          continue;
        }
        key = ent.getKey();
        final String keyString;
        if (key == null) {
          keyString = "null";
        } else {
          keyString = key.toString(); // only support String keys
        }

        Object json = ser.marshall(state, mapdata, ent.getValue(), keyString);

        mapdata.put(keyString, json);
      }
    } catch (JSONException | MarshallException e) {
      throw new MarshallException("map key " + key + " " + e.getMessage(), e);
    } finally {
      state.pop();
    }
    return obj;
  }

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
      throw new UnmarshallException("not a Map");
    }
    JSONObject jsonmap;
    try {
      jsonmap = jso.getJSONObject("map");
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read map: " + e.getMessage(), e);
    }
    if (jsonmap == null) {
      throw new UnmarshallException("map missing");
    }
    ObjectMatch m = new ObjectMatch(-1);
    Iterator<String> i = jsonmap.keys();
    String key = null;
    state.setSerialized(o, m);
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

    Supplier<Map<String, Object>> supp = CLASSNAME_TO_CONSTRUCTOR.get(javaClass);
    if (supp == null) {
      throw new UnmarshallException("not a Map");
    }

    Map<String, Object> abmap = supp.get();

    JSONObject jsonmap;
    try {
      jsonmap = jso.getJSONObject("map");
    } catch (JSONException e) {
      throw new UnmarshallException("Could not read map: " + e.getMessage(), e);
    }
    if (jsonmap == null) {
      throw new UnmarshallException("map missing");
    }
    state.setSerialized(o, abmap);
    Iterator<String> i = jsonmap.keys();
    String key = null;
    try {
      while (i.hasNext()) {
        key = i.next();
        abmap.put(key, ser.unmarshall(state, null, jsonmap.get(key)));
      }
    } catch (JSONException | UnmarshallException e) {
      throw new UnmarshallException("key " + key + " " + e.getMessage(), e);
    }

    return abmap;
  }

}
