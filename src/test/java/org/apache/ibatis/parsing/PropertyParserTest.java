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
package org.apache.ibatis.parsing;

import java.util.Properties;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PropertyParserTest {

  @Test
  void replaceToVariableValue() {
    Properties props = new Properties();
    //开启默认值功能
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    props.setProperty("key", "value");
    props.setProperty("tableName", "members");
    props.setProperty("orderColumn", "member_id");
    props.setProperty("a:b", "c");
//    props.setProperty("a", "值存在就不会返回默认值");
    Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("value");
    Assertions.assertThat(PropertyParser.parse("${key:aaaa}", props)).isEqualTo("value");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props)).isEqualTo("SELECT * FROM members ORDER BY member_id");

//    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");
    /**
     * 1.开启默认值转换
     *   由于没有设置分隔符，默认的分隔符是  “:”
     *   则 ${a:b} 会被解析，“:”之后的 b 会被认为是 key a 的默认值
     *      之后会使用key a查找配置，而Properties 中没有key 为 a 的配置，所以会返回默认值，也就是b
     *      尝试，在Properties增加一个key为a的配置，看看会返回什么
     *      props.setProperty("a", "值存在就不会返回默认值")
     *
     * 2.不开启默认值转换
     *   由于没有设置分隔符，默认的分隔符是  “:”
     *   不会解析 ${a:b} 中间是否存在 ":" ，认为 “a:b” 整体是一个key，此时查询就会得到预设的value，也就是c
     */
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("b");

    //移除 是否开启默认值的配置  此配置项默认是为false
    // 因此和   props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");   效果一样
    props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("c");

  }

  @Test
  void notReplace() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    /**
     * Properties实例中没有任何的配置数据，仅设置是否开启默认配置
     * 在没有配置值的情况下
     * 实际调用的代码  {@link PropertyParser.VariableTokenHandler#handleToken(java.lang.String)}
     * 1.默认配置开启
     *    以${key}举例
     *    不包含":"，所以默认值就是null.最终会返回${key}
     *    以${a:b}举例
     *    包含":"，所以默认值是b，最终调用 {@link Properties#getProperty(java.lang.String, java.lang.String)}
     *    如果Properties中存在key为a的配置，则返回其配置，否则返回其默认值
     *    这里没有配置，所以返回b
     *
     * 2.默认配置关闭
     *    不会校验是否包含":",判断Properties中是否存在配置，存在返回对应配置，否则原样返回
     */
    Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("${key}");
    Assertions.assertThat(PropertyParser.parse("${key}", null)).isEqualTo("${key}");

    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");

    props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");

  }

  @Test
  void applyDefaultValue() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    Assertions.assertThat(PropertyParser.parse("${key:default}", props)).isEqualTo("default");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props)).isEqualTo("SELECT * FROM users ORDER BY id");
    Assertions.assertThat(PropertyParser.parse("${key:}", props)).isEmpty();
    Assertions.assertThat(PropertyParser.parse("${key: }", props)).isEqualTo(" ");
    Assertions.assertThat(PropertyParser.parse("${key::}", props)).isEqualTo(":");
  }

  @Test
  void applyCustomSeparator() {
    /**
     * 在开启默认值的配置上增加了设置分隔符的配置
     */
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    props.setProperty(PropertyParser.KEY_DEFAULT_VALUE_SEPARATOR, "?:");
    Assertions.assertThat(PropertyParser.parse("${key?:default}", props)).isEqualTo("default");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${schema?:prod}.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}", props)).isEqualTo("SELECT * FROM prod.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}");
    Assertions.assertThat(PropertyParser.parse("${key?:}", props)).isEmpty();
    Assertions.assertThat(PropertyParser.parse("${key?: }", props)).isEqualTo(" ");
    Assertions.assertThat(PropertyParser.parse("${key?::}", props)).isEqualTo(":");
  }

}
