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
/**
 *
 */
package com.kohlschutter.dumborb.client.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.dumborb.serializer.response.results.SuccessfulResult;

class DummyAsyncSession implements AsyncSession {
  private final int duration;
  private final String response;

  public DummyAsyncSession(final int duration, final String response) {
    this.duration = duration;
    this.response = response;
  }

  @Override
  public void close() {
    // nothing
  }

  @Override
  public Future<JSONObject> send(final JSONObject request,
      final AsyncResultCallback<AsyncSession, JSONObject, JSONObject> callback) {
    final CompletableFuture<JSONObject> future = new CompletableFuture<JSONObject>();

    new Thread() {
      @Override
      public void run() {
        try {
          Thread.sleep(duration);
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }

        try {
          final JSONObject resp = new SuccessfulResult("1", DummyAsyncSession.this.response)
              .createOutput();

          future.complete(resp);
          if (callback != null) {
            callback.onAsyncResult(DummyAsyncSession.this, future, request);
          }
        } catch (final JSONException e) {
          throw new RuntimeException(e);
        }
      }
    }.start();

    return future;
  }

  @Override
  public Future<JSONObject> send(final JSONObject request) {
    return send(request, null);
  }
}
