package org.jabsorb.security;

import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jabsorb.serializer.UnmarshallException;

/**
 * Controls which classes can get unmarshalled.
 * 
 * @author Christian Kohlschütter
 */
public final class ClassResolver {
  private static final Pattern PAT_ARRAY = Pattern.compile("^([\\[]+[L]?)(.*?)([;]*)$");
  private static final Set<String> DEFAULT_ALLOWED_CLASSES = Set.of("java.lang.Exception");

  /**
   * Sentinel class.
   */
  private static final class NotAccessible {
    private NotAccessible() {
      throw new IllegalStateException("No instances");
    }
  }

  private final Set<String> allowedClasses;
  private final WeakHashMap<String, Class<?>> cachedDecisions = new WeakHashMap<>();

  public static ClassResolver withDefaults() {
    return withAllowedClassNames(DEFAULT_ALLOWED_CLASSES);
  }

  public static ClassResolver withAllowedClasses(Set<Class<?>> allowedClasses) {
    return new ClassResolver(allowedClasses.stream().map((k) -> k.getName()).collect(Collectors
        .toSet()));
  }

  public static ClassResolver withAllowedClassNames(Set<String> allowedClasses) {
    return new ClassResolver(allowedClasses);
  }

  private ClassResolver(Set<String> allowedClasses) {
    this.allowedClasses = allowedClasses;
  }

  public Class<?> tryResolve(String className) {
    Class<?> klazz = cachedDecisions.get(className);
    if (klazz == NotAccessible.class) {
      return null;
    } else if (klazz != null) {
      return klazz;
    }

    try {
      if (!allowedClasses.contains(className)) {
        Matcher m = PAT_ARRAY.matcher(className);
        if (!m.find()) {
          return klazz = null;
        }
        String basicClassName = m.group(2);
        if (!allowedClasses.contains(basicClassName)) {
          return klazz = null;
        }
      }

      try {
        klazz = Class.forName(className);
      } catch (ClassNotFoundException e) {
        return klazz = null;
      }

      return klazz;
    } finally {
      cachedDecisions.put(className, klazz == null ? NotAccessible.class : klazz);
    }
  }

  public Class<?> resolveOrThrow(String className) throws UnmarshallException {
    Class<?> klazz = tryResolve(className);
    if (klazz == null) {
      throw new UnmarshallException("Could not resolve class: " + className);
    }
    return klazz;
  }
}
