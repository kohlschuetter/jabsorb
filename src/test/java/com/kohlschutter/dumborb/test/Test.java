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
package com.kohlschutter.dumborb.test;

import java.io.Serializable;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import org.json.JSONObject;

import com.kohlschutter.dumborb.JSONRPCBridge;
import com.kohlschutter.dumborb.callback.InvocationCallback;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Server side unit tests, used by unit.jsp / unit.js.
 */
public class Test implements Serializable, ITest {

  private static final long serialVersionUID = 2;

  // Void test

  @Override
  public void voidFunction() {
    // Void function does nothing
  }

  // Exception tests

  public static void throwException() throws Exception {
    throw new Exception("test exception");
  }

  // Overload tests

  @Override
  public String[] echo(String[] strings) {
    return strings;
  }

  @Override
  public int echo(int i) {
    return i;
  }

  @Override
  public int[] echo(int i[]) {
    return i;
  }

  @Override
  public String echo(String message) {
    return message;
  }

  // Type tests

  @Override
  public Object[] echoArray(Object[] object) {
    return object;
  }

  @Override
  public List<?> echoList(List<?> l) {
    return l;
  }

  @Override
  public byte[] echoByteArray(byte ba[]) {
    return ba;
  }

  @Override
  public char[] echoCharArray(char ca[]) {
    return ca;
  }

  @Override
  public char echoChar(char c) {
    return c;
  }

  @Override
  public boolean echoBoolean(boolean b) {
    return b;
  }

  @Override
  public boolean[] echoBooleanArray(boolean ba[]) {
    return ba;
  }

  @Override
  public Integer[] echoIntegerArray(Integer i[]) {
    return i;
  }

  @Override
  public Integer echoIntegerObject(Integer i) {
    return i;
  }

  @Override
  public Long echoLongObject(Long l) {
    return l;
  }

  @Override
  public Float echoFloatObject(Float f) {
    return f;
  }

  @Override
  public Double echoDoubleObject(Double d) {
    return d;
  }

  @Override
  public Date echoDateObject(Date d) {
    return d;
  }

  @Override
  public java.sql.Date echoSQLDateObject(java.sql.Date d) {
    return d;
  }

  @Override
  public Object echoObject(Object o) {
    return o;
  }

  @Override
  public Object echoObjectArray(Object[] o) {
    return o;
  }

  @Override
  public String echoOverloadedObject(Number i) {
    return "number method";
  }

  @Override
  public String echoOverloadedObject(Boolean s) {
    return "boolean method";
  }

  public JSONObject echoRawJSON(JSONObject rawObject) {
    return rawObject;
  }

  // Container tests

  @Override
  public int[] anArray() {
    int arr[] = new int[10];
    for (int i = 0; i < 10; i++) {
      arr[i] = i;
    }
    return arr;
  }

  public enum Suit {
    CLUBS, DIAMONDS, HEARTS, SPADES
  }

  @Override
  public Enum<?> anEnum() {
    return Suit.CLUBS;
  }

  @Override
  public ArrayList<Integer> anArrayList() {
    ArrayList<Integer> al = new ArrayList<Integer>();
    for (int i = 10; i < 20; i++) {
      al.add(i);
    }
    return al;
  }

  @Override
  public Vector<Integer> aVector() {
    Vector<Integer> v = new Vector<Integer>();
    for (int i = 20; i < 30; i++) {
      v.add(i);
    }
    return v;
  }

  @Override
  public List<Integer> aList() {
    List<Integer> l = new Vector<Integer>();
    for (int i = 20; i < 30; i++) {
      l.add(i);
    }
    return l;
  }

  @Override
  public Set<Integer> aSet() {
    Set<Integer> s = new HashSet<Integer>();
    for (int i = 0; i < 5; i++) {
      s.add(i);
    }
    return s;
  }

  @Override
  public Hashtable<Integer, ITest.Wiggle> aHashtable() {
    Hashtable<Integer, ITest.Wiggle> ht = new Hashtable<Integer, ITest.Wiggle>();
    for (int i = 0; i < 3; i++) {
      ITest.Wiggle w = new ITest.Wiggle();
      w.setFoo("foo " + i);
      w.setBar(i);
      ht.put(i, w);
    }
    return ht;
  }

  // circular reference tests

  @Override
  public BeanA aBean() {
    BeanA beanA = new BeanA();
    BeanB beanB = new BeanB();

    beanB.setBeanA(beanA);
    beanB.setId(beanB.hashCode());
    beanA.setBeanB(beanB);
    beanA.setId(beanA.hashCode());

    return beanA;
  }

  public Map<String, Object> aCircRefMap() {
    Map<String, Object> m = new HashMap<String, Object>();
    m.put("me", m);
    return m;
  }

