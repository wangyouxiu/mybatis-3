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

import static com.googlecode.catchexception.apis.BDDCatchException.*;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.*;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class ReflectorTest {

  @Test
  void testGetSetterType() {
    /**
     * 创建默认工程ReflectorFactory，该工厂默认开启缓存
     */
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //从默认工厂中获取Reflector实例
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    Class<?> idClass = reflector.getSetterType("id");
    if (idClass.equals(Long.class)) {
      log.info("id属性的set方法，入参的类型为:{}", JSON.toJSONString(idClass));
    }
  }

  @Test
  void testGetGetterType() {
    //测试getting方法的返回属性
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    Class<?> idReturnClass = reflector.getGetterType("id");
    if (idReturnClass.equals(Long.class)) {
      log.info("id属性的get方法返回数据类型:{}", JSON.toJSONString(idReturnClass));
    }
  }

  @Test
  void shouldNotGetClass() {
    //hasGetter方法测试
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    Assertions.assertFalse(reflector.hasGetter("class"));
  }

  interface Entity<T> {
    T getId();

    void setId(T id);
  }

  static abstract class AbstractEntity implements Entity<Long> {

    private Long id;

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public void setId(Long id) {
      this.id = id;
    }
  }

  static class Section extends AbstractEntity implements Entity<Long> {
  }

  @Test
  void shouldResolveSetterParam() {
    //测试的类换成了Child
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(String.class, reflector.getSetterType("id"));
  }

  @Test
  void shouldResolveParameterizedSetterParam() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(List.class, reflector.getSetterType("list"));
  }

  @Test
  void shouldResolveArraySetterParam() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    Class<?> clazz = reflector.getSetterType("array");
    //判断当前类型是否是一个数组
    assertTrue(clazz.isArray());
    //判断数组的类型是不是String类型
    assertEquals(String.class, clazz.getComponentType());
  }

  @Test
  void shouldResolveGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(String.class, reflector.getGetterType("id"));
  }

  @Test
  void shouldResolveSetterTypeFromPrivateField() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(String.class, reflector.getSetterType("fld"));
  }

  @Test
  void shouldResolveGetterTypeFromPublicField() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //pubFld 属性没有提供get方法，所以getMethods中创建的GetFieldInvoker实例，而返回值类型就是属性的类型
    assertEquals(String.class, reflector.getGetterType("pubFld"));
  }

  @Test
  void shouldResolveParameterizedGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(List.class, reflector.getGetterType("list"));
  }

  @Test
  void shouldResolveArrayGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    Class<?> clazz = reflector.getGetterType("array");
    assertTrue(clazz.isArray());
    assertEquals(String.class, clazz.getComponentType());
  }

  static abstract class Parent<T extends Serializable> {
    protected T id;
    protected List<T> list;
    protected T[] array;
    private T fld;
    public T pubFld;

    public T getId() {
      return id;
    }

    public void setId(T id) {
      this.id = id;
    }

    public List<T> getList() {
      return list;
    }

    public void setList(List<T> list) {
      this.list = list;
    }

    public T[] getArray() {
      return array;
    }

    public void setArray(T[] array) {
      this.array = array;
    }

    public T getFld() {
      return fld;
    }
  }

  static class Child extends Parent<String> {
  }

  @Test
  void shouldResoleveReadonlySetterWithOverload() {
    class BeanClass implements BeanInterface<String> {
      @Override
      public void setId(String id) {
        // Do nothing
      }
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(BeanClass.class);
    assertEquals(String.class, reflector.getSetterType("id"));
  }

  interface BeanInterface<T> {
    void setId(T id);
  }

  @Test
  void shouldSettersWithUnrelatedArgTypesThrowException() throws Exception {
    /**
     * setProp2三个方法会产生歧义
     */
    @SuppressWarnings("unused")
    class BeanClass {
      public void setProp1(String arg) {}
      public void setProp2(String arg) {}
      public void setProp2(Integer arg) {}
      public void setProp2(boolean arg) {}
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(BeanClass.class);

    List<String> setableProps = Arrays.asList(reflector.getSetablePropertyNames());
    assertTrue(setableProps.contains("prop1"));
    assertTrue(setableProps.contains("prop2"));
    assertEquals("prop1", reflector.findPropertyName("PROP1"));
    assertEquals("prop2", reflector.findPropertyName("PROP2"));

    assertEquals(String.class, reflector.getSetterType("prop1"));
    assertNotNull(reflector.getSetInvoker("prop1"));

    Class<?> paramType = reflector.getSetterType("prop2");
    assertTrue(String.class.equals(paramType) || Integer.class.equals(paramType) || boolean.class.equals(paramType));

    //这里，因为prop2属性有三个set方法，并且三个set方法的第一个参数类型都不相同，且没有子父类关系，所以生成了歧义方法
    Invoker ambiguousInvoker = reflector.getSetInvoker("prop2");
    //因为是歧义方法，这里构建参数时做了判断
    Object[] param = String.class.equals(paramType)? new String[]{"x"} : new Integer[]{1};
    /**
     * 直接调用，不捕获异常，让异常直接抛出
     * {@link AmbiguousMethodInvoker#invoke(java.lang.Object, java.lang.Object[])}
     * 歧义方法的invoke直接将异常抛出
     */
    ambiguousInvoker.invoke(new BeanClass(), param);

    /**
     * junit的处理异常方式，写法蛮有意思的，可以参考下面这篇文章
     * https://blog.csdn.net/dnc8371/article/details/107267815?ops_request_misc=%25257B%252522request%25255Fid%252522%25253A%252522160921991016780257451000%252522%25252C%252522scm%252522%25253A%25252220140713.130102334.pc%25255Fall.%252522%25257D&request_id=160921991016780257451000&biz_id=0&utm_medium=distribute.pc_search_result.none-task-blog-2~all~first_rank_v2~rank_v29-1-107267815.pc_search_result_no_baidu_js&utm_term=com.googlecode.catchexception.apis.BDDCatchException#when
     */
    when(() -> ambiguousInvoker.invoke(new BeanClass(), param));
    then(caughtException()).isInstanceOf(ReflectionException.class)
        .hasMessageMatching(
            "Ambiguous setters defined for property 'prop2' in class '" + BeanClass.class.getName().replace("$", "\\$")
                + "' with types '(java.lang.String|java.lang.Integer|boolean)' and '(java.lang.String|java.lang.Integer|boolean)'\\.");
  }

  @Test
  void shouldTwoGettersForNonBooleanPropertyThrowException() throws Exception {
    //这里会生成prop2的歧义方法
    @SuppressWarnings("unused")
    class BeanClass {
      public Integer getProp1() {return 1;}
      public int getProp2() {return 0;}
      public int isProp2() {return 0;}
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(BeanClass.class);

    List<String> getableProps = Arrays.asList(reflector.getGetablePropertyNames());
    assertTrue(getableProps.contains("prop1"));
    assertTrue(getableProps.contains("prop2"));
    assertEquals("prop1", reflector.findPropertyName("PROP1"));
    assertEquals("prop2", reflector.findPropertyName("PROP2"));

    assertEquals(Integer.class, reflector.getGetterType("prop1"));
    Invoker getInvoker = reflector.getGetInvoker("prop1");
    BeanClass beanClass = new BeanClass();
    Object invoke = getInvoker.invoke(beanClass, null);
    log.info("invoke==>:{}", JSON.toJSONString(invoke));


    Class<?> paramType = reflector.getGetterType("prop2");
    assertEquals(int.class, paramType);

    Invoker ambiguousInvoker = reflector.getGetInvoker("prop2");
    Object invoke1 = ambiguousInvoker.invoke(new BeanClass(), new Integer[]{1});


    when(() -> ambiguousInvoker.invoke(new BeanClass(), new Integer[] {1}));
    then(caughtException()).isInstanceOf(ReflectionException.class)
        .hasMessageContaining("Illegal overloaded getter method with ambiguous type for property 'prop2' in class '"
            + BeanClass.class.getName()
            + "'. This breaks the JavaBeans specification and can cause unpredictable results.");
  }

  @Test
  void shouldTwoGettersWithDifferentTypesThrowException() throws Exception {
    //还是会生成歧义的方法调用者
    @SuppressWarnings("unused")
    class BeanClass {
      public Integer getProp1() {return 1;}
      public Integer getProp2() {return 1;}
      public boolean isProp2() {return false;}
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(BeanClass.class);

    List<String> getableProps = Arrays.asList(reflector.getGetablePropertyNames());
    assertTrue(getableProps.contains("prop1"));
    assertTrue(getableProps.contains("prop2"));
    assertEquals("prop1", reflector.findPropertyName("PROP1"));
    assertEquals("prop2", reflector.findPropertyName("PROP2"));

    assertEquals(Integer.class, reflector.getGetterType("prop1"));
    Invoker getInvoker = reflector.getGetInvoker("prop1");
    assertEquals(Integer.valueOf(1), getInvoker.invoke(new BeanClass(), null));

    Class<?> returnType = reflector.getGetterType("prop2");
    assertTrue(Integer.class.equals(returnType) || boolean.class.equals(returnType));

    Invoker ambiguousInvoker = reflector.getGetInvoker("prop2");
    when(() -> ambiguousInvoker.invoke(new BeanClass(), null));
    then(caughtException()).isInstanceOf(ReflectionException.class)
        .hasMessageContaining("Illegal overloaded getter method with ambiguous type for property 'prop2' in class '"
            + BeanClass.class.getName()
            + "'. This breaks the JavaBeans specification and can cause unpredictable results.");
  }

  @Test
  void shouldAllowTwoBooleanGetters() throws Exception {
    /**
     * 当两个get方法且返回数据类型为boolean，发生冲突时，以is开头的方法被认为是最优的方法
     */
    @SuppressWarnings("unused")
    class Bean {
      // JavaBean Spec allows this (see #906)
      public boolean getBool() {return false;}
      public boolean isBool() {return true;}
      public void setBool(boolean bool) {}
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Bean.class);
    assertTrue((Boolean)reflector.getGetInvoker("bool").invoke(new Bean(), new Byte[0]));
  }

  @Test
  void shouldIgnoreBestMatchSetterIfGetterIsAmbiguous() throws Exception {
    /**
     * get、set方法都会产生歧义
     */
    @SuppressWarnings("unused")
    class Bean {
      public Integer isBool() {return Integer.valueOf(1);}
      public Integer getBool() {return Integer.valueOf(2);}
      public void setBool(boolean bool) {}
      public void setBool(Integer bool) {}
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Bean.class);
    Class<?> paramType = reflector.getSetterType("bool");
    Object[] param = boolean.class.equals(paramType) ? new Boolean[] { true } : new Integer[] { 1 };
    Invoker ambiguousInvoker = reflector.getSetInvoker("bool");
    when(() -> ambiguousInvoker.invoke(new Bean(), param));
    then(caughtException()).isInstanceOf(ReflectionException.class)
        .hasMessageMatching(
            "Ambiguous setters defined for property 'bool' in class '" + Bean.class.getName().replace("$", "\\$")
                + "' with types '(java.lang.Integer|boolean)' and '(java.lang.Integer|boolean)'\\.");
  }
}
