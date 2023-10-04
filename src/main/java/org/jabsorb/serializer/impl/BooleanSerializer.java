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
import java.util.Set;

import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;

/**
 * Serialiess Boolean values
 */
public class BooleanSerializer extends AbstractSerializer {
  private static final String FALSE = "false";

  private static final String TRUE = "true";

  /**
   * Classes that this can serialise.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of(boolean.class,
      Boolean.class);

  /**
   * Classes that this can serialise to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of(Boolean.class, String.class);

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
    return o;
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    final ObjectMatch toReturn;
    if (jso instanceof String) {
      // TODO: Boolean parses stuff as ignoreCase(x)=="true" as true or
      // anything else as false. I'm pretty sure in this case it this should
      // only be javascript true or false strings, because otherwise
      // this will catch string passed to it.
      switch ((String) jso) {
        case TRUE:
        case FALSE:
          toReturn = ObjectMatch.OKAY;
          break;
        default:
          toReturn = ObjectMatch.ROUGHLY_SIMILAR;
          break;
      }
    } else if (jso instanceof Boolean) {
      toReturn = ObjectMatch.OKAY;
    } else {
      toReturn = ObjectMatch.ROUGHLY_SIMILAR;
    }
    state.setSerialized(jso, toReturn);
    return toReturn;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object jso)
      throws UnmarshallException {
    Boolean returnValue = Boolean.FALSE;

    if (jso instanceof String) {
      try {
        returnValue = Boolean.valueOf((String) jso);
      } catch (Exception e) {
        throw new UnmarshallException("Cannot convert " + jso + " to Boolean", e);
      }
    } else if (jso instanceof Boolean || clazz == boolean.class) {
      returnValue = (Boolean) jso;
    }

    state.setSerialized(jso, returnValue);
    return returnValue;
  }
}