  public List<Object> aCircRefList() {
    List<Object> list = new ArrayList<Object>();
    list.add(0);
    Integer one = 1;
    list.add(one);
    Integer two = 2;
    list.add(two);

    Map<Object, Object> m = new HashMap<Object, Object>();
    m.put(0, "zero");
    m.put(one, "one");
    m.put(two, "two");
    m.put("buckle_my_shoe", list);

    BeanA beanA = new BeanA();
    BeanB beanB = new BeanB();
    beanB.setBeanA(beanA);
    beanA.setBeanB(beanB);

    m.put("aBean", beanA);

    list.add(beanB);
    list.add(m);
    return list;
  }

  /**
   * Test more than one duplicate, to make sure the fixups they generate all refer to the same
   * object
   *
   * @return a List with some duplicates.
   */
  public List<Object> aDupDup() {
    List<Object> list = new ArrayList<Object>();

    BeanA a = new BeanA();
    BeanB b = new BeanB();

    BeanA c = new BeanA();
    BeanB d = new BeanB();

    a.setBeanB(d);
    b.setBeanA(c);

    list.add(a);
    list.add(b);
    list.add(c);
    list.add(d);

    return list;
  }

  /**
   * Another duplicate with substantial savings to be gained by fixing it up
   *
   * @return aList with duplicates.
   */
  public List<Map<String, Object>> aDupDupDup() {
    Map<String, Object> m = new HashMap<String, Object>();
    m.put("drink", "soda");
    m.put("tree", "oak");
    m.put("planet", "jupiter");
    m.put("art", "painting");
    m.put("animal", "tiger");
    List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

    list.add(m);
    list.add(m);
    list.add(m);
    list.add(m);

    Map<String, Object> m2 = new TreeMap<String, Object>();
    m2.put("map", m);
    m2.put("dup", m);
    m2.put("copy", m);
    m2.put("ditto", m);
    m2.put("extra", m);

    list.add(m2);
    return list;
  }

  /**
   * Test of duplicate Strings
   *
   * @return a List with 3 duplicate Strings.
   */
  public List<String> aStringListDup() {
    List<String> list = new ArrayList<String>();

    String dup = "Supercalifragilisticexpialidocious";
    list.add(dup);
    list.add(dup);
    list.add(dup);
    return list;
  }

  /**
   * Test an array of 3 duplicate Strings.
   *
   * @return an array of 3 duplicate Strings.
   */
  public String[] aStringArrayDup() {
    String[] arr = new String[3];
    String dup = "Supercalifragilisticexpialidocious";
    arr[0] = dup;
    arr[1] = dup;
    arr[2] = dup;
    return arr;
  }

  /**
   * Test an array of 3 duplicate Beans.
   *
   * @return an array of 3 duplicate Beans.
   */
  public BeanA[] aBeanArrayDup() {
    BeanB b = new BeanB();
    BeanA a = new BeanA();
    a.setBeanB(b);
    BeanA[] arr = new BeanA[3];
    arr[0] = a;
    arr[1] = a;
    arr[2] = a;
    return arr;
  }

  /**
   * Return a List that has several Strings and a few nulls. We want make sure that the null objects
   * don't get fixed up (as duplicates...)
   *
   * @return a List that has several Strings and a few nulls.
   */
  public List<String> listNull() {
    List<String> l = new ArrayList<String>();
    l.add("one");
    l.add("two");
    l.add(null);
    l.add("my");
    l.add("shoe");
    l.add(null);
    l.add(null);
    l.add(null);
    return l;
  }

  @Override
  public Map<Object, Object> nullKeyedMap() {
    Map<Object, Object> m = new HashMap<Object, Object>();
    m.put("normalKey", "normal value");
    m.put(null, "Null value");
    return m;
  }

  // Misc tests

  @Override
  public String[] twice(String string) {
    return new String[] {string, string};
  }

  @Override
  public String concat(String msg1, String msg2) {
    return msg1 + " and " + msg2;
  }

  // Bean tests

  @Override
  public ITest.Wiggle echo(ITest.Wiggle wiggle) {
    return wiggle;
  }

  @Override
  public ITest.Waggle echo(ITest.Waggle waggle) {
    return waggle;
  }

  @Override
  public ArrayList<ITest.Wiggle> aWiggleArrayList(int numWiggles) {
    ArrayList<ITest.Wiggle> al = new ArrayList<ITest.Wiggle>();
    for (int i = 0; i < numWiggles; i++) {
      al.add(new ITest.Wiggle(i));
    }
    return al;
  }

