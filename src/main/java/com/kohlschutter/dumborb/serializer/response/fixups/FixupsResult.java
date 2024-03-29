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
package com.kohlschutter.dumborb.serializer.response.fixups;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.serializer.response.FixUp;
import com.kohlschutter.dumborb.serializer.response.results.SuccessfulResult;

/**
 * A successful result that contains fixups.
 *
 * @author William Becker
 */
public class FixupsResult extends SuccessfulResult {
  /**
   * Optional fixup entries to run against the result in order to reconstitute duplicate and / or
   * circular references that were detected. This is a List of FixUp objects.
   *
   * @see FixUp
   */
  private final Collection<FixUp> fixUps;

  /**
   * Creates a new FixupsResult.
   *
   * @param id The id of the response.
   * @param o The main data to return.
   * @param fixups The fixups to return.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public FixupsResult(Object id, Object o, Collection<FixUp> fixups) {
    super(id, o);
    this.fixUps = fixups;
  }

  @Override
  public JSONObject createOutput() throws JSONException {
    JSONObject o = super.createOutput();
    UsingFixups.addFixups(o, this.fixUps);
    return o;
  }
}
