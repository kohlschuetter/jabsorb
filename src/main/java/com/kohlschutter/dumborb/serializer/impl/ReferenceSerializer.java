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
package com.kohlschutter.dumborb.serializer.impl;

import java.util.Collection;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.JSONRPCBridge;
import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.serializer.AbstractSerializer;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.ObjectMatch;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.UnmarshallException;

/**
 * Serializes classes that have been registered on the bridge as references or callable references.
 */
public class ReferenceSerializer extends AbstractSerializer {
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceSerializer.class);

  /**
   * Classes that this can serialize.
   */
  private static final Collection<Class<?>> SERIALIZABLE_CLASSES = Set.of();

  /**
   * Classes that this can serialize to.
   */
  private static final Collection<Class<?>> JSON_CLASSES = Set.of();

  /**
   * A reference to the bridge.
   */
  private final JSONRPCBridge bridge;

  /**
   * Creates a new ReferenceSerializer.
   *
   * @param bridge The bridge to determine if a class is a reference.
   *
   *          TODO: Should reference detection be abstracted out into another class?
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public ReferenceSerializer(JSONRPCBridge bridge) {
    super();
    this.bridge = bridge;
  }

  @Override
  public boolean canSerialize(Class<?> clazz, Class<?> jsonClazz) {
    return (!clazz.isArray() && !clazz.isPrimitive() && !clazz.isInterface() && (bridge.isReference(
        clazz) || bridge.isCallableReference(clazz)) && (jsonClazz == null
            || jsonClazz == JSONObject.class));
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public Collection<Class<?>> getSerializableClasses() {
    return SERIALIZABLE_CLASSES;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public Collection<Class<?>> getJSONClasses() {
    return JSON_CLASSES;
  }

  @Override
  public Object marshall(SerializerState state, Object p, Object o) throws MarshallException {
    Class<?> clazz = o.getClass();
    Integer identity = System.identityHashCode(o);
    if (bridge.isReference(clazz)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("marshalling reference to object " + identity + " of class " + clazz.getName());
      }
      bridge.addReference(o);
      JSONObject jso = new JSONObject();
      try {
        jso.put("JSONRPCType", "Reference");
        jso.put(JSONSerializer.JAVA_CLASS_FIELD, clazz.getName());
        jso.put("objectID", identity);
      } catch (JSONException e) {
        throw new MarshallException(e.getMessage(), e);
      }
      return jso;
    } else if (bridge.isCallableReference(clazz)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("marshalling callable reference to object " + identity + " of class " + clazz
            .getName());
      }
      bridge.registerObject(identity, o);
      bridge.addReference(o);

      JSONObject jso = new JSONObject();
      try {
        // TODO: get rid of these strings.
        jso.put("JSONRPCType", "CallableReference");
        jso.put(JSONSerializer.JAVA_CLASS_FIELD, clazz.getName());
        jso.put("objectID", identity);
      } catch (JSONException e) {
        throw new MarshallException(e.getMessage(), e);
      }

      return jso;
    }
    return null;
  }

  @Override
  public ObjectMatch tryUnmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    state.setSerialized(o, ObjectMatch.OKAY);
    return ObjectMatch.OKAY;
  }

  @Override
  public Object unmarshall(SerializerState state, Class<?> clazz, Object o)
      throws UnmarshallException {
    JSONObject jso = (JSONObject) o;
    Object ref = null;
    String jsonType;
    int objectId;
    try {
      jsonType = jso.getString("JSONRPCType");
      objectId = jso.getInt("objectID");
    } catch (JSONException e) {
      throw new UnmarshallException(e.getMessage(), e);
    }
    if (jsonType != null) {
      switch (jsonType) {
        case "Reference":
        case "CallableReference":
          ref = bridge.getReference(objectId);
          break;
        default:
          break;
      }
    }
    state.setSerialized(o, ref);
    return ref;
  }

}
