/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 一个带有日志的缓存实现，内部持有一个Cache对象，再其基础上进行日志相关处理
 * @author Clinton Begin
 */
public class LoggingCache implements Cache {

  private final Log log;
  private final Cache delegate;
  /**
   * 记录请求次数
   */
  protected int requests = 0;
  /**
   * 计算命中率
   */
  protected int hits = 0;

  public LoggingCache(Cache delegate) {
    this.delegate = delegate;
    this.log = LogFactory.getLog(getId());
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  /**
   * 获取缓存时，进行日志相关处理
   * @param key
   *          The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    //统计请求次数
    requests++;
    //被装饰者处理获取缓存的逻辑
    final Object value = delegate.getObject(key);
    //value!=null 说明缓存命中
    if (value != null) {
      hits++;
    }
    if (log.isDebugEnabled()) {
      log.debug("Cache Hit Ratio [" + getId() + "]: " + getHitRatio());
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 计算缓存命中率
   * @return
   */
  private double getHitRatio() {
    return (double) hits / (double) requests;
  }

}
