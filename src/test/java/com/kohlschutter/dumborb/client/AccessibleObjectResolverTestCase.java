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
package com.kohlschutter.dumborb.client;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;

import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.reflect.AccessibleObjectKey;
import com.kohlschutter.dumborb.reflect.ClassAnalyzer;
import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.serializer.AccessibleObjectResolver;
import com.kohlschutter.dumborb.serializer.request.fixups.FixupsCircularReferenceHandler;
import com.kohlschutter.dumborb.serializer.response.fixups.FixupCircRefAndNonPrimitiveDupes;
import com.kohlschutter.dumborb.test.ConstructorTest;

import junit.framework.TestCase;

public class AccessibleObjectResolverTestCase extends TestCase {
  AccessibleObjectResolver resolver;
  Map<AccessibleObjectKey, Set<AccessibleObject>> methodMap;
  JSONSerializer serializer;

  @Override
  protected void setUp() throws Exception {
    resolver = new AccessibleObjectResolver();
    methodMap = new HashMap<AccessibleObjectKey, Set<AccessibleObject>>();
    methodMap.putAll(ClassAnalyzer.getClassData(ConstructorTest.class).getMethodMap());
    methodMap.putAll(ClassAnalyzer.getClassData(ConstructorTest.class).getConstructorMap());
    serializer = new JSONSerializer(FixupCircRefAndNonPrimitiveDupes.class,
        new FixupsCircularReferenceHandler(), ClassResolver.withDefaults());
    serializer.registerDefaultSerializers();
  }

  public void testResolution() {
    JSONArray args = new JSONArray();
    args.put(1);
    Constructor<?> methodInt = (Constructor<?>) AccessibleObjectResolver.resolveMethod(methodMap,
        "$constructor", args, serializer);
    Class<?>[] params = methodInt.getParameterTypes();
    assertNotNull(params);
    assertEquals(1, params.length);
    assertEquals(Integer.TYPE, params[0]);
  }
}
