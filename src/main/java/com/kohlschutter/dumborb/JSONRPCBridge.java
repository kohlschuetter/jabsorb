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

import java.lang.reflect.AccessibleObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.dumborb.callback.CallbackController;
import com.kohlschutter.dumborb.callback.InvocationCallback;
import com.kohlschutter.dumborb.localarg.LocalArgController;
import com.kohlschutter.dumborb.localarg.LocalArgResolver;
import com.kohlschutter.dumborb.reflect.AccessibleObjectKey;
import com.kohlschutter.dumborb.reflect.ClassAnalyzer;
import com.kohlschutter.dumborb.reflect.ClassData;
import com.kohlschutter.dumborb.security.ClassResolver;
import com.kohlschutter.dumborb.serializer.AccessibleObjectResolver;
import com.kohlschutter.dumborb.serializer.Serializer;
import com.kohlschutter.dumborb.serializer.SerializerState;
import com.kohlschutter.dumborb.serializer.impl.ReferenceSerializer;
import com.kohlschutter.dumborb.serializer.request.RequestParser;
import com.kohlschutter.dumborb.serializer.request.fixups.FixupsCircularReferenceHandler;
import com.kohlschutter.dumborb.serializer.response.fixups.FixupCircRefAndNonPrimitiveDupes;
import com.kohlschutter.dumborb.serializer.response.results.FailedResult;
import com.kohlschutter.dumborb.serializer.response.results.JSONRPCResult;
import com.kohlschutter.dumborb.serializer.response.results.SuccessfulResult;

/**
 * <p>
 * This class implements a bridge that unmarshalls JSON objects in JSON-RPC request format, invokes
 * a method on the exported object, and then marshalls the resulting Java objects to JSON objects in
 * JSON-RPC result format.
 * </p>
 * <p>
 * There is a global bridge singleton object that allows exporting classes and objects to all HTTP
 * clients. In addition to this, an instance of the JSONRPCBridge can optionally be placed in a
 * users' HttpSession object registered under the attribute "JSONRPCBridge" to allow exporting of
 * classes and objects to specific users. A session specific bridge will delegate requests for
 * objects it does not know about to the global singleton JSONRPCBridge instance.
 * </p>
 * <p>
 * Using session specific bridge instances can improve the security of applications by allowing
 * exporting of certain objects only to specific HttpSessions as well as providing a convenient
 * mechanism for JavaScript clients to access stateful data associated with the current user.
 * </p>
 * <p>
 * You can create a HttpSession specific bridge in JSP with the usebean tag:
 * </p>
 * <code>&lt;jsp:useBean id="JSONRPCBridge" scope="session"
 * class="com.kohlschutter.dumborb.JSONRPCBridge" /&gt;</code>
 * <p>
 * Then export an object for your JSON-RPC client to call methods on:
 * </p>
 * <code>JSONRPCBridge.registerObject("test", testObject);</code>
 * <p>
 * This will make available all public methods of the object as
 * <code>test.&lt;methodnames&gt;</code> to JSON-RPC clients. This approach should generally be
 * performed after an authentication check to only export objects to clients that are authorised to
 * use them.
 * </p>
 */
@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects"})
public final class JSONRPCBridge {
  /**
   * The logger for this class.
   */
  private static final Logger LOG = LoggerFactory.getLogger(JSONRPCBridge.class);

  private static final String SYSTEM_LIST_METHODS = "system.listMethods";

  /**
   * The prefix for callable references, as sent in messages.
   */
  private static final String CALLABLE_REFERENCE_METHOD_PREFIX = ";ref";

  /**
   * The string identifying constuctor calls.
   */
  public static final String CONSTRUCTOR_FLAG = "$constructor";

  /**
   * The prefix for objects, as sent in messages.
   */
  private static final String OBJECT_METHOD_PREFIX = ";obj";

  /**
   * A simple transformer that makes no change.
   */
  private static final ExceptionTransformer IDENTITY_EXCEPTION_TRANSFORMER =
      new ExceptionTransformer() {
        @Override
        public Object transform(Throwable t) {
          return t;
        }
      };

  /**
   * Whether references will be used on the bridge.
   */
  private boolean referencesEnabled;

  /**
   * Key clazz, classes that should be returned as CallableReferences.
   */
  private final Set<Class<?>> callableReferenceSet;

  /**
   * The callback controller.
   */
  private CallbackController cbc = null;

