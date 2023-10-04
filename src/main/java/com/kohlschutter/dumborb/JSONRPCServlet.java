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
package com.kohlschutter.dumborb;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.serializer.response.results.FailedResult;
import com.kohlschutter.dumborb.serializer.response.results.JSONRPCResult;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * This servlet handles JSON-RPC requests over HTTP and hands them to a JSONRPCBridge instance
 * (either a global instance or one in the user's HttpSession).
 * </p>
 * <p>
 * The following can be added to your web.xml to export the servlet under the URI
 * &quot;<code>/JSON-RPC</code>&quot;
 * </p>
 *
 * <pre>
 * &lt;servlet&gt;
 *   &lt;servlet-name&gt;com.kohlschutter.dumborb.JSONRPCServlet&lt;/servlet-name&gt;
 *   &lt;servlet-class&gt;com.kohlschutter.dumborb.JSONRPCServlet&lt;/servlet-class&gt;
 *   &lt;!-- optional gzip threshold control --&gt;
 *   &lt;init-param&gt;
 *     &lt;param-name&gt;gzip_threshold&lt;/param-name&gt;
 *     &lt;param-value&gt;200&lt;/param-value&gt;
 *   &lt;/init-param&gt;
 * &lt;/servlet&gt;
 * &lt;servlet-mapping&gt;
 *   &lt;servlet-name&gt;com.kohlschutter.dumborb.JSONRPCServlet&lt;/servlet-name&gt;
 *   &lt;url-pattern&gt;/JSON-RPC&lt;/url-pattern&gt;
 * &lt;/servlet-mapping&gt;
 * </pre>
 *
 * </p>
 * The JSONRPCServlet looks for a session specific bridge object under the attribute
 * <code>"JSONRPCBridge"</code> in the HttpSession associated with the request (without creating a
 * session if one does not already exist). If it can't find a session specific bridge instance, it
 * will default to invoking against the default bridge.
 * </p>
 * <p>
 * Using a session specific bridge allows you to export certain object instances or classes only to
 * specific users, and of course these instances could be stateful and contain data specific to the
 * user's session.
 * </p>
 * <p>
 * An example or creating a session specific bridge in JSP is as follows:
 * </p>
 * <code>
 * &lt;jsp:useBean id="JSONRPCBridge" scope="session"
 *   class="com.kohlschutter.dumborb.JSONRPCBridge"/&gt;
 * </code>
 * <p>
 * An example in Java (i.e. in another Servlet):
 * </p>
 * <code>
 * HttpSession session = request.getSession();<br />
 * JSONRPCBridge bridge = (JSONRPCBridge) session.getAttribute("JSONRPCBridge");<br>
 * if(bridge == null) {<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;bridge = new JSONRPCBridge();<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;session.setAttribute("JSONRPCBridge", bridge);<br />
 * }<br />
 * </code>
 */
public class JSONRPCServlet extends HttpServlet {
  private static final long serialVersionUID = -1; // not serializable, just to suppress a warning

  /**
   * The logger for this class.
   */
  private static final Logger LOG = LoggerFactory.getLogger(JSONRPCServlet.class);

  /**
   * The size of the buffer used for reading requests.
   */
  private static final int BUF_SIZE = 4096;

  /**
   * The GZIP_THRESHOLD indicates the response size at which the servlet will attempt to gzip the
   * response if it can. Gzipping smaller responses is counter productive for 2 reasons: 1. if the
   * response is really small, the gzipped output can actually be larger than the non-compressed
   * original. because of the gzip header and the general overhead of the gzipping. This is a
   * lose-lose situation, so the original should always be sent in this case. 2. gzipping imposes a
   * small performance penality in that it takes a little more time to gzip the content. There is
   * also a corresponding penality on the browser side when the content has to be uncompressed. This
   * penalty is really small, and is normally more than outweighed by the bandwidth savings provided
   * by gzip (the response is typically 1/10th the size when gzipped! Especially for json data which
   * tends to have a lot of repetition. So, the GZIP_THRESHOLD should be tuned to a size that is
   * optimal for your application. If your application is always served from a high speed network,
   * you might want to set this to a very high number-- (or even -1 to turn it off) for slower
   * networks where it's more important to conserve bandwidth, set this to a lower number (but not
   * too low!) Set this to zero if you want to always attempt to gzip the output when the browser
   * can accept gzip encoded responses. This is useful for analyzing what a good gzip setting should
   * be for potential responses from your application. You can set this to -1 if you want to turn
   * off gzip encoding for some reason.
   */
  private int gzipThreshold = 200;

  /**
   * The location of the JSONRPCBridge variable in the session.
   */
  private final String bridgeLocation;

  private final transient JSONRPCBridge defaultBridge;

  /**
   * Creates a new JSONRPCServlet with the bridge in the "JSONRPCBridge" variable.
   */
  public JSONRPCServlet(JSONRPCBridge defaultBridge) {
    this("JSONRPCBridge", defaultBridge);
  }

  /**
   * Creates a new JSONRPCServlet.
   *
   * @param bridgeLocation The location of the JSONRPCBridge variable in the session
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public JSONRPCServlet(String bridgeLocation, JSONRPCBridge defaultBridge) {
    super();
    this.bridgeLocation = bridgeLocation;
    this.defaultBridge = defaultBridge;
  }

  private void readObject(java.io.ObjectInputStream unused) throws IOException {
    throw new NotSerializableException(JSONRPCServlet.class.getName());
  }

  private void writeObject(java.io.ObjectOutputStream unused) throws IOException {
    throw new NotSerializableException(JSONRPCServlet.class.getName());
  }

  /**
   * Called by the container when the servlet is initialized. Check for optional configuration
   * parameters.
   * <p>
   * At this time, only gzip_threshold is looked for.
   * </p>
   * <p>
   * If it is found, and a valid Integer is specified, then that is used for the GZIP_THRESHOLD.
   * </p>
   * <p>
   * If an invalid Integer is specified, then the GZIP_THRESHOLD is set to -1 which disables GZIP
   * compression.
   * </p>
   * <p>
   * The gzip_threshold indicates the response size at which the servlet will attempt to gzip the
   * response if it can.
   * </p>
   * <p>
   * Set this to -1 if you want to disable gzip compression for some reason, or if you have another
   * filter or other mechanism to handle gzipping for you.
   * </p>
   * <p>
   * Set this to 0 to attempt to gzip all responses from this servlet. otherwise, set it to the
   * minimum response size at which gzip compression is attempted.
   * </p>
   * <p>
   * <b>NOTE:</b> if the browser making the request does not accept gzip compressed content, or the
   * result of gzipping would cause the response size to be larger (this could happen with very
   * small responses) then the content will be returned without gzipping, regardless.
   * </p>
   * <p>
   * of this setting, so it is very reasonable idea to set this to 0 for maximum bandwidth savings,
   * at the (very minor) expense of having the server attempt to gzip all responses.
   * </p>
   *
   * @param config ServletConfig from container.
   * @throws ServletException if something goes wrong during initialization.
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    String gzipThresh = config.getInitParameter("gzip_threshold");
    if (gzipThresh != null && gzipThresh.length() > 0) {
      try {
        gzipThreshold = Integer.parseInt(gzipThresh);
      } catch (NumberFormatException n) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("could not parse " + gzipThresh
              + " as an integer... defaulting to -1 (gzip compression off)");
        }
        gzipThreshold = -1;
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("GZIP_THRESHOLD is " + gzipThreshold);

      if (gzipThreshold == -1) {
        LOG.debug("Gzipping is turned OFF.  "
            + "No attempts will be made to gzip content from this servlet.");
      } else if (gzipThreshold == 0) {
        LOG.debug("All responses will be Gzipped "
            + "when gzipping results in a smaller response size.");
      } else {
        LOG.debug("Responses over this size will be Gzipped "
            + "when gzipping results in a smaller response size.");
      }
    }
  }

  /**
   * Called when a JSON-RPC requests comes in. Looks in the session for a JSONRPCBridge and if not
   * found there, uses the default bridge; then passes off the JSON-PRC call to be handled by the
   * JSONRPCBridge found.
   *
   * @param request servlet request from browser.
   * @param response servlet response to browser.
   * @throws IOException if an IOException occurs during processing.
   */
  @Override
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Use protected method in case someone wants to override it
    JSONRPCBridge jsonBridge = findBridge(request);

    // Decode using the charset in the request if it exists otherwise
    // use UTF-8 as this is what all browser implementations use.
    // The JSON-RPC-Java JavaScript client is ASCII clean so it
    // although here we can correctly handle data from other clients
    // that do not escape non ASCII data
    String charset = request.getCharacterEncoding();
    if (charset == null) {
      charset = "UTF-8";
    }

    String receiveString = (String) request.getAttribute("_jabsorb_beenHere");
    try (BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream(),
        charset))) {

      // if JSON data is found in a special request attribute, it means
      // that a continuation was used and this request is being retried
      // as a consequence of a Jetty continuation
      // see http://blogs.webtide.com/gregw/2007/11/18/1195421880000.html
      if (receiveString == null) {
        // Read the request
        CharArrayWriter data = new CharArrayWriter();
        char[] buf = new char[BUF_SIZE];
        int ret;
        while ((ret = in.read(buf, 0, BUF_SIZE)) != -1) {
          data.write(buf, 0, ret);
        }
        receiveString = data.toString();

        // save the json-rpc data in a special request attribute, in case a jetty
        // continuation exception (org.mortbay.jetty.RetryRequest) is thrown and this
        // request is retried by the container
        request.setAttribute("_jabsorb_beenHere", receiveString);
      } else {
        LOG.debug("jetty continuation resumed...");
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("receive on: " + bridgeLocation);
      LOG.debug("receive: " + receiveString);
      LOG.debug("receive: " + prettyPrintJson(receiveString));
    }

    // Process the request
    JSONObject jsonRequest;
    JSONRPCResult jsonResult;
    try {
      jsonRequest = new JSONObject(receiveString);
      jsonResult = jsonBridge.call(new Object[] {request, response}, jsonRequest);
    } catch (JSONException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("can't parse call" + receiveString, e);
      }
      jsonResult = new FailedResult(FailedResult.CODE_ERR_PARSE, null, FailedResult.MSG_ERR_PARSE);
    }

    String sendString = jsonResult.toString();

    // dump the received string
    if (LOG.isDebugEnabled()) {
      LOG.debug("send: " + sendString);
      LOG.debug("send: " + prettyPrintJson(sendString));
    }

    // Write the response
    byte[] bout = sendString.getBytes("UTF-8");

    // handle gzipping of the response if it is turned on
    if (gzipThreshold != -1) {
      // if the request header says that the browser can take gzip compressed output, then gzip the
      // output
      // but only if the response is large enough to warrant it and if the resultant compressed
      // output is
      // actually smaller.
      if (acceptsGzip(request)) {
        if (bout.length > gzipThreshold) {
          byte[] gzippedOut = gzip(bout);

          if (LOG.isDebugEnabled()) {
            LOG.debug("gzipping! original size =  " + bout.length + "  gzipped size = "
                + gzippedOut.length);
          }

          // if gzip didn't actually help, abort
          if (bout.length <= gzippedOut.length) {
            if (LOG.isWarnEnabled()) {
              LOG.warn("gzipping resulted in a larger output size!  "
                  + "aborting (sending non-gzipped response)... "
                  + "you may want to increase the gzip threshold if this happens a lot!"
                  + " original size = " + bout.length + "  gzipped size = " + gzippedOut.length);
            }
          } else {
            // go with the gzipped output
            bout = gzippedOut;
            response.addHeader("Content-Encoding", "gzip");
          }
        } else if (LOG.isDebugEnabled()) {
          LOG.debug("not gzipping because size is " + bout.length
              + " (less than the GZIP_THRESHOLD of " + gzipThreshold + " bytes)");
        }
      } else if (LOG.isDebugEnabled()) {
        // this should be rare with modern user agents
        LOG.debug("not gzipping because user agent doesn't accept gzip encoding...");
      }
    }

    // Encode using UTF-8, although We are actually ASCII clean as
    // all unicode data is JSON escaped using backslash u. This is
    // less data efficient for foreign character sets but it is
    // needed to support naughty browsers such as Konqueror and Safari
    // which do not honour the charset set in the response
    response.setContentType("application/json;charset=utf-8");

    try (OutputStream out = response.getOutputStream()) {
      response.setIntHeader("Content-Length", bout.length);
      out.write(bout);
      out.flush();
    }
  }

  /**
   * Find the JSONRPCBridge from the current session. If it can't be found in the session, or there
   * is no session, then return the default bridge.
   *
   * @param request The message received
   * @return the JSONRPCBridge to use for this request
   */
  protected JSONRPCBridge findBridge(HttpServletRequest request) {
    // Find the JSONRPCBridge for this session or create one
    // if it doesn't exist
    HttpSession session = request.getSession(false);
    JSONRPCBridge jsonBridge = null;
    if (session != null) {
      jsonBridge = (JSONRPCBridge) session.getAttribute(this.bridgeLocation);
    }
    if (jsonBridge == null) {
      jsonBridge = defaultBridge;
    }
    return jsonBridge;
  }

  /**
   * Checks if browser can accept gzip encoding.
   *
   * @param request browser request object.
   * @return true if gzip encoding accepted.
   */
  private boolean acceptsGzip(HttpServletRequest request) {
    // can browser accept gzip encoding?
    String ae = request.getHeader("accept-encoding");
    return ae != null && ae.indexOf("gzip") != -1;
  }

  /**
   * Gzip something.
   *
   * @param in original content
   * @return size gzipped content
   */
  private byte[] gzip(byte[] in) {
    if (in != null && in.length > 0) {
      long tstart = System.currentTimeMillis();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      try {
        try (GZIPOutputStream gout = new GZIPOutputStream(bout)) {
          gout.write(in);
          gout.flush();
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("gzipping took " + (System.currentTimeMillis() - tstart) + " msec");
        }
        return bout.toByteArray();
      } catch (IOException io) {
        LOG.error("io exception gzipping byte array", io);
      }
    }
    return new byte[0];
  }

  /**
   * Format (pretty print) json nicely for debugging output. If the pretty printing fails for any
   * reason (this is not expected) then the original, unformatted json will be returned.
   *
   * @param unformattedJSON a json string.
   * @return a String containing the formatted json text for the passed in json object.
   */
  private String prettyPrintJson(String unformattedJSON) {
    if (unformattedJSON == null || "".equals(unformattedJSON)) {
      return unformattedJSON;
    }
    try {
      // sort the keys in the output as well
      return new JSONObject(unformattedJSON).toString(2);
    } catch (JSONException je) {
      return unformattedJSON; // fall back to unformatted json, if pretty print fails...
    }
  }
}
