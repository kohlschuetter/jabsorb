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
/**
 *
 */
package org.jabsorb.client.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.jabsorb.client.Session;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author matthijs
 *
 */
public class AsyncSessionUtil {
  private static final Logger LOG = LoggerFactory.getLogger(AsyncSessionUtil.class);

  public static Session toSyncSession(final AsyncSession asyncSession) {
    // unwrap if possible
    if (asyncSession instanceof AsyncedSyncSession) {
      return ((AsyncedSyncSession) asyncSession).getSession();
    }

    return new SyncedAsyncSession(asyncSession);
  }

  public static AsyncSession toAsyncSession(final Session session) {
    // unwrap if possible
    if (session instanceof SyncedAsyncSession) {
      return ((SyncedAsyncSession) session).getAsyncSession();
    }

    return new AsyncedSyncSession(session);
  }

  private static class SyncedAsyncSession implements Session {
    private final AsyncSession asyncSession;

    public SyncedAsyncSession(final AsyncSession asyncSession) {
      this.asyncSession = asyncSession;
    }

    /**
     * @return the asyncSession
     */
    public AsyncSession getAsyncSession() {
      return asyncSession;
    }

    @Override
    public JSONObject sendAndReceive(final JSONObject message) {
      final Future<JSONObject> result = asyncSession.send(message);

      JSONObject response = null;
      try {
        response = result.get();
      } catch (final InterruptedException e) {
        LOG.error("sendAndReceive was interrupted", e);
      } catch (final ExecutionException e) {
        LOG.error("sendAndReceive could not properly execute", e);
      }

      return response;
    }

    @Override
    public void close() {
      asyncSession.close();
    }
  }

  private static class AsyncedSyncSession implements AsyncSession {
    private final Session session;

    public AsyncedSyncSession(final Session session) {
      this.session = session;
    }

    /**
     * @return the session
     */
    public Session getSession() {
      return session;
    }

    @Override
    public Future<JSONObject> send(final JSONObject request) {
      return send(request, null);
    }

    @Override
    public Future<JSONObject> send(final JSONObject request,
        final AsyncResultCallback<AsyncSession, JSONObject, JSONObject> callback) {
      final CompletableFuture<JSONObject> result = new CompletableFuture<JSONObject>();

      new Thread() {
        @Override
        public void run() {
          final JSONObject response = session.sendAndReceive(request);

          result.complete(response);

          if (callback != null) {
            try {
              callback.onAsyncResult(AsyncedSyncSession.this, result, request);
            } catch (final Exception e) {
              LOG.warn("Callback threw exception", e);
            }
          }
        }
      }.start();

      return result;
    }

    @Override
    public void close() {
      session.close();
    }
  }
}
