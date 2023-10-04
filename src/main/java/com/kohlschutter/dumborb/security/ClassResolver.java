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
package com.kohlschutter.dumborb.security;

import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Controls which classes can get unmarshalled.
 *
 * @author Christian Kohlschütter
 */
public final class ClassResolver {
  private static final Pattern PAT_ARRAY = Pattern.compile("^([\\[]+[L]?)(.*?)([;]*)$");
  private static final Set<String> DEFAULT_ALLOWED_CLASSES = Set.of("java.lang.Exception");

  private final Set<String> allowedClasses;
  private final WeakHashMap<String, Class<?>> cachedDecisions = new WeakHashMap<>();

  private ClassResolver(Set<String> allowedClasses) {
    this.allowedClasses = allowedClasses;
  }

  /**
   * Sentinel class.
   */
  private static final class NotAccessible {
    private NotAccessible() {
      throw new IllegalStateException("No instances");
    }
  }

  public static ClassResolver withDefaults() {
    return withAllowedClassNames(DEFAULT_ALLOWED_CLASSES);
  }

  public static ClassResolver withAllowedClasses(Set<Class<?>> allowedClasses) {
    return new ClassResolver(allowedClasses.stream().map((k) -> k.getName()).collect(Collectors
        .toSet()));
  }

  public static ClassResolver withAllowedClassNames(Set<String> allowedClasses) {
    return new ClassResolver(allowedClasses);
  }

  public Class<?> tryResolve(String className) {
    Class<?> klazz = cachedDecisions.get(className);
    if (klazz == NotAccessible.class) {
      return null;
    } else if (klazz != null) {
      return klazz;
    }

    try {
      if (!allowedClasses.contains(className)) {
        Matcher m = PAT_ARRAY.matcher(className);
        if (!m.find()) {
          return klazz = null;
        }
        String basicClassName = m.group(2);
        if (!allowedClasses.contains(basicClassName)) {
          return klazz = null;
        }
      }

      try {
        klazz = Class.forName(className);
      } catch (ClassNotFoundException e) {
        return klazz = null;
      }

      return klazz;
    } finally {
      cachedDecisions.put(className, klazz == null ? NotAccessible.class : klazz);
    }
  }

  public Class<?> resolveOrThrow(String className) throws UnmarshallException {
    Class<?> klazz = tryResolve(className);
    if (klazz == null) {
      throw new UnmarshallException("Could not resolve class: " + className);
    }
    return klazz;
  }
}
