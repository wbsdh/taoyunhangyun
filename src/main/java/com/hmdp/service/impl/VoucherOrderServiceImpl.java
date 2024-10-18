package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    //阻塞队列
    //private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //PostConstruct注解：类加载自动运行该方法
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    //获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息是否获取成功
                    if (list == null || list.isEmpty()){
                        //如果获取失败，没有消息，继续下一次循环
                        continue;
                    }
                    //如果获取成功则继续下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);

                    //ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    //获取pendingList中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1  STREAMS streams.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息是否获取成功
                    if (list == null || list.isEmpty()){
                        //如果获取失败，pendingList没有消息，跳出循环
                        break;
                    }
                    //如果获取成功则继续下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    log.error("处理pendingList异常",e);
                    try {
                        Thread.sleep(2000);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }
    }

    //线程方法
    /*private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){


                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }

            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock){
            //获取锁失败
            log.error("不允许重复下单");
            return;
        }
        //获取代理对象，防止事务失效
        try {
            proxy.getResult(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

/*        @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断是否开放秒杀
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())){
            //尚未开始
            return Result.fail("秒杀未开始");
        }
        //3.判断秒杀是否已经结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (LocalDateTime.now().isAfter(endTime)){
            //秒杀结束
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1){
            //库存不足
            return Result.fail("库存不足");
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock",0).update();
        if (!success){
            //扣减失败
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock){
            //获取锁失败
            return Result.fail("不得重复购买,草泥马");
        }
        //获取代理对象，防止事务失效
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getResult(voucherId,userId);
        } finally {
            //释放锁
            lock.unlock();
        }

    }*/
    //代理对象
    private IVoucherOrderService proxy;

    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString(),String.valueOf(orderId)
        );
        int r = result.intValue();
        //判断结果是否为0
        //不为0，没有购买资格
        if (r == 1){
            return Result.fail("库存不足");
        }
        if (r == 2){
            return Result.fail("请勿多次购买");
        }
        //返回订单id
        return Result.ok(orderId);
    }

    /*@Transactional
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString(),String.valueOf(orderId)
        );
        int r = result.intValue();
        //判断结果是否为0
        //不为0，没有购买资格
        if (r == 1){
            return Result.fail("库存不足");
        }
        if (r == 2){
            return Result.fail("请勿多次购买");
        }
        //为0，把下单信息保存到阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id

        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);
        //创建阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }*/

    @Transactional
    public void getResult(VoucherOrder voucherOrder) {
        save(voucherOrder);

    }
}
