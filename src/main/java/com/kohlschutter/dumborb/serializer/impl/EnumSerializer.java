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

import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Serializes enums.
 *
 * @author mingfai
 */
public class EnumSerializer extends AbstractSerializer {
  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(String.class);

  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of();

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return clazz.isEnum();
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
    if (o instanceof Enum) {
      return o.toString();
    }
    return null;
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object json)
      throws UnmarshallException {
    final Class<?>[] classes = json.getClass().getClasses();
    for (Class<?> c : classes) {
      if (c.isEnum()) {
        state.setSerialized(json, ObjectMatch.OKAY);
        return ObjectMatch.OKAY;
      }
    }

    state.setSerialized(json, ObjectMatch.SIMILAR);
    return ObjectMatch.SIMILAR;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object unmarshall(SerializerState state, @SuppressWarnings("rawtypes") Class clazz,
      Object json) throws UnmarshallException {
    String val = json.toString();
    if (clazz.isEnum()) {
      return Enum.valueOf(clazz, val);
    }
    return null;
  }

}
