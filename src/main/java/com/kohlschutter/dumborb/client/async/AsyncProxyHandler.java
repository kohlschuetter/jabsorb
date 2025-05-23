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
package com.kohlschutter.dumborb.client.async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.client.ErrorResponse;
import com.kohlschutter.dumborb.serializer.MarshallException;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.response.results.FailedResult;

final class AsyncProxyHandler implements InvocationHandler {
  private static final String TO_STRING = "toString";
  private static final String EQUALS = "equals";
  private static final String HASH_CODE = "hashCode";
  // AsyncProxy methods
  private static final Method METHOD_GET_FUTURE_RESULT;
  private static final Method METHOD_SET_RESULT_CALLBACK;

  static {
    try {
      METHOD_GET_FUTURE_RESULT = AsyncProxy.class.getDeclaredMethod("getFutureResult",
          new Class<?>[0]);
      METHOD_SET_RESULT_CALLBACK = AsyncProxy.class.getDeclaredMethod("setResultCallback",
          AsyncResultCallback.class);
    } catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final String proxyKey;
  private final AsyncSession session;
  private final JSONSerializer serializer;

  private Future<Object> futureResult;

  private AsyncResultCallback<Object, Object, Method> resultCallback;

  public AsyncProxyHandler(final String proxyKey, final AsyncSession session,
      final JSONSerializer serializer) {
    this.proxyKey = proxyKey;
    this.session = session;
    this.serializer = serializer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object invoke(final Object proxyObj, final Method method, final Object[] args)
      throws Exception {
    assert (proxyObj instanceof AsyncProxy) : "Proxy object is not created by AsyncClient?";

    switch (method.getName()) {
      case HASH_CODE:
        return System.identityHashCode(proxyObj);
      case EQUALS:
        return (proxyObj == args[0] // NOPMD
            ? Boolean.TRUE : Boolean.FALSE);
      case TO_STRING:
        return proxyObj.getClass().getName() + '@' + Integer.toHexString(proxyObj.hashCode());
      default:
        if (METHOD_GET_FUTURE_RESULT.equals(method)) {
          synchronized (this) {
            return futureResult;
          }
        } else if (METHOD_SET_RESULT_CALLBACK.equals(method)) {
          setResultCallback((AsyncResultCallback<Object, Object, Method>) args[0]);
          return null;
        } else {
          return doInvoke(proxyObj, method, args);
        }
    }
  }

  /**
   * Invokes a method for the asynchronous client and returns null immediately.
   *
   * @param objectTag (optional) the name of the object to invoke the method on. May be null.
   * @param method The method to call.
   * @param args The arguments to the method.
   * @param returnType What should be returned
   * @return Always null
   * @throws Exception JSONObject, UnmarshallExceptions or Exceptions from invoking the method may
   *           be thrown.
   */
  private Object doInvoke(final Object proxyObject, final Method method, final Object[] args)
      throws Exception {
    // Create a final reference that can be used when this method
    // returns. We don't know if the original callback will still be
    // the same.
    final AsyncResultCallback<Object, Object, Method> currentCallback;
    synchronized (this) {
      currentCallback = resultCallback;
    }

    final CompletableFuture<Object> future = new CompletableFuture<Object>();
    setFutureResult(future);

    final JSONObject message = createInvokeMessage(proxyKey, method.getName(), args);

    final AsyncResultCallback<AsyncSession, JSONObject, JSONObject> jsonResultCallback =
        new AsyncResultCallback<AsyncSession, JSONObject, JSONObject>() {
          @Override
          public void onAsyncResult(final AsyncSession source, final Future<JSONObject> response,
              final JSONObject request) {
            // get the response
            try {
              final JSONObject responseMessage = response.get();

              // convert the response
              final Object resultObject = convertResponseMessage(responseMessage, method
                  .getReturnType());

              // set the result onto the future that is used
              future.complete(resultObject);
            } catch (ExecutionException e) {
              // deal with exceptions in the future
              future.completeExceptionally(e);
            } catch (Exception e) {
              // deal with exceptions in the future
              future.completeExceptionally(new ExecutionException(e));
            }

            // call the callback that was set when invoke was called
            currentCallback.onAsyncResult(proxyObject, future, method);
          }
        };

    // invoke the method by sending the message
    session.send(message, jsonResultCallback);

    // return null as fast as you can
    return null;
  }

  /**
   * Gets the id of the next message.
   *
   * @return The id for the next message.
   */
  private String nextId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generate and throw exception based on the data in the 'responseMessage'.
   *
   * @param responseMessage The error message
   * @throws JSONException Rethrows the exception in the repsonse.
   */
  @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
  private void processException(final JSONObject responseMessage) throws JSONException {
    final JSONObject error = (JSONObject) responseMessage.get("error");
    if (error != null) {
      final Integer code = error.has("code") ? error.getInt("code") : 0;
      final String trace = error.has("data") ? error.getString("data") : null;
      final String msg = error.has("message") ? error.getString("message") : null;
      throw new ErrorResponse(code, msg, trace);
    }
    throw new ErrorResponse(FailedResult.CODE_ERR_PARSE, "Unknown response:" + responseMessage
        .toString(2), null);
  }

  private JSONObject createInvokeMessage(final String objectTag, final String methodName,
      final Object[] args) throws MarshallException, JSONException {
    JSONObject message;
    String methodTag = objectTag == null ? "" : objectTag + ".";
    methodTag += methodName;

    if (args != null) {
      final SerializerState state = serializer.createSerializerState();
      final Object params = serializer.marshall(state, /* parent */
          null, args, JSONSerializer.PARAMETER_FIELD);

      message = state.createObject(JSONSerializer.PARAMETER_FIELD, params);
    } else {
      message = new JSONObject();
      message.put(JSONSerializer.PARAMETER_FIELD, new JSONArray());
    }

    message.put(JSONSerializer.METHOD_FIELD, methodTag);
    message.put(JSONSerializer.ID_FIELD, nextId());

    return message;
  }

  private Object convertResponseMessage(final JSONObject responseMessage, final Class<?> returnType)
      throws Exception {
    if (!responseMessage.has(JSONSerializer.RESULT_FIELD)) {
      processException(responseMessage);
    }

    final Object rawResult = serializer.getRequestParser().unmarshall(responseMessage,
        JSONSerializer.RESULT_FIELD);

    if (returnType.equals(Void.TYPE)) {
      return null;
    } else if (rawResult == null) {
      processException(responseMessage);
    }

    final SerializerState state = serializer.createSerializerState();
    final Object toReturn = serializer.unmarshall(state, returnType, rawResult);

    return toReturn;
  }

  private synchronized void setFutureResult(final Future<Object> futureResult) {
    // Synchronize setting the futureResult so that calling a method and
    // getting the futureResult in one synchronized block returns the
    // futureResult for that call. Other calling threads not holding the
    // monitor will have to wait
    this.futureResult = futureResult;
  }

  private synchronized void setResultCallback(
      final AsyncResultCallback<Object, Object, Method> resultCallback) {
    // Synchronize setting the resultCallback so that calling a method and
    // setting the resultCallback in one synchronized block set the
    // resultCallback for that call. Other calling threads not holding the
    // monitor will have to wait
    this.resultCallback = resultCallback;
  }
}
