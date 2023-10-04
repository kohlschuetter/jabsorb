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
package org.jabsorb;

/**
 * Container for objects of which instances have been made.
 */
final class ObjectInstance {
  /**
   * The class the object is of.
   */
  private final Class<?> clazz;

  /**
   * The object for the instance.
   */
  private final Object object;

  /**
   * Creates a new ObjectInstance.
   *
   * @param object The object for the instance.
   */
  ObjectInstance(Object object) {
    this.object = object;
    this.clazz = object.getClass();
  }

  /**
   * Creates a new ObjectInstance.
   *
   * @param object The object for the instance.
   * @param clazz The class the object is of.
   */
  public ObjectInstance(Object object, Class<?> clazz) {
    if (!clazz.isInstance(object)) {
      throw new ClassCastException("Attempt to register jsonrpc object with invalid class.");
    }
    this.object = object;
    this.clazz = clazz;
  }

  /**
   * Gets the class the object is of.
   *
   * @return The class the object is of.
   */
  public Class<?> getClazz() {
    return clazz;
  }

  /**
   * Gets the object for the instance.
   *
   * @return the object for the instance.
   */
  public Object getObject() {
    return object;
  }
}
