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
package com.kohlschutter.dumborb.serializer.impl;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;

import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Responsible for serializing Java arrays.
 */
public class ArraySerializer extends AbstractSerializer {
  /**
   * The classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = //
      Set.of(int[].class, short[].class, long[].class, float[].class, double[].class,
          boolean[].class, Integer[].class, Short[].class, Long[].class, Float[].class,
          Double[].class, Boolean[].class, String[].class);

  /**
   * The class that this serializes to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONArray.class);

  private static final Map<Class<?>, ArrayMarshaller<?>> MARSHAL_MAP = new HashMap<>();

  private static final Map<Class<?>, ArrayUnmarshaller<?>> UNMARSHAL_MAP = new HashMap<>();

  @Override
  public Collection<Class<?>> getSerializableClasses() {
    return SERIALIZABLE_CLASSES;
  }

  @Override
  public Collection<Class<?>> getJSONClasses() {
    return JSON_CLASSES;
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    Class<?> cc = clazz.getComponentType();
    return (super.canSerialize(clazz, jsonClazz) || ((jsonClazz == null
        || jsonClazz == JSONArray.class) && (clazz.isArray() && !cc.isPrimitive()))
        || (clazz == java.lang.Object.class && jsonClazz == JSONArray.class));
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONArray jso = (JSONArray) o;
    Class<?> cc = clazz.getComponentType();
    int i = 0;
    ObjectMatch m = new ObjectMatch(-1);
    state.setSerialized(o, m);
    try {
      for (; i < jso.length(); i++) {
        m.setMismatch(ser.tryUnmarshall(state, cc, jso.get(i)).max(m).getMismatch());
      }
    } catch (UnmarshallException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage(), e);
    } catch (JSONException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage()
          + " not found in json object", e);
    }
    return m;
  }

  @FunctionalInterface
  private interface ArrayUnmarshaller<T> {
    T unmarshal(JSONSerializer ser, SerializerState state, Class<?> componentType, JSONArray jso)
        throws UnmarshallException;
  }

  private static <T> void registerUnmarshaller(Class<T> returnType,
      ArrayUnmarshaller<T> unmarshaller) {
    UNMARSHAL_MAP.put(returnType, unmarshaller);
  }

  static {
    registerUnmarshaller( //
        int[].class, (ser, state, cc, jso) -> {
          int[] arr = new int[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).intValue();
          }
          return arr;
        });
    //
    registerUnmarshaller( //
        byte[].class, (ser, state, cc, jso) -> {
          byte[] arr = new byte[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).byteValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        short[].class, (ser, state, cc, jso) -> {
          short[] arr = new short[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).shortValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        long[].class, (ser, state, cc, jso) -> {
          long[] arr = new long[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).longValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        float[].class, (ser, state, cc, jso) -> {
          float[] arr = new float[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).floatValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        double[].class, (ser, state, cc, jso) -> {
          double[] arr = new double[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Number) ser.unmarshall(state, cc, jso.get(i))).doubleValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        char[].class, (ser, state, cc, jso) -> {
          char[] arr = new char[jso.length()];
          state.setSerialized(jso, arr); // FIXME why was this missing in the original code?
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((String) ser.unmarshall(state, cc, jso.get(i))).charAt(0);
          }
          return arr;
        });
    registerUnmarshaller( //
        boolean[].class, (ser, state, cc, jso) -> {
          boolean[] arr = new boolean[jso.length()];
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ((Boolean) ser.unmarshall(state, cc, jso.get(i))).booleanValue();
          }
          return arr;
        });
    registerUnmarshaller( //
        Object[].class, (ser, state, cc, jso) -> {
          Object[] arr = (Object[]) Array.newInstance(cc, jso.length());
          state.setSerialized(jso, arr);
          for (int i = 0; i < arr.length; i++) {
            arr[i] = ser.unmarshall(state, cc, jso.get(i));
          }
          return arr;
        });
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONArray jso = (JSONArray) o;
    Class<?> cc = clazz.getComponentType();
    int i = 0;
    try {
      ArrayUnmarshaller<?> aum = UNMARSHAL_MAP.get(clazz);
      if (aum == null) {
        aum = UNMARSHAL_MAP.get(Object[].class);
      }
      return aum.unmarshal(ser, state, cc, jso);
    } catch (UnmarshallException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage(), e);
    } catch (JSONException e) {
      throw new UnmarshallException("element " + i + " " + e.getMessage()
          + " not found in json object", e);
    }
  }

  static {
    registerMarshaller(int[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(long[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(short[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(byte[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(float[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(double[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(char[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(boolean[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        arr.put(a[i]);
      }
    });
    registerMarshaller(Object[].class, (ser, state, a, arr) -> {
      for (int i = 0; i < a.length; i++) {
        Object json = ser.marshall(state, a, a[i], i);
        arr.put(json);
      }
    });
  }

  private static <T> void registerMarshaller(Class<T> returnType, ArrayMarshaller<T> marshaller) {
    MARSHAL_MAP.put(returnType, marshaller);
  }

  @FunctionalInterface
  interface ArrayMarshaller<T> {
    void marshal(JSONSerializer ser, SerializerState state, T source, JSONArray target)
        throws MarshallException;

    default void marshal(JSONSerializer ser, SerializerState state, Object source,
        Class<T> sourceType, JSONArray target) throws MarshallException {
      marshal(ser, state, sourceType.cast(source), target);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    try {
      JSONArray arr = new JSONArray();
      Class<?> type = o.getClass();
      ArrayMarshaller<?> marshaller = MARSHAL_MAP.get(type);
      if (marshaller == null) {
        type = Object[].class;
        marshaller = MARSHAL_MAP.get(type);
      }
      marshaller.marshal(ser, state, o, (Class) type, arr);
      return arr;
    } catch (JSONException e) {
      throw new MarshallException(e.getMessage() + " threw json exception", e);
    }
  }
}
