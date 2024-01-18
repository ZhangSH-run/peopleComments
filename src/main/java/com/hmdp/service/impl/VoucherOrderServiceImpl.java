package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserThreadLocal;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker idWorker;
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始！");
        }
        // 判断秒杀是否结束
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("秒杀已经结束。");
        }
        // 判断库存是否充足
        if (seckillVoucher.getStock() < 1){
            return Result.fail("优惠券已经被抢光了...");
        }
        Long userId = UserThreadLocal.getUser().getId();
        // 返回订单id
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, redisTemplate);
        boolean isLock = simpleRedisLock.tryLock(10);
        if (!isLock){
            return Result.fail("不允许重复下单。");
        }
        try {
            return voucherOrderService.createVoucherOrder(voucherId,seckillVoucher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            simpleRedisLock.unlock();
        }
    }
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher){
        // 一人一单
        Long userId = UserThreadLocal.getUser().getId();
        long count = this.count(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId)
        );
        if (count > 0){
            return Result.fail("用户已经购买一次了！");
        }
        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId)
                .eq("stock",seckillVoucher.getStock())
                .update();
        if (!success){
            // 扣减失败
            return Result.fail("优惠券已经被抢光了...");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = idWorker.nextId("voucherOrder");
        voucherOrder.setId(orderId);
        // 6.2.用户id
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        baseMapper.insert(voucherOrder);
        return Result.ok(orderId);
    }
}
