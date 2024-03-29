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
package com.kohlschutter.dumborb.serializer.response.flat;

import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.dumborb.JSONSerializer;
import com.kohlschutter.dumborb.serializer.response.results.SuccessfulResult;

/**
 * Puts all the objects and indexes into a results.
 *
 * @author William Becker
 */
class FlatResult extends SuccessfulResult {
  /**
   * Maps the hash codes of objects to results.
   */
  private final Map<Integer, FlatProcessedObject> map;

  /**
   * Creates a new {@link FlatResult}.
   *
   * @param id The id of the message
   * @param jsonResult The main message to send
   * @param map Contains the other objects to put in the result
   */
  public FlatResult(Object id, Object jsonResult, Map<Integer, FlatProcessedObject> map) {
    super(id, jsonResult);
    this.map = map;
  }

  @Override
  public JSONObject createOutput() throws JSONException {
    JSONObject o = this.createOutput0();
    Object result = getResult();
    if (result != null) {
      FlatSerializerState.addValuesToObject(o, result, JSONSerializer.RESULT_FIELD, this.map);
    }
    return o;
  }
}
