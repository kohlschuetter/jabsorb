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
package com.kohlschutter.dumborb.serializer;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.JSONSerializer;

/**
 * Convenience class for implementing Serializers providing default setOwner and canSerialize
 * implementations.
 */
public abstract class AbstractSerializer implements Serializer {

  /**
   * Main serialiser.
   */
  protected JSONSerializer ser;

  /**
   * Default check that simply tests the given serializeable class arrays to determine if the pair
   * of classes can be serialized/deserialized from this Serializer.
   *
   * @param clazz Java type to check if this Serializer can handle.
   * @param jsonClazz JSON type to check this Serializer can handle.
   * @return true If this Serializer can serialize/deserialize the given java,json pair.
   */
  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    boolean canJava = false;
    boolean canJSON = false;

    for (Class<?> cl : getSerializableClasses()) {
      if (cl == clazz) {
        canJava = true;
        break;
      }
    }

    if (jsonClazz == null) {
      canJSON = true;
    } else {
      for (Class<?> cl : getJSONClasses()) {
        if (cl == jsonClazz) {
          canJSON = true;
          break;
        }
      }
    }

    return (canJava && canJSON);
  }

  /**
   * Set the JSONSerialiser that spawned this object.
   *
   * @param ser The parent serialiser.
   */
  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public void setOwner(JSONSerializer ser) {
    this.ser = ser;
  }

  /**
   * Marshalls class hints onto an object, if appropriate (ie check
   * <code>getMarshallClassHints()</code>).
   *
   * @param toAddTo The object to add the hints to.
   * @param objectWithClass The object from which the class should be taken
   * @return the object to which the hints were added, for use with chaining.
   * @throws MarshallException If an exception occurs while the hints are added.
   */
  protected JSONObject marshallHints(JSONObject toAddTo, final Object objectWithClass)
      throws MarshallException {
    if (ser.isMarshallClassHints()) {
      try {
        toAddTo.put(JSONSerializer.JAVA_CLASS_FIELD, objectWithClass.getClass().getName());
      } catch (JSONException e) {
        throw new MarshallException("javaClass not found!", e);
      }
    }
    return toAddTo;
  }

}
