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
package com.kohlschutter.dumborb.serializer.response.fixups;

import java.util.List;

import com.kohlschutter.dumborb.serializer.MarshallException;

/**
 * Use this class to make fixups for non-primitive duplicates.
 *
 * @author William Becker
 */
public class FixupNonPrimitiveDupesOnly extends FixupDupesOnly {
  @Override
  public Object duplicateFound(List<Object> originalLocation, Object ref, Object java)
      throws MarshallException {
    if (!isPrimitive(java)) {
      return super.duplicateFound(originalLocation, ref, java);
    }
    return null;
  }
}
