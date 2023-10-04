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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises java beans that are known to have readable and writable properties
 */
public class BeanSerializer extends AbstractSerializer {
  /**
   * Stores the readable and writable properties for the Bean.
   */
  private static class BeanData {
    // TODO: Legacy comment. WTF?
    // in absence of getters and setters, these fields are
    // public to allow subclasses to access.
    /**
     * The readable properties of the bean.
     */
    private final Map<String, MethodHandle> readableProps;

    /**
     * The writable properties of the bean.
     */
    private final Map<String, MethodHandle> writableProps;

    private final Constructor<?> constructor;

    private BeanData(Constructor<?> constructor, Map<String, MethodHandle> readableProps,
        Map<String, MethodHandle> writableProps) {
      this.constructor = constructor;
      this.readableProps = readableProps;
      this.writableProps = writableProps;
    }
  }

  /**
   * Unique serialisation id.
   */
  private final static long serialVersionUID = 2;

  /**
   * The logger for this class
   */
  private final static Logger log = LoggerFactory.getLogger(BeanSerializer.class);

  /**
   * Caches analysed beans
   */
  private static Map<Class<?>, BeanData> beanCache = new HashMap<Class<?>, BeanData>();

  /**
   * Classes that this can serialise.
   *
   * TODO: Yay for bloat!
   */
  private static Class<?>[] _serializableClasses = new Class[] {};

  /**
   * Classes that this can serialise to.
   *
   * TODO: Yay for bloat!
   */
  private static Class<?>[] _JSONClasses = new Class[] {};

  /**
   * Analyses a bean, returning a BeanData with the data extracted from it.
   *
   * @param clazz The class of the bean to analyse
   * @return A populated BeanData
   * @throws IntrospectionException If a problem occurs during getting the bean info.
   * @throws IllegalAccessException
   * @throws NoSuchMethodException
   */
  public static BeanData analyzeBean(Class<?> clazz) throws IntrospectionException,
      IllegalAccessException, NoSuchMethodException {
    log.info("analyzing " + clazz.getName());
    Lookup lookup = MethodHandles.publicLookup();

    Constructor<?> constructor;
    try {
      constructor = clazz.getDeclaredConstructor();
      constructor.newInstance();
    } catch (Exception e) {
      constructor = null;
    }

    BeanInfo beanInfo = Introspector.getBeanInfo(clazz, Object.class);
    PropertyDescriptor props[] = beanInfo.getPropertyDescriptors();

    Map<String, MethodHandle> readableProps = new HashMap<String, MethodHandle>();
    Map<String, MethodHandle> writableProps = new HashMap<String, MethodHandle>();

    for (int i = 0; i < props.length; i++) {
      // This is declared by enums and shouldn't be shown.
      if (props[i].getName().equals("declaringClass")) {
        // FIXME generalize, and check that this is an enum class
        continue;
      }
      Method writeMethod = props[i].getWriteMethod();
      if (writeMethod != null) {
        Class<?> param[] = writeMethod.getParameterTypes();
        if (param.length != 1) {
          throw new IntrospectionException("bean " + clazz.getName() + " method " + writeMethod
              .getName() + " does not have exactly one arg");
        }

        MethodHandle mh = lookup.unreflect(writeMethod);
        writableProps.put(props[i].getName(), mh);
      }

      Method readMethod = props[i].getReadMethod();
      if (readMethod != null) {
        readableProps.put(props[i].getName(), lookup.unreflect(readMethod));
      }
    }
    return new BeanData(constructor, readableProps, writableProps);
  }

