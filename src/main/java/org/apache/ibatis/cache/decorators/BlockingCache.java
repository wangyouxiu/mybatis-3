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
package org.apache.ibatis.cache.decorators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * 带阻塞的缓存实现，当一个线程获取不到缓存时，不会释放锁，会阻塞后续获取缓存的线程
 * 当对应的缓存被添加时，释放锁
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrecly.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  /**
   * 阻塞超时等待时间
   */
  private long timeout;
  private final Cache delegate;
  /**
   * 缓存键与 CountDownLatch 对象的映射
   */
  private final ConcurrentHashMap<Object, CountDownLatch> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
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
  public void putObject(Object key, Object value) {
    try {
      //设置缓存
      delegate.putObject(key, value);
    } finally {
      //释放缓存key所对应的锁
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    //获取缓存时先申请锁
    acquireLock(key);
    Object value = delegate.getObject(key);
    //如果获取到缓存值，释放锁，否则不释放锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    // 不论方法叫什么名字，该方法都只是为了释放锁
    // 这里有些奇怪，至少释放锁之后也应该把缓存删了   不晓得为什么
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 申请锁
   * @param key
   */
  private void acquireLock(Object key) {
    //创建一个锁对象
    CountDownLatch newLatch = new CountDownLatch(1);
    while (true) {
      //将所对象放入locks
      CountDownLatch latch = locks.putIfAbsent(key, newLatch);
      if (latch == null) {
        break;
      }
      //等待其他线程释放锁
      try {
        if (timeout > 0) {
          boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
          if (!acquired) {
            throw new CacheException(
                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
          }
        } else {
          latch.await();
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    }
  }

  private void releaseLock(Object key) {
    //移除锁
    CountDownLatch latch = locks.remove(key);
    if (latch == null) {
      throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
    }
    //释放锁
    latch.countDown();
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
