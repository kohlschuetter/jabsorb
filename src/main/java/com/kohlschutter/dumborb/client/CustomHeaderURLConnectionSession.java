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

import java.net.URL;
import java.net.URLConnection;

/**
 * An {@link URLConnectionSession} that can send a custom header per each request.
 */
public class CustomHeaderURLConnectionSession extends URLConnectionSession {
  private final String headerKey;
  private String headerValue;

  /**
   * Create a URLConnection transport.
   *
   * @param url The URL.
   */
  public CustomHeaderURLConnectionSession(URL url, String headerKey, String headerValue) {
    super(url);
    this.headerKey = headerKey;
    this.headerValue = headerValue;
  }

  @Override
  protected void onConnection(URLConnection connection) {
    String v = headerValue;
    if (v != null) {
      connection.setRequestProperty(getHeaderKey(), v);
    }
  }

  public String getHeaderKey() {
    return headerKey;
  }

  public void setHeaderValue(String headerValue) {
    this.headerValue = headerValue;
  }
}
