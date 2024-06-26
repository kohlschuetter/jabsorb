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
package com.kohlschutter.dumborb.callback;

import java.lang.reflect.AccessibleObject;

/**
 * Interface to be implemented by objects registered for invocation callbacks with the
 * JSONRPCBridge.
 *
 * Any registered InvocationCallback can optionally throw an Exception to cause any given invocation
 * to fail. This could be used as a simple security mechanism.
 */
public interface InvocationCallback<C> {
  /**
   * Callback before invocation of an RPC method.
   *
   * @param context The transport context (the HttpServletRequest object in the case of the HTTP
   *          transport).
   * @param instance The object instance or null if it is a static method.
   * @param accessibleObject Method/constructor that failed the invocation.
   * @param arguments The arguments passed to the method
   * @throws Exception if the invocation doesn't work.
   */
  void preInvoke(C context, Object instance, AccessibleObject accessibleObject, Object[] arguments)
      throws Exception;

  /**
   * Callback after invocation of an RPC method.
   *
   * @param context The transport context (the HttpServletRequest object in the case of the HTTP
   *          transport).
   * @param instance The object instance or null if it is a static method.
   * @param accessibleObject Method/constructor that failed the invocation.
   * @param result The returned result from the method
   * @param error Error (if execution was unsuccessful)
   * @throws Exception if the invocation doesn't work.
   */
  void postInvoke(C context, Object instance, AccessibleObject accessibleObject, Object result,
      Throwable error) throws Exception;

}