  /**
   * Key "exported class name", val Class.
   */
  private final Map<String, Class<?>> classMap;

  /**
   * The functor used to convert exceptions.
   */
  private ExceptionTransformer exceptionTransformer = IDENTITY_EXCEPTION_TRANSFORMER;

  /**
   * Key "exported instance name", val ObjectInstance.
   */
  private final Map<Object, ObjectInstance> objectMap;

  /**
   * key Integer hashcode, object held as reference.
   */
  private final Map<Integer, Object> referenceMap;

  /**
   * ReferenceSerializer if enabled.
   */
  private final Serializer referenceSerializer;

  /**
   * key clazz, classes that should be returned as References.
   */
  private final Set<Class<?>> referenceSet;

  /**
   * Local JSONSerializer instance.
   */
  private final JSONSerializer ser;

  public JSONRPCBridge(ClassResolver resolver) {
    this(JSONSerializer.getDefaultSerializers(), new FixupsCircularReferenceHandler(),
        FixupCircRefAndNonPrimitiveDupes.class, resolver);
  }

  /**
   * Creates a new bridge.
   *
   * @param serializers The serializers to load on this bridge.
   * @param requestParser The request parser to use
   * @param serializerStateClass The serializer state to use
   */
  public JSONRPCBridge(final List<Serializer> serializers, final RequestParser requestParser,
      final Class<? extends SerializerState> serializerStateClass, ClassResolver classResolver) {
    {
      if (serializerStateClass == null) {
        LOG.debug("Using default serializer state");
      } else if (LOG.isDebugEnabled()) {
        LOG.debug("Using serializer state: {}", serializerStateClass.getCanonicalName());
      }
      if (requestParser == null) {
        LOG.debug("Using default request parser");
      } else if (LOG.isDebugEnabled()) {
        LOG.debug("Using request parser: {}", requestParser.getClass().getCanonicalName());
      }
    }

    ser = new JSONSerializer(serializerStateClass, requestParser, classResolver);

    referenceSerializer = new ReferenceSerializer(this);
    try {
      for (Serializer s : serializers) {
        if (s.getClass().equals(ReferenceSerializer.class)) {
          ser.registerSerializer(this.referenceSerializer);
        } else {
          ser.registerSerializer(s);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    classMap = new HashMap<String, Class<?>>();
    objectMap = new HashMap<Object, ObjectInstance>();
    referenceMap = new HashMap<Integer, Object>();
    referenceSet = new HashSet<Class<?>>();
    callableReferenceSet = new HashSet<Class<?>>();
    referencesEnabled = false;
  }

  /**
   * Registers a Class to be removed from the exported method signatures and instead be resolved
   * locally using context information from the transport.
   *
   * @param argClazz The class to be resolved locally
   * @param argResolver The user defined class that resolves the and returns the method argument
   *          using transport context information
   * @param contextInterface The type of transport Context object the callback is interested in eg.
   *          HttpServletRequest.class for the servlet transport
   */
  public static void registerLocalArgResolver(Class<?> argClazz, Class<?> contextInterface,
      LocalArgResolver argResolver) {
    LocalArgController.registerLocalArgResolver(argClazz, contextInterface, argResolver);
  }

  /**
   * Unregisters a {@link LocalArgResolver}.
   *
   * @param argClazz The previously registered local class
   * @param argResolver The previously registered LocalArgResolver object
   * @param contextInterface The previously registered transport Context interface.
   */
  public static void unregisterLocalArgResolver(Class<?> argClazz, Class<?> contextInterface,
      LocalArgResolver argResolver) {
    LocalArgController.unregisterLocalArgResolver(argClazz, contextInterface, argResolver);
  }

  /**
   * Create unique method names by appending the given prefix to the keys from the given HashMap and
   * adding them all to the given HashSet.
   *
   * @param m HashSet to add unique methods to.
   * @param prefix prefix to append to each method name found in the methodMap.
   * @param methodMap a HashMap containing MethodKey keys specifying methods.
   */
  private static void uniqueMethods(Set<String> m, String prefix,
      Map<AccessibleObjectKey, Set<AccessibleObject>> methodMap) {
    for (Map.Entry<AccessibleObjectKey, Set<AccessibleObject>> mentry : methodMap.entrySet()) {
      AccessibleObjectKey mk = mentry.getKey();
      m.add(prefix + mk.getMethodName());
    }
  }

  /**
   * Adds a reference to the map of known references.
   *
   * @param o The object to be added
   */
  public void addReference(Object o) {
    synchronized (referenceMap) {
      referenceMap.put(System.identityHashCode(o), o);
    }
  }

  /**
   * Call a method using a JSON-RPC request object.
   *
   * @param context The transport context (the HttpServletRequest and HttpServletResponse objects in
   *          the case of the HTTP transport).
   * @param jsonReq The JSON-RPC request structured as a JSON object tree.
   * @return a JSONRPCResult object with the result of the invocation or an error.
   */
  public JSONRPCResult call(Object[] context, JSONObject jsonReq) {
    // #1: Parse the request
    final String encodedMethod;
    final Object requestId;
    final JSONArray arguments;

    try {
      encodedMethod = jsonReq.getString(JSONSerializer.METHOD_FIELD);
      requestId = jsonReq.opt(JSONSerializer.ID_FIELD);
      arguments = this.ser.getRequestParser().unmarshallArray(jsonReq,
          JSONSerializer.PARAMETER_FIELD);
      if (LOG.isDebugEnabled()) {
        LOG.debug("call " + encodedMethod + "(" + arguments + ")" + ", requestId=" + requestId);
      }
      // #2: Get the name of the class and method from the encodedMethod
      final String className;
      final String methodName;
      {
        int lastDot = encodedMethod.lastIndexOf('.');
        if (lastDot == -1) {
          className = encodedMethod;
          methodName = null;
        } else {
          className = encodedMethod.substring(0, lastDot);
          methodName = encodedMethod.substring(lastDot + 1);
        }
      }
      // #3: Get the id of the object (if it exists) from the className
      // (in the format: ".obj#<objectID>")
      final int objectID;
      {
        final int objectStartIndex = encodedMethod.indexOf('[');
        final int objectEndIndex = encodedMethod.indexOf(']');
        if (encodedMethod.startsWith(OBJECT_METHOD_PREFIX) && (objectStartIndex != -1)
            && (objectEndIndex != -1) && (objectStartIndex < objectEndIndex)) {
          objectID = Integer.parseInt(encodedMethod.substring(objectStartIndex + 1,
              objectEndIndex));
        } else {
          objectID = 0;
        }
      }
      // #4: Handle list method calls
      if (objectID == 0 && SYSTEM_LIST_METHODS.equals(encodedMethod)) {
        return new SuccessfulResult(requestId, getSystemMethods());
      }

      // #5: Get the object to act upon and the possible method that could be
      // called on it
      final Map<AccessibleObjectKey, Set<AccessibleObject>> methodMap;
      final Object javascriptObject;
      final AccessibleObject ao;
      try {
        javascriptObject = getObjectContext(objectID, className);
        methodMap = getAccessibleObjectMap(objectID, className, methodName);
        // #6: Resolve the method
        ao = AccessibleObjectResolver.resolveMethod(methodMap, methodName, arguments, ser);
        if (ao == null) {
          throw new NoSuchMethodException(FailedResult.MSG_ERR_NOMETHOD);
        }
        // #7: Call the method
        return AccessibleObjectResolver.invokeAccessibleObject(ao, context, arguments,
            javascriptObject, requestId, ser, cbc, exceptionTransformer);
      } catch (NoSuchMethodException e) { // NOPMD.ExceptionAsFlowControl
        if (FailedResult.MSG_ERR_NOCONSTRUCTOR.equals(e.getMessage())) {
          return new FailedResult(FailedResult.CODE_ERR_NOCONSTRUCTOR, requestId,
              FailedResult.MSG_ERR_NOCONSTRUCTOR);
        } else {
          return new FailedResult(FailedResult.CODE_ERR_NOMETHOD, requestId,
              FailedResult.MSG_ERR_NOMETHOD);
        }
      }
    } catch (JSONException e) {
      LOG.error("no method or parameters in request");
      return new FailedResult(FailedResult.CODE_ERR_NOMETHOD, null, FailedResult.MSG_ERR_NOMETHOD);
    }
  }

  /**
   * Allows references to be used on the bridge.
   *
   * @throws Exception If a serializer has already been registered for CallableReferences.
   */
  public synchronized void enableReferences() throws Exception {
    if (!referencesEnabled) {
      registerSerializer(referenceSerializer);
      referencesEnabled = true;
      LOG.info("enabled references on this bridge");
    }
  }

  /**
   * Get the CallbackController object associated with this bridge.
   *
   * @return the CallbackController object associated with this bridge.
   */
  public CallbackController getCallbackController() {
    return cbc;
  }

  /**
   * Gets a known reference.
   *
   * @param objectId The id of the object to get.
   * @return The requested reference.
   */
  public Object getReference(int objectId) {
    synchronized (referenceMap) {
      return referenceMap.get(objectId);
    }
  }

  /**
   * Get the global JSONSerializer object.
   *
   * @return the global JSONSerializer object.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public JSONSerializer getSerializer() {
    return ser;
  }

  /**
   * Check whether a class is registered as a callable reference type.
   *
   * @param clazz The class object to check is a callable reference.
   * @return true if it is, false otherwise
   */
  public boolean isCallableReference(Class<?> clazz) {
    if (!referencesEnabled) {
      return false;
    }
    if (callableReferenceSet.contains(clazz)) {
      return true;
    }

    // check if the class implements any interface that is
    // registered as a callable reference...
    for (Class<?> interf : clazz.getInterfaces()) {
      if (callableReferenceSet.contains(interf)) {
        return true;
      }
    }

    // check super classes as well...
    Class<?> superClass = clazz.getSuperclass();
    while (superClass != null) {
      if (callableReferenceSet.contains(superClass)) {
        return true;
      }
      superClass = superClass.getSuperclass();
    }

    // should interfaces of each superclass be checked too???
    // not sure...

    return false;
  }

  /**
   * Check whether a class is registered as a reference type.
   *
   * @param clazz The class object to check is a reference.
   * @return true if it is, false otherwise.
   */
  public boolean isReference(Class<?> clazz) {
    if (!referencesEnabled) {
      return false;
    }
    if (referenceSet.contains(clazz)) {
      return true;
    }
    return false;
  }

  /**
   * Lookup a class that is registered with this bridge.
   *
   * @param name The registered name of the class to lookup.
   * @return the class for the name
   */
  public Class<?> lookupClass(String name) {
    synchronized (classMap) {
      return classMap.get(name);
    }
  }

  /**
   * Lookup an object that is registered with this bridge.
   *
   * @param key The registered name of the object to lookup.
   * @return The object desired if it exists, else null.
   */
  public Object lookupObject(Object key) {
    synchronized (objectMap) {
      ObjectInstance oi = objectMap.get(key);
      if (oi != null) {
        return oi.getObject();
      }
    }
    return null;
  }

  /**
   * <p>
   * Registers a class to be returned as a callable reference.
   * </p>
   * <p>
   * The JSONBridge will return a callable reference to the JSON-RPC client for registered classes
   * instead of passing them by value. The JSONBridge will take a references to these objects and
   * the JSON-RPC client will create an invocation proxy for objects of this class for which methods
   * will be called on the instance on the server.
   * </p>
   * <p>
   * <p>
   * Note that the global bridge does not support registering of callable references and attempting
   * to do so will throw an Exception. These operations are inherently session based and are
   * disabled on the global bridge because there is currently no safe simple way to garbage collect
   * such references across the JavaScript/Java barrier.
   * </p>
   * <p>
   * A Callable Reference in JSON format looks like this:
   * </p>
   * <code>{ "javaClass":"com.kohlschutter.dumborb.test.Bar",<br />
   * "objectID":4827452,<br /> "JSONRPCType":"CallableReference" }</code>
   *
   * @param clazz The class object that should be marshalled as a callable reference.
   * @throws Exception if this method is called on the global bridge.
   */
  public void registerCallableReference(Class<?> clazz) throws Exception {
    if (!referencesEnabled) {
      enableReferences();
    }
    synchronized (callableReferenceSet) {
      callableReferenceSet.add(clazz);
    }
    ser.registerCallableReference(clazz);
    if (LOG.isDebugEnabled()) {
      LOG.debug("registered callable reference " + clazz.getName());
    }
  }

  /**
   * Registers a callback to be called before and after method invocation.
   *
   * @param callback The object implementing the InvocationCallback Interface
   * @param contextInterface The type of transport Context interface the callback is interested in
   *          eg. HttpServletRequest.class for the servlet transport.
   * @param <C> The context type.
   */
  public <C> void registerCallback(InvocationCallback<C> callback, Class<C> contextInterface) {
    if (cbc == null) {
      cbc = new CallbackController();
    }
    cbc.registerCallback(callback, contextInterface);
  }

  /**
   * Registers a class to export static methods.
   * <p>
   * The JSONBridge will export all static methods of the class. This is useful for exporting
   * factory classes that may then return CallableReferences to the JSON-RPC client.
   * <p>
   * Calling registerClass for a clazz again under the same name will have no effect.
   * <p>
   * To export instance methods you need to use registerObject.
   *
   * @param name The name to register the class with.
   * @param clazz The class to export static methods from.
   * @throws IllegalStateException If a class is already registed with this name
   */
  public void registerClass(String name, Class<?> clazz) throws IllegalStateException {
    synchronized (classMap) {
      Class<?> exists = classMap.get(name);
      if (exists != null && exists != clazz) {
        throw new IllegalStateException("different class registered as " + name);
      }
      if (exists == null) {
        classMap.put(name, clazz);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("registered class " + clazz.getName() + " as " + name);
    }
  }

  /**
   * Registers an object to export all instance methods and static methods.
   * <p>
   * The JSONBridge will export all instance methods and static methods of the particular object
   * under the name passed in as a key.
   * <p>
   * This will make available all methods of the object as
   * <code>&lt;key&gt;.&lt;methodnames&gt;</code> to JSON-RPC clients.
   * <p>
   * Calling registerObject for a name that already exists will replace the existing entry.
   *
   * @param key The named prefix to export the object as
   * @param o The object instance to be called upon
   */
  public void registerObject(Object key, Object o) {
    ObjectInstance oi = new ObjectInstance(o);
    synchronized (objectMap) {
      objectMap.put(key, oi);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("registered object " + o.hashCode() + " of class " + o.getClass().getName() + " as "
          + key);
    }
  }

  /**
   * Registers an object to export all instance methods defined by interfaceClass.
   * <p>
   * The JSONBridge will export all instance methods defined by interfaceClass of the particular
   * object under the name passed in as a key.
   * <p>
   * This will make available these methods of the object as
   * <code>&lt;key&gt;.&lt;methodnames&gt;</code> to JSON-RPC clients.
   *
   * @param key The named prefix to export the object as
   * @param o The object instance to be called upon
   * @param interfaceClass The type that this object should be registered as.
   *          <p>
   *          This can be used to restrict the exported methods to the methods defined in a specific
   *          superclass or interface.
   */
  public void registerObject(String key, Object o, Class<?> interfaceClass) {
    ObjectInstance oi = new ObjectInstance(o, interfaceClass);
    synchronized (objectMap) {
      objectMap.put(key, oi);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("registered object " + o.hashCode() + " of class " + interfaceClass.getName()
          + " as " + key);
    }
  }

  /**
   * Registers a class to be returned by reference and not by value as is done by default.
   * <p>
   * The JSONBridge will take a references to these objects and return an opaque object to the
   * JSON-RPC client. When the opaque object is passed back through the bridge in subsequent calls,
   * the original object is substitued in calls to Java methods. This should be used for any objects
   * that contain security information or complex types that are not required in the Javascript
   * client but need to be passed as a reference in methods of exported objects.
   * <p>
   * A Reference in JSON format looks like this:
   * <p>
   * <code>{ "javaClass":"com.kohlschutter.dumborb.test.Foo",<br />
   * "objectID":5535614,<br /> "JSONRPCType":"Reference" }</code>
   * <p>
   * Note that the global bridge does not support registering of references and attempting to do so
   * will throw an Exception. These operations are inherently session based and are disabled on the
   * global bridge because there is currently no safe simple way to garbage collect such references
   * across the JavaScript/Java barrier.
   * </p>
   *
   * @param clazz The class object that should be marshalled as a reference.
   * @throws Exception if this method is called on the global bridge.
   */
  public void registerReference(Class<?> clazz) throws Exception {
    if (!referencesEnabled) {
      enableReferences();
    }
    synchronized (referenceSet) {
      referenceSet.add(clazz);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("registered reference " + clazz.getName());
    }
  }

  /**
   * Register a new serializer on this bridge.
   *
   * @param serializer A class implementing the Serializer interface (usually derived from
   *          AbstractSerializer).
   * @throws Exception If a serialiser has already been registered that serialises the same class
   */
  public void registerSerializer(Serializer serializer) throws Exception {
    ser.registerSerializer(serializer);
  }

  /**
   * Set the CallbackController object for this bridge.
   *
   * @param cbc the CallbackController object to be set for this bridge.
   */
  public void setCallbackController(CallbackController cbc) {
    this.cbc = cbc;
  }

  /**
   * Sets the exception transformer for the bridge.
   *
   * @param exceptionTransformer The new exception transformer to use.
   */
  public void setExceptionTransformer(ExceptionTransformer exceptionTransformer) {
    this.exceptionTransformer = exceptionTransformer;
  }

  /**
   * Allow serializer state class to be set after construction. This is necessary for beans.
   *
   * @param serializerStateClass The serializer state class to use.
   */
  public void setSerializerStateClass(Class<? extends SerializerState> serializerStateClass) {
    this.ser.setSerializerStateClass(serializerStateClass);
  }

  /**
   * Unregisters a callback.
   *
   * @param callback The previously registered InvocationCallback object
   * @param contextInterface The previously registered transport Context interface.
   */
  public <C> void unregisterCallback(InvocationCallback<C> callback, Class<C> contextInterface) {
    if (cbc == null) {
      return;
    }
    cbc.unregisterCallback(callback, contextInterface);
  }

  /**
   * Unregisters a class exported with registerClass.
   * <p>
   * The JSONBridge will unexport all static methods of the class.
   *
   * @param name The registered name of the class to unexport static methods from.
   */
  public void unregisterClass(String name) {
    synchronized (classMap) {
      Class<?> clazz = classMap.get(name);
      if (clazz != null) {
        classMap.remove(name);
        if (LOG.isDebugEnabled()) {
          LOG.debug("unregistered class " + clazz.getName() + " from " + name);
        }
      }
    }
  }

  /**
   * Unregisters an object exported with registerObject.
   * <p>
   * The JSONBridge will unexport all instance methods and static methods of the particular object
   * under the name passed in as a key.
   *
   * @param key The named prefix of the object to unexport
   */
  public void unregisterObject(Object key) {
    synchronized (objectMap) {
      ObjectInstance oi = objectMap.get(key);
      if (oi.getObject() != null) {
        objectMap.remove(key);
        if (LOG.isDebugEnabled()) {
          LOG.debug("unregistered object " + oi.getObject().hashCode() + " of class " + oi
              .getClazz().getName() + " from " + key);
        }
      }
    }
  }

  /**
   * Get list of system methods that can be invoked on this JSONRPCBridge.
   *
   * These are the methods that are retrieved via a system.listMethods call from the client (like
   * when a new JSONRpcClient object is initialized by the browser side javascript.)
   *
   * @return A JSONArray of method names (in the format of Class.Method)
   */
  public JSONArray getSystemMethods() {
    Set<String> m = new TreeSet<String>();
    allStaticMethods(m);
    allInstanceMethods(m);
    allCallableReferences(m);
    JSONArray methodNames = new JSONArray();
    for (String methodName : m) {
      methodNames.put(methodName);
    }
    return methodNames;
  }

  /**
   * Add all methods on registered callable references to a HashSet.
   *
   * @param m Set to add all methods to.
   */
  private void allCallableReferences(Set<String> m) {
    synchronized (callableReferenceSet) {
      for (Class<?> clazz : callableReferenceSet) {
        ClassData cd = ClassAnalyzer.getClassData(clazz);

        uniqueMethods(m, CALLABLE_REFERENCE_METHOD_PREFIX + "[" + clazz.getName() + "].", cd
            .getStaticMethodMap());
        uniqueMethods(m, CALLABLE_REFERENCE_METHOD_PREFIX + "[" + clazz.getName() + "].", cd
            .getMethodMap());
      }
    }
  }

  /**
   * Add all instance methods that can be invoked on this bridge to a HashSet.
   *
   * @param m HashSet to add all static methods to.
   */
  private void allInstanceMethods(Set<String> m) {
    synchronized (objectMap) {
      for (Map.Entry<Object, ObjectInstance> oientry : objectMap.entrySet()) {
        Object key = oientry.getKey();
        if (!(key instanceof String)) {
          continue;
        }
        String name = (String) key;
        ObjectInstance oi = oientry.getValue();
        ClassData cd = ClassAnalyzer.getClassData(oi.getClazz());
        uniqueMethods(m, name + ".", cd.getMethodMap());
        uniqueMethods(m, name + ".", cd.getStaticMethodMap());
      }
    }
  }

  /**
   * Add all static methods that can be invoked on this bridge to the given HashSet.
   *
   * @param m HashSet to add all static methods to.
   */
  private void allStaticMethods(Set<String> m) {
    synchronized (classMap) {
      for (Map.Entry<String, Class<?>> cdentry : classMap.entrySet()) {
        String name = cdentry.getKey();
        Class<?> clazz = cdentry.getValue();
        ClassData cd = ClassAnalyzer.getClassData(clazz);
        uniqueMethods(m, name + ".", cd.getStaticMethodMap());
      }
    }
  }

  /**
   * Gets the methods that can be called on the given object.
   *
   * @param objectID The id of the object or 0 if it is a class
   * @param className The name of the class of the object - only required if objectID==0
   * @param methodName The name of method in the request
   * @return A map of AccessibleObjectKeys to a Collection of AccessibleObjects
   * @throws NoSuchMethodException If methods cannot be found for the class
   */
  private Map<AccessibleObjectKey, Set<AccessibleObject>> getAccessibleObjectMap(final int objectID,
      final String className, final String methodName) throws NoSuchMethodException

  {
    final Map<AccessibleObjectKey, Set<AccessibleObject>> methodMap =
        new HashMap<AccessibleObjectKey, Set<AccessibleObject>>();
    // if it is not an object
    if (objectID == 0) {
      final ObjectInstance oi = resolveObject(className);
      final ClassData classData = resolveClass(className);

      // Look up the class, object instance and method objects
      if (oi != null) {
        methodMap.putAll(ClassAnalyzer.getClassData(oi.getClazz()).getMethodMap());
      }
      // try to get the constructor data
      else if (CONSTRUCTOR_FLAG.equals(methodName)) {
        try {
          methodMap.putAll(ClassAnalyzer.getClassData(lookupClass(className)).getConstructorMap());
        } catch (Exception e) {
          throw (NoSuchMethodException) new NoSuchMethodException(
              FailedResult.MSG_ERR_NOCONSTRUCTOR).initCause(e);
        }
      }
      // else it must be static
      else if (classData != null) {
        methodMap.putAll(classData.getStaticMethodMap());
      } else {
        throw new NoSuchMethodException(FailedResult.MSG_ERR_NOMETHOD);
      }
    }
    // else it is an object, so we can get the member methods
    else {
      final ObjectInstance oi = resolveObject(objectID);
      if (oi == null) {
        throw new NoSuchMethodException("Object not found");
      }
      ClassData cd = ClassAnalyzer.getClassData(oi.getClazz());
      methodMap.putAll(cd.getMethodMap());
    }
    return methodMap;
  }

  /**
   * Resolves an objectId to an actual object.
   *
   * @param objectID The id of the object to resolve
   * @param className The name of the class of the object
   * @return The object requested
   */
  private Object getObjectContext(final int objectID, final String className) {
    final Object objectContext;
    if (objectID == 0) {
      final ObjectInstance oi = resolveObject(className);
      if (oi != null) {
        objectContext = oi.getObject();
      } else {
        objectContext = null;
      }
    } else {
      final ObjectInstance oi = resolveObject(objectID);
      if (oi != null) {
        objectContext = oi.getObject();
      } else {
        objectContext = null;
      }
    }
    return objectContext;
  }

  /**
   * Resolves a string to a class.
   *
   * @param className The name of the class to resolve
   * @return The data associated with the className
   */
  private ClassData resolveClass(String className) {
    Class<?> clazz;
    ClassData cd = null;

    synchronized (classMap) {
      clazz = classMap.get(className);
    }

    if (clazz != null) {
      cd = ClassAnalyzer.getClassData(clazz);
    }

    if (cd != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("found class " + cd.getClazz().getName() + " named " + className);
      }
      return cd;
    }

    return null;
  }

  /**
   * Resolve the key to a specified instance object. If an instance object of the requested key is
   * not found, and this is not the global bridge, then look in the global bridge too.
   * <p>
   * If the key is not found in this bridge or the global bridge, the requested key may be a class
   * method (static method) or may not exist (not registered under the requested key.)
   *
   * @param key registered object key being requested by caller.
   * @return ObjectInstance that has been registered under this key, in this bridge or the global
   *         bridge.
   */
  private ObjectInstance resolveObject(Object key) {
    ObjectInstance oi;
    synchronized (objectMap) {
      oi = objectMap.get(key);
    }
    if (LOG.isDebugEnabled() && oi != null) {
      LOG.debug("found object " + oi.getObject().hashCode() + " of class " + oi.getClazz().getName()
          + " with key " + key);
    }
    return oi;
  }
}