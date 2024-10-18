package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间
     * @return
     */
    boolean tryLock(long timeoutSec);
    //释放锁
    void unlock();
}
