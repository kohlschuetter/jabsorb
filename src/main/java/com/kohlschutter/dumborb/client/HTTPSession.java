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
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport session straightforwardly implemented in HTTP. As compared to the built-in
 * URLConnectionSession, it allows more control over HTTP transport parameters, for example, proxies
 * and the support for HTTPS.
 *
 * <p>
 * To use this transport you need to first register it in the TransportRegistry, for example:
 * <p>
 * <code>
 * HTTPSession.register(TransportRegistry.i());
 * </code>
 */
public class HTTPSession implements Session {
  private static final Logger LOG = LoggerFactory.getLogger(HTTPSession.class);

  /**
   * As per JSON-RPC Working Draft.
   * http://json-rpc.org/wd/JSON-RPC-1-1-WD-20060807.html#RequestHeaders
   */
  static final String JSON_CONTENT_TYPE = "application/json";

  protected HttpClient client;

  protected URI uri;

  public HTTPSession(URI uri) {
    this.uri = uri;
  }

  // /**
  // * An option to set state from the outside. for example, to provide existing session parameters.
  // */
  // public void setState(HttpState state) {
  // this.state = state;
  // }

  @Override
  public JSONObject sendAndReceive(JSONObject message) throws IOException {
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sending: " + message.toString(2));
      }

      Request postRequest = http().POST(uri.toString()).body(new StringRequestContent(
          JSON_CONTENT_TYPE, message.toString()));
      ContentResponse response;
      try {
        response = postRequest.send();
      } catch (InterruptedException | TimeoutException | ExecutionException e) {
        throw new ClientException(e);
      }

      int statusCode = response.getStatus();
      if (statusCode != HttpStatus.OK_200) {
        throw new ClientException("HTTP Status - " + HttpStatus.getMessage(statusCode) + " ("
            + statusCode + ")");
      }

      JSONTokener tokener = new JSONTokener(response.getContentAsString());

      Object rawResponseMessage = tokener.nextValue();
      JSONObject responseMessage = (JSONObject) rawResponseMessage;
      if (responseMessage == null) {
        throw new ClientException("Invalid response type: null");
      }

      return responseMessage;
    } catch (JSONException | IOException e) {
      throw new ClientException(e);
    }
  }

  HttpClient http() throws IOException {
    if (client == null) {
      client = new HttpClient();
      try {
        client.start();
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
    return client;
  }

  @Override
  public void close() throws Exception {
    if (client != null) {
      HttpClient cl = client;
      client = null;
      cl.stop();
    }
  }

  static class Factory implements SessionFactory {
    @Override
    public Session newSession(URI uri) {
      return new HTTPSession(uri);
    }
  }

  /**
   * Register this transport in 'registry'.
   */
  public static void register(TransportRegistry registry) {
    registry.registerTransport("http", new Factory());
  }
}