  /**
   * Gets the bean data from cache if possible, otherwise analyses the bean.
   *
   * @param clazz The class of the bean to analyse
   * @return A populated BeanData
   * @throws IntrospectionException If a problem occurs during getting the bean info.
   */
  public static BeanData getBeanData(Class<?> clazz) throws IntrospectionException {
    BeanData bd;
    synchronized (beanCache) {
      bd = beanCache.get(clazz);
      if (bd == null) {
        try {
          bd = analyzeBean(clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
          throw (IntrospectionException) new IntrospectionException(e.toString()).initCause(e);
        }
        beanCache.put(clazz, bd);
      }
    }
    return bd;
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (!clazz.isArray() && !clazz.isPrimitive() && !clazz.isInterface() && (jsonClazz == null
        || jsonClazz == JSONObject.class));
  }

  @Override
  public Class<?>[] getJSONClasses() {
    return _JSONClasses;
  }

  @Override
  public Class<?>[] getSerializableClasses() {
    return _serializableClasses;
  }

  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    BeanData bd;
    try {
      bd = getBeanData(o.getClass());
    } catch (IntrospectionException e) {
      throw new MarshallException(o.getClass().getName() + " is not a bean", e);
    }

    JSONObject val = new JSONObject();
    marshallHints(val, o);
    Object result;
    for (Map.Entry<String, MethodHandle> ent : bd.readableProps.entrySet()) {
      String prop = ent.getKey();
      MethodHandle getMethod = ent.getValue();
      if (log.isDebugEnabled()) {
        log.debug("invoking getter " + prop + " " + getMethod + "()");
      }
      try {
        result = getMethod.invoke(o);
      } catch (Throwable e) {
        if (e instanceof InvocationTargetException) {
          e = ((InvocationTargetException) e).getTargetException();
        }
        throw new MarshallException("bean " + o.getClass().getName() + " can't invoke getter:"
            + prop + " " + getMethod + ": " + e.getMessage(), e);
      }
      try {
        if (result != null || ser.getMarshallNullAttributes()) {
          try {
            Object json = ser.marshall(state, o, result, prop);
            val.put(prop, json);
          } catch (JSONException e) {
            throw new MarshallException("JSONException: " + e.getMessage(), e);
          }
        }
      } catch (MarshallException e) {
        throw new MarshallException("bean " + o.getClass().getName() + " " + e.getMessage(), e);
      }
    }

    return val;
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONObject jso = (JSONObject) o;
    BeanData bd;
    try {
      bd = getBeanData(clazz);
    } catch (IntrospectionException e) {
      throw new UnmarshallException(clazz.getName() + " is not a bean", e);
    }

    int match = 0;
    int mismatch = 0;
    for (String prop : bd.writableProps.keySet()) {
      if (jso.has(prop)) {
        match++;
      } else {
        mismatch++;
      }
    }
    if (match == 0) {
      throw new UnmarshallException("bean has no matches");
    }

    // create a concrete ObjectMatch that is always returned in order to satisfy circular reference
    // requirements
    ObjectMatch returnValue = new ObjectMatch(-1);
    state.setSerialized(o, returnValue);

    ObjectMatch m = null;
    ObjectMatch tmp;
    for (String field : jso.keySet()) {
      MethodHandle setMethod = bd.writableProps.get(field);
      if (setMethod != null) {
        try {
          tmp = ser.tryUnmarshall(state, setMethod.type().parameterType(1), jso.get(field));
          if (tmp != null) {
            if (m == null) {
              m = tmp;
            } else {
              m = m.max(tmp);
            }
          }
        } catch (UnmarshallException e) {
          throw new UnmarshallException("bean " + clazz.getName() + " " + e.getMessage(), e);
        } catch (JSONException e) {
          throw new UnmarshallException("bean " + clazz.getName() + " " + e.getMessage(), e);
        }
      } else {
        mismatch++;
      }
    }
    if (m != null) {
      returnValue.setMismatch(m.max(new ObjectMatch(mismatch)).getMismatch());
    } else {
      returnValue.setMismatch(mismatch);
    }
    return returnValue;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONObject jso = (JSONObject) o;
    BeanData bd;
    try {
      bd = getBeanData(clazz);
    } catch (IntrospectionException e) {
      throw new UnmarshallException(clazz.getName() + " is not a bean", e);
    }
    if (log.isDebugEnabled()) {
      log.debug("instantiating " + clazz.getName());
    }

    Object instance;
    try {
      instance = Objects.requireNonNull(bd.constructor).newInstance();
    } catch (InstantiationException | InvocationTargetException | IllegalAccessException
        | RuntimeException e) {
      throw new UnmarshallException("could not instantiate bean of type " + clazz.getName()
          + ", make sure it has a no argument " + "constructor and that it is not an interface or "
          + "abstract class", e);
    }

    if (instance instanceof Throwable) {
      // do not expose internals
      ((Throwable) instance).setStackTrace(new StackTraceElement[0]);
    }

    state.setSerialized(o, instance);
    Object fieldVal;
    for (String field : jso.keySet()) {
      MethodHandle setMethod = bd.writableProps.get(field);
      if (setMethod != null) {
        try {
          fieldVal = ser.unmarshall(state, setMethod.type().parameterType(1), jso.get(field));
        } catch (UnmarshallException e) {
          throw new UnmarshallException("could not unmarshall field \"" + field + "\" of bean "
              + clazz.getName(), e);
        } catch (JSONException e) {
          throw new UnmarshallException("could not unmarshall field \"" + field + "\" of bean "
              + clazz.getName(), e);
        }
        if (log.isDebugEnabled()) {
          log.debug("invoking setter " + field + " " + setMethod + "(" + fieldVal + ")");
        }
        try {
          setMethod.invoke(instance, fieldVal);
        } catch (Throwable e) {
          if (e instanceof InvocationTargetException) {
            e = ((InvocationTargetException) e).getTargetException();
          }
          throw new UnmarshallException("bean " + clazz.getName() + "can't invoke setter " + field
              + " " + setMethod + ": " + e.getMessage(), e);
        }
      }
    }
    return instance;
  }
}
