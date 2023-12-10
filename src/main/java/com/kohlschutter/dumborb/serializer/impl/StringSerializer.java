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

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Serializes String values.
 */
public class StringSerializer extends AbstractSerializer {
  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(String.class, char.class,
      Character.class, byte[].class, char[].class);

  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(String.class, Integer.class);

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
    if (o instanceof Character) {
      return o.toString();
    } else if (o instanceof byte[]) {
      return new String((byte[]) o, StandardCharsets.UTF_8);
    } else if (o instanceof char[]) {
      return String.valueOf((char[]) o);
    } else {
      return o;
    }
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    // For some reason getClass can be String but getClasses will return an
    // empty array. This catches this.
    if (jso.getClass().equals(String.class)) {
      return ObjectMatch.OKAY;
    }
    Class<?>[] classes = jso.getClass().getClasses();
    for (Class<?> cl : classes) {
      if (String.class.equals(cl)) {
        state.setSerialized(jso, ObjectMatch.OKAY);
        return ObjectMatch.OKAY;
      }
    }

    state.setSerialized(jso, ObjectMatch.SIMILAR);
    return ObjectMatch.SIMILAR;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    Object returnValue;
    String val = jso instanceof String ? (String) jso : jso.toString();
    if (clazz == char.class) {
      returnValue = val.charAt(0);
    } else if (clazz == byte[].class) {
      returnValue = val.getBytes(StandardCharsets.UTF_8);
    } else if (clazz == char[].class) {
      returnValue = val.toCharArray();
    } else {
      returnValue = val;
    }
    state.setSerialized(jso, returnValue);
    return returnValue;
  }
}
