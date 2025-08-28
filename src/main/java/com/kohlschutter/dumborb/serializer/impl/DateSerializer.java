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

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
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
 * Serializes date and time values.
 */
@SuppressWarnings("PMD.ReplaceJavaUtilDate")
public class DateSerializer extends AbstractSerializer {
  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(Date.class,
      Timestamp.class, java.sql.Date.class, Time.class);

  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(JSONObject.class);

  private static final Set<String> SUPPORTED_JAVA_CLASSES = SERIALIZABLE_CLASSES.stream().map((
      c) -> c.getName()).collect(Collectors.toSet());

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
    long time;
    if (o instanceof Date) {
      time = ((Date) o).getTime();
    } else {
      throw new MarshallException("cannot marshall date using class " + o.getClass());
    }
    JSONObject obj = new JSONObject();
    marshallHints(obj, o);
    try {
      obj.put("time", time);
    } catch (JSONException e) {
      throw new MarshallException(e.getMessage(), e);
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
      throw new UnmarshallException("no type hint", e);
    }
    if (javaClass == null) {
      throw new UnmarshallException("no type hint");
    }
    if (!SUPPORTED_JAVA_CLASSES.contains(javaClass)) {
      throw new UnmarshallException("not a Date");
    }
    state.setSerialized(o, ObjectMatch.OKAY);
    return ObjectMatch.OKAY;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    Class<?> realClazz = clazz;
    JSONObject jso = (JSONObject) o;
    long time;
    try {
      time = jso.getLong("time");
    } catch (JSONException e) {
      throw new UnmarshallException("Could not get the time in date serialiser", e);
    }
    if (jso.has(JSONSerializer.JAVA_CLASS_FIELD)) {
      try {
        realClazz = Class.forName(jso.getString(JSONSerializer.JAVA_CLASS_FIELD));
      } catch (ClassNotFoundException e) {
        throw new UnmarshallException(e.getMessage(), e);
      } catch (JSONException e) {
        throw new UnmarshallException("Could not find javaClass", e);
      }
    }
    Object returnValue = null;
    if (Date.class.equals(realClazz)) {
      returnValue = new Date(time);
    } else if (Timestamp.class.equals(realClazz)) {
      returnValue = new Timestamp(time);
    } else if (java.sql.Date.class.equals(realClazz)) {
      returnValue = new java.sql.Date(time);
    } else if (Time.class.equals(realClazz)) {
      returnValue = new Time(time);
    }

    if (returnValue == null) {
      throw new UnmarshallException("invalid class " + realClazz);
    }
    state.setSerialized(o, returnValue);
    return returnValue;
  }

}
