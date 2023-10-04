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
package com.kohlschutter.dumborb.client;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.request.fixups.FixupsCircularReferenceHandler;
import com.kohlschutter.dumborb.serializer.response.fixups.FixupCircRefAndNonPrimitiveDupes;
import com.kohlschutter.dumborb.serializer.response.results.FailedResult;

/**
 * A factory to create proxies for access to remote Jabsorb services.
 */
public class Client {
  private static final String TO_STRING = "toString";

  private static final String EQUALS = "equals";

  private static final String HASH_CODE = "hashCode";

  /**
   * Maintain a unique id for each message.
   */
  private final AtomicInteger id = new AtomicInteger(0);

  /**
   * Maps proxy keys to proxies.
   */
  private final Map<Object, String> proxyMap;

  /**
   * The serializer instance to use.
   */
  private final JSONSerializer serializer;

  /**
   * The transport session to use for this connection.
   */
  private final Session session;

  /**
   * Create a client given a session.
   *
   * @param session transport session to use for this connection
   */
  public Client(Session session, ClassResolver resolver) {
    try {
      this.session = session;
      this.proxyMap = new HashMap<Object, String>();
      // TODO: this might need a better way of initialising it
      this.serializer = new JSONSerializer(FixupCircRefAndNonPrimitiveDupes.class,
          new FixupsCircularReferenceHandler(), resolver);
      this.serializer.registerDefaultSerializers();
    } catch (Exception e) {
      throw new ClientError(e);
    }
  }

  /**
   * Gets the id of the next message.
   *
   * @return The id for the next message.
   */
  private int getId() {
    return id.incrementAndGet();
  }

  /**
   * Dispose of the proxy that is no longer needed.
   *
   * @param proxy The proxy to close
   */
  public void closeProxy(Object proxy) {
    proxyMap.remove(proxy);
  }

  /**
   * Allow access to the serializer.
   *
   * @return The serializer for this class
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public JSONSerializer getSerializer() {
    return serializer;
  }

  /**
   * Create a proxy for communicating with the remote service.
   *
   * @param key the remote object key
   * @param klass the class of the interface the remote object should adhere to
   * @return created proxy
   */
  public Object openProxy(String key, Class<?> klass) {
    Object result = java.lang.reflect.Proxy.newProxyInstance(Thread.currentThread()
        .getContextClassLoader(), new Class<?>[] {klass}, //
        (Object proxyObj, Method method, Object[] args) -> {
          String methodName = method.getName();
          switch (methodName) {
            case HASH_CODE:
              return System.identityHashCode(proxyObj);
            case EQUALS:
              return (proxyObj == args[0] // NOPMD
                  ? Boolean.TRUE : Boolean.FALSE);
            case TO_STRING:
              return proxyObj.getClass().getName() + '@' + Integer.toHexString(proxyObj.hashCode());
            default:
              return invoke(proxyMap.get(proxyObj), method.getName(), args, method.getReturnType());
          }
        });
    proxyMap.put(result, key);
    return result;
  }

  /**
   * Generate and throw exception based on the data in the 'responseMessage'.
   *
   * @param responseMessage The error message
   * @throws JSONException Rethrows the exception in the repsonse.
   */
  @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
  protected void processException(JSONObject responseMessage) throws JSONException {
    JSONObject error = (JSONObject) responseMessage.get("error");
    if (error != null) {
      Integer code = error.has("code") ? error.getInt("code") : 0;
      String trace = error.has("data") ? error.getString("data") : null;
      String msg = error.has("message") ? error.getString("message") : null;
      throw new ErrorResponse(code, msg, trace);
    }
    throw new ErrorResponse(FailedResult.CODE_ERR_PARSE, "Unknown response:" + responseMessage
        .toString(2), null);
  }

  /**
   * Invokes a method for the ciient.
   *
   * @param objectTag (optional) the name of the object to invoke the method on. May be null.
   * @param methodName The name of the method to call.
   * @param args The arguments to the method.
   * @param returnType What should be returned
   * @return The result of the call.
   * @throws Exception JSONObject, UnmarshallExceptions or Exceptions from invoking the method may
   *           be thrown.
   */
  private Object invoke(String objectTag, String methodName, Object[] args, Class<?> returnType)
      throws Exception {
    JSONObject message;
    String methodTag = objectTag == null ? "" : objectTag + ".";
    methodTag += methodName;

    {
      if (args != null) {
        SerializerState state = this.serializer.createSerializerState();
        Object params = serializer.marshall(state, /* parent */
            null, args, JSONSerializer.PARAMETER_FIELD);
        message = state.createObject(JSONSerializer.PARAMETER_FIELD, params);
      } else {
        message = new JSONObject();
        message.put(JSONSerializer.PARAMETER_FIELD, new JSONArray());
      }
    }
    message.put(JSONSerializer.METHOD_FIELD, methodTag);
    message.put(JSONSerializer.ID_FIELD, getId());

    JSONObject responseMessage = session.sendAndReceive(message);

    if (!responseMessage.has(JSONSerializer.RESULT_FIELD)) {
      processException(responseMessage);
    }

    Object rawResult = this.serializer.getRequestParser().unmarshall(responseMessage,
        JSONSerializer.RESULT_FIELD);
    if (returnType.equals(Void.TYPE)) {
      return null;
    } else if (rawResult == null) {
      processException(responseMessage);
    }
    {
      SerializerState state = this.serializer.createSerializerState();
      Object toReturn = serializer.unmarshall(state, returnType, rawResult);

      return toReturn;
    }
  }
}
