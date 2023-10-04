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
package com.kohlschutter.dumborb.reflect;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.dumborb.JSONRPCBridge;
import com.kohlschutter.dumborb.localarg.LocalArgController;

/**
 * A &quot;factory&quot; for producing ClassData information from Class objects. Gathers the
 * ClassData information via reflection and internally caches it.
 */
public final class ClassAnalyzer {
  private static final Logger LOG = LoggerFactory.getLogger(ClassAnalyzer.class);

  /**
   * Classes that have been analysed.
   *
   * key: Clazz, val ClassData
   */
  private static final Map<Class<?>, ClassData> CLASS_CACHE = new HashMap<Class<?>, ClassData>();

  /**
   * <p>
   * Get ClassData containing information on public methods that can be invoked for a given class.
   * </p>
   * <p>
   * The ClassData will be cached, and multiple calls to getClassData for the same class will return
   * the same cached ClassData object (unless invalidateCache is called to clear the cache.)
   * </p>
   *
   * @param clazz class to get ClassData for.
   *
   * @return ClassData object for the given class.
   */
  public static ClassData getClassData(Class<?> clazz) {
    ClassData cd;
    synchronized (CLASS_CACHE) {
      cd = CLASS_CACHE.get(clazz);
      if (cd == null) {
        cd = analyzeClass(clazz);
        CLASS_CACHE.put(clazz, cd);
      }
    }
    return cd;
  }

  /**
   * Empty the internal cache of ClassData information.
   */
  public static void invalidateCache() {
    CLASS_CACHE.clear();
  }

  /**
   * Analyze a class and create a ClassData object containing all of the public methods (both static
   * and non-static) in the class.
   *
   * @param clazz class to be analyzed.
   *
   * @return a ClassData object containing all the public static and non-static methods that can be
   *         invoked on the class.
   */
  private static ClassData analyzeClass(Class<?> clazz) {
    if (LOG.isInfoEnabled()) {
      LOG.info("analyzing " + clazz.getName());
    }
    final List<AccessibleObject> constructors = new ArrayList<AccessibleObject>(Arrays.asList(clazz
        .getConstructors()));
    final List<AccessibleObject> memberMethods = new ArrayList<AccessibleObject>();
    final List<AccessibleObject> staticMethods = new ArrayList<AccessibleObject>();

    for (Method m : clazz.getMethods()) {
      if (Modifier.isStatic(m.getModifiers())) {
        staticMethods.add(m);
      } else {
        memberMethods.add(m);
      }
    }

    ClassData cd = new ClassData(clazz, createMap(memberMethods, false), createMap(staticMethods,
        false), createMap(constructors, true));

    return cd;
  }

  /**
   * Creates a mapping of AccessibleObjectKey to a Collection which contains all the
   * AccessibleObjects which have the same amount of arguments. This takes into account
   * LocalArgResolvers, discounting them from the argument size.
   *
   * @param accessibleObjects The objects to put into the map
   * @param isConstructor Whether the objects are methods or constructors
   * @return Map of AccessibleObjectKey to a Collection of AccessibleObjects
   */
  private static Map<AccessibleObjectKey, Set<AccessibleObject>> createMap(
      Collection<AccessibleObject> accessibleObjects, boolean isConstructor) {
    final Map<AccessibleObjectKey, Set<AccessibleObject>> map =
        new HashMap<AccessibleObjectKey, Set<AccessibleObject>>();
    for (final AccessibleObject accessibleObject : accessibleObjects) {
      if (!Modifier.isPublic(((Member) accessibleObject).getModifiers())) {
        continue;
      }

      final AccessibleObjectKey accessibleObjectKey;
      {
        // argCount determines the key
        int argCount = 0;
        {
          // The parameters determine the size of argCount
          final Class<?>[] param;
          if (isConstructor) {
            param = ((Constructor<?>) accessibleObject).getParameterTypes();
          } else {
            // If it is a method and the method was defined in Object(), skip
            // it.
            if (((Method) accessibleObject).getDeclaringClass() == Object.class) {
              continue;
            }
            param = ((Method) accessibleObject).getParameterTypes();
          }
          // don't count locally resolved args
          for (Class<?> cl : param) {
            if (LocalArgController.isLocalArg(cl)) {
              continue;
            }
            argCount++;
          }

          if (isConstructor) {
            // Since there is only one constructor name, we don't need to put a
            // name in.
            accessibleObjectKey = new AccessibleObjectKey(JSONRPCBridge.CONSTRUCTOR_FLAG, argCount);
          } else {
            // The key is the methods name and arg count
            accessibleObjectKey = new AccessibleObjectKey(((Method) accessibleObject).getName(),
                argCount);
          }
        }
      }
      Set<AccessibleObject> marr = map.get(accessibleObjectKey);
      if (marr == null) {
        marr = new HashSet<AccessibleObject>();
        map.put(accessibleObjectKey, marr);
      }

      marr.add(accessibleObject);
    }
    return map;
  }
}
