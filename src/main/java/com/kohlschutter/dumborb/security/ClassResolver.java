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

import java.util.Collection;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.dumbo.annotations.DumboSafe;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Controls which classes can get unmarshalled.
 *
 * @author Christian Kohlschütter
 */
public final class ClassResolver {
  private static final Logger LOG = LoggerFactory.getLogger(ClassResolver.class);

  private static final Set<String> DEFAULT_ALLOWED_CLASSES = Set.of("java.lang.Exception");
  private static final Collection<String> DEFAULT_DISALLOWED_PREFIXES = Set.of("javax.", "com.sun.",
      "sun.");

  // JVM's restriction is 65535, probably for Spring
  private static final int MAX_CLASSNAME_LENGTH = 256;

  private final Set<String> allowedClasses;
  private final Collection<String> disallowedPrefixes;

  private final ConcurrentHashMap<String, Class<?>> cachedResults = new ConcurrentHashMap<>();
  private final WeakHashMap<String, Class<?>> cachedResultsWeak = new WeakHashMap<>();

  private ClassResolver(Set<String> allowedClasses, Collection<String> disallowedPrefixes) {
    this.allowedClasses = allowedClasses;
    this.disallowedPrefixes = disallowedPrefixes;
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
    return withClassNames(DEFAULT_ALLOWED_CLASSES, DEFAULT_DISALLOWED_PREFIXES);
  }

  public static ClassResolver withClasses(Set<Class<?>> allowedClasses,
      Collection<String> disallowedPrefixes) {
    return new ClassResolver(allowedClasses.stream().map((k) -> k.getName()).collect(Collectors
        .toSet()), disallowedPrefixes);
  }

  public static ClassResolver withClassNames(Set<String> allowedClasses,
      Collection<String> disallowedPrefixes) {
    return new ClassResolver(allowedClasses, disallowedPrefixes);
  }

//  private static Package packageFromClassName(String className) {
//    String packageName = className;
//    int dollar = packageName.indexOf('$');
//    if (dollar != -1) {
//      packageName = packageName.substring(0, dollar);
//    }
//
//    int lastDot = packageName.lastIndexOf('.');
//    if (lastDot == -1) {
//      return null;
//    }
//    packageName = packageName.substring(0, lastDot);
//
//    Package pkg = Thread.currentThread().getContextClassLoader().getDefinedPackage(packageName);
//    if (pkg == null) { // this happens
//      try {
//        pkg = Class.forName(packageName + ".package-info").getPackage();
//      } catch (ClassNotFoundException ignore) {
//        // pkg = Package.getPackage(packageName);
//      }
//    }
//    return pkg;
//  }

  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
  public Class<?> tryResolve(String className) {
    if (className == null || className.isEmpty()) {
      return null;
    } else if (className.length() > MAX_CLASSNAME_LENGTH) {
      return null;
    }

    Class<?> klazz;

    klazz = cachedResults.get(className);
    if (klazz == null) {
      synchronized (cachedResultsWeak) {
        klazz = cachedResultsWeak.get(className);
      }
    }

    if (klazz == NotAccessible.class) {
      return null;
    } else if (klazz != null) {
      return klazz;
    }

    try {
      boolean knownAllowed = false;
      if (allowedClasses.contains(className)) {
        knownAllowed = true;
      } else {
        int start = 0;
        int end = className.length();
        if (className.charAt(end - 1) == ';') {
          end--;
        }

        while (start < end && (className.charAt(0) == '[')) {
          start++;
        }
        if (start > 0 && className.charAt(start) == 'L') {
          start++;
        }

        if (start >= end) {
          // invalid
          return klazz = null;
        }

        // no default package shenanigans
        if (className.indexOf('.') == -1) {
          return klazz = null;
        }

        String basicClassName = className.substring(start, end);
        if (basicClassName != null && allowedClasses.contains(basicClassName)) {
          knownAllowed = true;
        }
      }

      // no known disallowed packages
      for (String prefix : disallowedPrefixes) {
        if (className.startsWith(prefix)) {
          return klazz = null;
        }
      }

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      try {
        klazz = Class.forName(className, false, classLoader);
      } catch (ClassNotFoundException e) {
        return klazz = null;
      }

      if (klazz == null) {
        return null;
      }

      if (!knownAllowed) {
        if (klazz.isAnnotationPresent(DumboSafe.class)) {
          knownAllowed = true;
        } else {
          return klazz = null;
        }
      }

      return klazz;
    } finally {
      if (klazz == null) {
        if (LOG.isWarnEnabled()) {
          LOG.warn("Marking class '" + className + "' as not resolvable");
        }
        synchronized (cachedResultsWeak) {
          cachedResultsWeak.put(className, NotAccessible.class);
        }
      } else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Marking class '" + className + "' as resolvable");
        }
        cachedResults.put(className, klazz);
      }
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
