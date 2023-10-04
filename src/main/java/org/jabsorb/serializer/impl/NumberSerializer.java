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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;

/**
 * Serialises numeric values
 */
public class NumberSerializer extends AbstractSerializer {
  /**
   * Classes that this can serialise.
   */
  private static Class<?>[] _serializableClasses = new Class[] {
      Integer.class, Byte.class, Short.class, Long.class, Float.class, Double.class, int.class,
      byte.class, short.class, long.class, float.class, double.class, BigDecimal.class,};

  /**
   * Classes that this can serialise to.
   */
  private static Class<?>[] _JSONClasses = new Class[] {
      Integer.class, Byte.class, Short.class, Long.class, Float.class, Double.class, int.class,
      byte.class, short.class, long.class, float.class, double.class, BigDecimal.class,
      String.class};

  @Override
  public Class<?>[] getSerializableClasses() {
    return _serializableClasses;
  }

  @Override
  public Class<?>[] getJSONClasses() {
    return _JSONClasses;
  }

  private static final Map<Class<?>, Function<Object, Object>> TO_NUMBER_MAP = new HashMap<>();

  static {
    registerConverter(Integer.class, int.class, (v) -> v instanceof Number ? ((Number) v).intValue()
        : Integer.parseInt(v.toString()));
    registerConverter(Long.class, long.class, (v) -> v instanceof Number ? ((Number) v).longValue()
        : Integer.parseInt(v.toString()));
    registerConverter(Short.class, short.class, (v) -> v instanceof Number ? ((Number) v)
        .shortValue() : Integer.parseInt(v.toString()));
    registerConverter(Byte.class, byte.class, (v) -> v instanceof Number ? ((Number) v).byteValue()
        : Integer.parseInt(v.toString()));
    registerConverter(Float.class, float.class, (v) -> v instanceof Number ? ((Number) v)
        .floatValue() : Integer.parseInt(v.toString()));
    registerConverter(Double.class, double.class, (v) -> v instanceof Number ? ((Number) v)
        .doubleValue() : Integer.parseInt(v.toString()));
    registerConverter(BigDecimal.class, (v) -> v instanceof BigDecimal ? (BigDecimal) v
        : v instanceof Number ? new BigDecimal(((Number) v).doubleValue()) : new BigDecimal(v
            .toString()));
  }

  private static void registerConverter(Class<?> class1, Function<Object, Object> function) {
    registerConverter(class1, null, function);
  }

  private static void registerConverter(Class<?> class1, Class<?> class2,
      Function<Object, Object> function) {
    TO_NUMBER_MAP.put(class1, function);
    if (class2 != null) {
      TO_NUMBER_MAP.put(class2, function);
    }
  }

  /**
   * Converts a javascript object to a Java number
   *
   * @param clazz The class of the Java object that it should be converted to
   * @param jso The javascript object
   * @return A Java primitive type in its java.lang wrapper.
   * @throws NumberFormatException If clazz is numeric and jso does not parse into a number.
   */
  public Object toNumber(Class<?> clazz, Object jso) throws NumberFormatException {
    Function<Object, Object> function = TO_NUMBER_MAP.get(clazz);
    if (jso == null || function == null) {
      return null;
    } else {
      return function.apply(jso);
    }
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    try {
      toNumber(clazz, jso);
    } catch (NumberFormatException e) {
      throw new UnmarshallException("not a number", e);
    }
    state.setSerialized(jso, ObjectMatch.OKAY);
    return ObjectMatch.OKAY;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    try {
      if (jso == null || "".equals(jso)) {
        return null;
      }
      Object num = toNumber(clazz, jso);
      state.setSerialized(jso, num);
      return num;
    } catch (NumberFormatException e) {
      throw new UnmarshallException("cannot convert object " + jso + " to type " + clazz.getName(),
          e);
    }
  }

  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    return o;
  }
}
