package org.jabsorb;

/**
 * Container for objects of which instances have been made
 */
final class ObjectInstance {
  /**
   * The class the object is of
   */
  private final Class<?> clazz;

  /**
   * The object for the instance
   */
  private final Object object;

  /**
   * Creates a new ObjectInstance
   *
   * @param object The object for the instance
   */
  ObjectInstance(Object object) {
    this.object = object;
    this.clazz = object.getClass();
  }

  /**
   * Creates a new ObjectInstance
   *
   * @param object The object for the instance
   * @param clazz The class the object is of
   */
  public ObjectInstance(Object object, Class<?> clazz) {
    if (!clazz.isInstance(object)) {
      throw new ClassCastException("Attempt to register jsonrpc object with invalid class.");
    }
    this.object = object;
    this.clazz = clazz;
  }

  /**
   * Gets the class the object is of
   *
   * @return The class the object is of
   */
  public Class<?> getClazz() {
    return clazz;
  }

  /**
   * Gets the object for the instance
   *
   * @return the object for the instance
   */
  public Object getObject() {
    return object;
  }
}