  @Override
  public ArrayList<ITest.Waggle> aWaggleArrayList(int numWaggles) {
    ArrayList<ITest.Waggle> al = new ArrayList<ITest.Waggle>();
    for (int i = 0; i < numWaggles; i++) {
      al.add(new ITest.Waggle(i));
    }
    return al;
  }

  @Override
  public String wigOrWag(ArrayList<?> al) {
    Iterator<?> i = al.iterator();
    StringBuffer buf = new StringBuffer();
    while (i.hasNext()) {
      Object o = i.next();
      if (o instanceof ITest.Wiggle) {
        ITest.Wiggle w = (ITest.Wiggle) o;
        buf.append(w + " ");
      } else if (o instanceof ITest.Waggle) {
        ITest.Waggle w = (ITest.Waggle) o;
        buf.append(w + " ");
      } else {
        buf.append("unknown object ");
      }
    }
    return buf.toString();
  }

  // Reference Tests
  public static class CallableRefTest implements Serializable, Comparable<Object> {

    private static final long serialVersionUID = 2;

    private static Test.RefTest ref = new Test.RefTest("a secret");

    public String ping() {
      return "ping pong";
    }

    public Test.RefTest getRef() {
      return ref;
    }

    public String whatsInside(Test.RefTest r) {
      return r.toString();
    }

    @Override
    public int compareTo(Object arg0) {
      return System.identityHashCode(this) - System.identityHashCode(arg0);
    }
  }

  public static class RefTest implements Serializable {

    private static final long serialVersionUID = 2;

    private String s;

    public RefTest(String s) {
      this.s = s;
    }

    @Override
    public String toString() {
      return s;
    }
  }

  private static CallableRefTest callableRef = new CallableRefTest();

  public CallableRefTest getCallableRef() {
    return callableRef;
  }

  public Vector<CallableRefTest> getCallableRefVector() {
    Vector<CallableRefTest> v = new Vector<CallableRefTest>();
    v.add(callableRef);
    v.add(callableRef);
    return v;
  }

  public Vector<Vector<CallableRefTest>> getCallableRefInnerVector() {
    Vector<Vector<CallableRefTest>> v1 = new Vector<Vector<CallableRefTest>>();
    Vector<CallableRefTest> v = new Vector<CallableRefTest>();
    v.add(callableRef);
    v.add(callableRef);
    v1.add(v);
    return v1;
  }

  public Map<String, CallableRefTest> getCallableRefMap() {
    Map<String, CallableRefTest> m = new TreeMap<String, CallableRefTest>();
    m.put("a", callableRef);
    m.put("b", callableRef);
    return m;
  }

  public Set<CallableRefTest> getCallableRefSet() {
    Set<CallableRefTest> s = new TreeSet<CallableRefTest>();
    s.add(callableRef);
    return s;
  }

  // Callback tests

  @SuppressWarnings("null")
  public void setCallback(JSONRPCBridge bridge, boolean flag) {
    if (flag) {
      bridge.registerCallback(cb, HttpServletRequest.class);
    } else {
      bridge.unregisterCallback(cb, HttpServletRequest.class);
    }
  }

  public static InvocationCallback<HttpServletRequest> cb =
      new InvocationCallback<HttpServletRequest>() {
        @Override
        public void preInvoke(HttpServletRequest context, Object instance, AccessibleObject m,
            Object arguments[]) throws Exception {
          System.out.print("Test.preInvoke");
          if (instance != null) {
            System.out.print(" instance=" + instance);
          }
          System.out.print(" method=" + ((Method) m).getName());
          for (int i = 0; i < arguments.length; i++) {
            System.out.print(" arg[" + i + "]=" + arguments[i]);
          }
          System.out.println("");
        }

        @Override
        public void postInvoke(HttpServletRequest context, Object instance, AccessibleObject m,
            Object result, Throwable error) throws Exception {
          // Nothing done
        }
      };

  /**
   * Count the number of true booleans in the Map.
   *
   * @param in input map.
   * @return number of booleans in the map that were set to true.
   */
  public int trueBooleansInMap(Map<Object, Object> in) {
    int numTrue = 0;
    for (Iterator<Object> i = in.keySet().iterator(); i.hasNext();) {
      Object key = i.next();
      Object value = in.get(key);
      if (value instanceof Boolean && ((Boolean) value).booleanValue()) {
        numTrue++;
      }
    }
    return numTrue;
  }

  @Override
  public Map<?, ?> complexKeyedMap() {
    Map<CallableRefTest, CallableRefTest> map = new HashMap<CallableRefTest, CallableRefTest>();

    CallableRefTest a = new CallableRefTest();
    CallableRefTest b = new CallableRefTest();
    CallableRefTest c = new CallableRefTest();
    CallableRefTest d = new CallableRefTest();
    map.put(a, b);
    map.put(c, d);
    return map;
  }

}
