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
package com.kohlschutter.dumborb.client;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Transport based on {@link URLConnection}.
 */
public class URLConnectionSession implements Session {
  final URL url;

  /**
   * Create a URLConnection transport.
   *
   * @param url The URL.
   */
  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  public URLConnectionSession(URL url) throws IllegalArgumentException {
    this.url = url;
    String proto = url.getProtocol();
    if (!"http".equals(proto) && !"https".equals(proto)) {
      throw new IllegalArgumentException("Unsupported protocol in URL: " + url);
    }
  }

  @Override
  public void close() {
    // Nothing to do
  }

  @Override
  @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
  public JSONObject sendAndReceive(JSONObject message) {
    try {
      URLConnection connection = url.openConnection();
      if (connection instanceof HttpURLConnection) {
        ((HttpURLConnection) connection).setRequestMethod("POST");
      }
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
      onConnection(connection);

      try (Writer request = new OutputStreamWriter(connection.getOutputStream(),
          StandardCharsets.UTF_8)) {
        message.write(request);
      }

      JSONTokener tokener = new JSONTokener(connection.getInputStream());
      Object rawResponseMessage = tokener.nextValue();
      if (rawResponseMessage == null) {
        throw new ClientException("Invalid response type - null");
      } else if (!(rawResponseMessage instanceof JSONObject)) {
        throw new ClientException("Invalid response type: not an object, got: " + rawResponseMessage
            .getClass());
      }
      return (JSONObject) rawResponseMessage;
    } catch (JSONException | IOException e) {
      throw new ClientException(e);
    }
  }

  protected void onConnection(URLConnection connection) {
  }
}
