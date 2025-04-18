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
package com.kohlschutter.dumborb.serializer.response;

import java.util.List;

import com.kohlschutter.dumborb.serializer.MarshallException;

/**
 * Allows circular references to be signalled when found in Java code.
 *
 * @author William Becker
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CircularReferenceHandler {
  /**
   * Signals that a circular reference was found.
   *
   * @param originalLocation The location where it first appeared
   * @param ref The reference of from the current location where it next appeared
   * @param java The object which is refered to from within itself.
   * @return The object to put in the place of the circular reference in the JSONObject
   * @throws MarshallException May be thrown if a circular reference is found and cannot be handled
   */
  Object circularReferenceFound(List<Object> originalLocation, Object ref, Object java)
      throws MarshallException;
}
