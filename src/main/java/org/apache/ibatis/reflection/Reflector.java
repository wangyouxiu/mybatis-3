/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 对应的类
   */
  private final Class<?> type;
  /**
   * 可读属性数组
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性数组
   */
  private final String[] writablePropertyNames;
  /**
   * setting方法映射
   *
   * key: 属性名
   * value: 方法
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * getting方法映射
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * setting方法参数映射
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * getting方法返回数据类型映射
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造方法
   */
  private Constructor<?> defaultConstructor;

  /**
   * 忽略大小写的属性集合
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    //设置默认构造器，就是获取无参构造
    addDefaultConstructor(clazz);
    //设置getting方法
    addGetMethods(clazz);
    //设置setting方法
    addSetMethods(clazz);
    //设置属性  有一些属性没有提供get或者set方法，所以在get、set方法设定的时候，会漏掉这些属性
    addFields(clazz);
    //设置可读属性
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    //设置可写属性
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    //设置不区分大小写的属性集合   可读属性+可写属性
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    //获取当前clazz中声明的构造器
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    //流式便利，过滤出参数列表长度为0的构造器，也就是无参构造，如果找到，就赋值给defaultConstructor属性
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    //获取类的方法
    Method[] methods = getClassMethods(clazz);
    //流式便利方法，过滤出无参、且方法名为“get”或“is”开头 的方法，也就是getting方法
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      // PropertyNamer.methodToProperty(m.getName())  根据方法名获取属性名
      //将getting方法放入映射，key为属性名，value为get方法集合
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    //解决getting方法冲突 最终一个属性只能保留一个对应的方法
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    //遍历获取到的Map集合
    //遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 getting 方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      //是否有歧义，模棱两可
      boolean isAmbiguous = false;
      //遍历当前属性对应的方法集合
      for (Method candidate : entry.getValue()) {
        //首次进入，winner为null，将第一个方法赋值给winner，如果集合中有多个方法，第二次进来winner就不再为null
        if (winner == null) {
          winner = candidate;
          continue;
        }
        //winner不等于null，获取了winner与当前候选人的返回值类型
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        //判断两者返回值类型是否相同
        if (candidateType.equals(winnerType)) {
          //判断返回是否是布尔类型
          //只有boolean类型的返回值运行有两个相同的方法
          if (!boolean.class.equals(candidateType)) {
            //返回值非boolean类型，设置为有歧义
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            //boolean类型的返回值，以is开头的方法被认为是最合适的
            winner = candidate;
          }
          //candidateType 是 winnerType 的超类或者超接口 则winner是最合适的
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          //winnerType 是 candidateType 的超类或者超接口 则candidate是最合适的
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          //上述条件都没达成，说明有歧义
          isAmbiguous = true;
          break;
        }
      }
      /**
       * 将解决冲突后的getting方法加入，如果是有歧义的，那就生成歧义方法调用者
       */
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    //如果有歧义，就生成AmbiguousMethodInvoker(调用时会抛出自定义异常)，否则生成正常方法
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    getMethods.put(name, invoker);
    //获取getting方法的返回数据类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    //根据type获取真正的class
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    //判读是否是有效的属性名
    if (isValidPropertyName(name)) {
      //获取key为当前属性名的方法集合，如果不存在或者为null，就返回一个新的集合
      //JDK8的写法，针不戳
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      //将当前方法加入这个集合
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey();
      List<Method> setters = entry.getValue();
      //由getting方法返回的数据类型，来确定setting方法的参数类型
      Class<?> getterType = getTypes.get(propName);
      //如果getMethods中获取的Invoker是AmbiguousMethodInvoker子类，则说明当前get方法是有歧义的
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      //默认setting方法是没有歧义的
      boolean isSetterAmbiguous = false;
      Method match = null;
      //遍历当前属性的setting方法
      for (Method setter : setters) {
        //如果当前属性的get方法没有歧义，并且set方法的第一个参数类型是等于get方法的返回的类型，说明当前set方法是最优的
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        /**
         * 如果当前的set方法没有歧义，则进行进一步的选择。
         * 如果当前的set方法已经有歧义，则不再进行选择。(反正比较完了之后都会生成歧义的调用方法，后续的选择已经没有必要)
         */
        if (!isSetterAmbiguous) {
          /**
           * 如果match、setter第一个参数类型没有任何关系，就会创建产生歧义的方法调用。
           * 对于歧义的方法调用，pickBetterSetter方法会在内部将值赋给setMethods、setTypes，然后返回null
           * 因此，如果match==null，就说明已经产生了歧义方法
           */
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      /**
       * match == null 时，说明产生歧义方法，此时不需处理，因为pickBetterSetter方法已经处理了后续逻辑
       * 否则，说明并非歧义方法，而是经过选择得到了唯一的最优方法，则应该做后续处理
       */
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    //获取到两个方法的第一个参数的类型
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    //如果方法1的参数是方法二参数的超类或者超接口
    /**
     * 也就是选择类型给小的作为更合适的接口
     * 因为遍历是由子类向父类进行的
     */
    if (paramType1.isAssignableFrom(paramType2)) {
      //则方法二是更合适的方法
      return setter2;
      //反之，则认为方法一更合适
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    //如果两个参数在类型上毫不相关，即没有父子类关系，则创建有歧义的方法调用者
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    //获取所有声明的属性
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      //当前属性不存在 setMethods 中
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        //当前属性是非final且非static,将当前field加入
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      //不存在getMethods中
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    //处理父类
    if (clazz.getSuperclass() != null) {
      //递归
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    //先判断当前属性名是否合法
    if (isValidPropertyName(field.getName())) {
      //因为当前属性是不提供set方法的，所以创建的实例是 SetFieldInvoker
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    //每个方法的签名与该方法的映射
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    //循环类，类的父类，父类的父类，直到Object
    while (currentClass != null && currentClass != Object.class) {
      //将当前类中的方法增加在映射中
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      //将当前类设置为其父类，达到遍历效果
      currentClass = currentClass.getSuperclass();
    }

    //将方法数组返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    //遍历方法
    for (Method currentMethod : methods) {
      //判断当前方法是否是桥接方法，忽略了桥接方法
      if (!currentMethod.isBridge()) {
        //获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        //如果当前方法签名不存在 uniqueMethods 中，就put
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
