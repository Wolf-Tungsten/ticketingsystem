package ticketingsystem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Random;

/**
 * 针对指定列车的计票数据结构
 */
abstract public class TrainTicketingDS {

    protected ArrayList<ConcurrentHashMap<Long, Ticket>> coachTicketRecord;
    protected TrainRemainTicketCounter remainCounter;
    protected TrainSeatOccupiedBitmap bitmap;
    protected int trainNr;
    protected Random rnd;
    protected int stationnum;
    protected int coachnum;
    protected int seatnumPerCoach;

    final protected static Ticket firedTicket = new Ticket();

    TrainTicketingDS(int trainNr, int coachnum, int seatnum, int stationnum) {
        this.trainNr = trainNr;
        this.stationnum = stationnum;
        this.coachnum = coachnum;
        this.seatnumPerCoach = seatnum;
        // 模拟每节车厢列车员的换票本
        this.coachTicketRecord = new ArrayList<>(coachnum);
        for (int i = 0; i < coachnum; i++) {
            this.coachTicketRecord.add(new ConcurrentHashMap<>());
        }
        this.rnd = new Random(this.trainNr);
        this.firedTicket.tid = -1;
    }

    abstract public Ticket buyTicket(String passenger, int departure, int arrival);
    abstract public int inquiry(int departure, int arrival);
    abstract public boolean refundTicket(Ticket ticket);

    protected int randomSeatIndex() {
        return this.rnd.nextInt(this.bitmap.getSeatAmount());
    }

    static final class TidComponent {
        public static final int ROUTE_BIT = 8;
        public static final int ROUTE_MASK = 0xFF;
        public static final int COACH_BIT = 8;
        public static final int COACH_MASK = 0xFF;
        public static final int SEAT_BIT = 16;
        public static final int SEAT_MASK = 0xFFFF;
        public static final int STATION_BIT = 8;
        public static final int STATION_MASK = 0xFF;
        public static final int TIMESTAMP_BIT = 16;
        public static final int TIMESTAMP_MASK = 0xFFFF;

        private TidComponent() {}
    }

    protected long generateTid(Ticket ticketWithoutTid) {
        long tid;
        while (true) {
            tid = 0;
            tid += ticketWithoutTid.route & TidComponent.ROUTE_MASK;

            tid = tid << TidComponent.COACH_BIT;
            tid += ticketWithoutTid.coach & TidComponent.COACH_MASK;

            tid = tid << TidComponent.SEAT_BIT;
            tid += ticketWithoutTid.seat & TidComponent.SEAT_MASK;

            tid = tid << TidComponent.STATION_BIT;
            tid += ticketWithoutTid.departure & TidComponent.STATION_MASK;

            tid = tid << TidComponent.STATION_BIT;
            tid += ticketWithoutTid.arrival & TidComponent.STATION_MASK;

            tid = tid << TidComponent.TIMESTAMP_BIT;
            tid += System.currentTimeMillis() & TidComponent.TIMESTAMP_MASK;

            // 再加个随机数
            tid += this.rnd.nextInt() & TidComponent.TIMESTAMP_MASK;

            if (!this.coachTicketRecord.get(ticketWithoutTid.coach - 1).containsKey(tid)) {
                // 确认是否冲突
                break;
            }
        }
        return tid;
    }

    protected boolean isLegalRange(int departure, int arrival){
        return !(departure < 1 || arrival > this.stationnum || (arrival - departure) <= 0);
    }
}

class RangeLockTrainTicketingDS extends TrainTicketingDS {

    RangeLockTrainTicketingDS(int trainNr, int coachnum, int seatnum, int stationnum, int threadnum, boolean enableCache){
        super(trainNr, coachnum, seatnum, stationnum);
        this.bitmap = new RangeLockTrainSeatOccupiedBitmap(stationnum, coachnum, seatnum, threadnum);
        this.remainCounter = new LazyRemainTicketCounter(this.bitmap, enableCache, stationnum);
    }

    public Ticket buyTicket(String passenger, int departure, int arrival) {
        //System.out.printf("开始购票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, passenger, departure, arrival);
        // 检查区间是否合法
        if (!isLegalRange(departure, arrival)) {
            return null;
        }
        // 随机获取一个座位开始尝试占座
        int seatStartPoint = this.randomSeatIndex();
        int seatIndex = 0;
        RangeLockSeat currentSeat = null;
        boolean success = false;
        int conflict = 0;
        for (int i = 0; i < this.bitmap.getSeatAmount(); i++) {
            // 当前尝试的座位 index
            seatIndex = (seatStartPoint + i) % this.bitmap.getSeatAmount();
            // 当前尝试的座位实例
            currentSeat = (RangeLockSeat)this.bitmap.pickSeatAtIndex(seatIndex);
            // 检查区间是否可用
            if(currentSeat.isRangeOccupied(departure, arrival)){
                // 这个座位已经冲突了，看下一个
                conflict++;
                continue;
            }
            //System.out.printf(">>>>CONFLICT:%s<<<<\n",conflict);
            // 开始获取锁
            for (int range = departure - 1; range < arrival - 1; range++) {
                if(currentSeat.seatOccupiedLock[range].isLocked()){
                    System.out.printf(">>>>LOCKED<<<<\n");
                }
                currentSeat.seatOccupiedLock[range].lock();
            }
            // 成功获取锁
            try {
                // 现在没有人会来争抢，再次检查座位是否还空着
                if(currentSeat.isRangeOccupied(departure, arrival)){
                    // 在检查到加锁期间，座位已经被占了，看下一个
                    continue;
                }
                // 很好，座位还是空的，赶紧占上
                currentSeat.occupyRange(departure, arrival);
                // 标记购票成功
                success = true;
                break;
            } finally {
                // 释放锁
                for (int range = departure - 1; range < arrival - 1; range++) {
                    currentSeat.seatOccupiedLock[range].unlock();
                }
            }
        }
        // 看过所有座位，没有发现可用空座，那么本次购票失败
        if (!success) {
            return null;
        }
        // 执行到此处：已经成功锁定席位，开始出票
        Ticket ticket = new Ticket();
        ticket.passenger = passenger;
        ticket.route = this.trainNr;
        ticket.coach = seatIndex / this.seatnumPerCoach + 1; // 车厢
        ticket.seat = seatIndex % this.seatnumPerCoach + 1; // 座位都是要+1的，从1开始
        ticket.departure = departure;
        ticket.arrival = arrival;
        ticket.tid = this.generateTid(ticket);
        // 使用并发hash记录票出售的情况（用于退票验证）
        this.coachTicketRecord.get(ticket.coach - 1).put(ticket.tid, ticket);
        // 更新计数器时间，并没有实质上更新计数器
        this.remainCounter.buyRange(departure, arrival, null);
        // System.out.printf("成功购票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, passenger, departure, arrival);
        return ticket;
    }

    public int inquiry(int departure, int arrival) {
        return remainCounter.inquiryRemainTicket(departure, arrival);
    }

    public boolean refundTicket(Ticket ticket) {
        //System.out.printf("开始退票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        // 检查票的合法性
        if (ticket.coach <= 0 || ticket.coach > this.coachnum) {
            // 防止 coach 越界
            return false;
        }
        Ticket ticketRecord = this.coachTicketRecord.get(ticket.coach - 1).get(ticket.tid);
        if (ticketRecord == null || ticketRecord == firedTicket || !ticketRecord.equals(ticket)) {
            return false;
        }
        // 运行到此处，票面是合法的，确实存在这样的一张票
        // coach 和 seat 都是加了1的一定要小心！
        int seatIndex = (ticketRecord.coach - 1) * seatnumPerCoach + ticketRecord.seat - 1;
        RangeLockSeat currentSeat = (RangeLockSeat) this.bitmap.pickSeatAtIndex(seatIndex);
        // 获取操作区间的锁
        for (int range = ticketRecord.departure - 1; range < ticketRecord.arrival - 1; range++) {
            currentSeat.seatOccupiedLock[range].lock();
        }
        try {
            currentSeat.releaseRange(ticketRecord.departure, ticketRecord.arrival);
        } finally {
            for (int range = ticketRecord.departure - 1; range < ticketRecord.arrival - 1; range++) {
                currentSeat.seatOccupiedLock[range].unlock();
            }
        }
        // 将记录置为空，防止tid重复
        this.coachTicketRecord.get(ticket.coach - 1).replace(ticketRecord.tid, firedTicket);
        // 更新计数器时间，并没有实质上更新计数器
        this.remainCounter.refundRange(ticketRecord.departure, ticketRecord.arrival, null);
        //System.out.printf("成功退票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        return true;
    }

}

class AdptGraAtomicTrainTicketingDS extends TrainTicketingDS {

    AdptGraAtomicTrainTicketingDS(int trainNr, int coachnum, int seatnum, int stationnum, int threadnum){
        super(trainNr, coachnum, seatnum, stationnum);
        this.bitmap = new AdaptiveGranularityTrainSeatOccupiedBitmap(stationnum, coachnum, seatnum, threadnum);
        this.remainCounter = new SeatLevelAtomicRemainTicketCounter(stationnum,  coachnum, seatnum);
    }

    public Ticket buyTicket(String passenger, int departure, int arrival) {
        AdaptiveGranularityTrainSeatOccupiedBitmap bitmap = (AdaptiveGranularityTrainSeatOccupiedBitmap)this.bitmap;
        //System.out.printf("开始购票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, passenger, departure, arrival);
        // 检查区间是否合法
        if (!isLegalRange(departure, arrival)) {
            return null;
        }
        // 随机获取一个座位开始尝试占座
        int seatStartPoint = this.randomSeatIndex();
        int seatIndex = 0;
        Seat currentSeat = null;
        boolean success = false;
        for (int i = 0; i < bitmap.getSeatAmount(); i++) {
            // 当前尝试的座位 index
            seatIndex = (seatStartPoint + i) % bitmap.getSeatAmount();
            // 当前尝试的座位实例
            currentSeat = bitmap.pickSeatAtIndex(seatIndex);
            // 检查区间是否可用
            if(currentSeat.isRangeOccupied(departure, arrival)){
                // 这个座位已经冲突了，看下一个
                continue;
            }
            // 尝试获取锁，锁定座位所在区间
            if(!bitmap.lockSeat(seatIndex)){
                // 别的线程持有了锁，直接去找别处
                continue;
            }
            // 成功获取锁
            try {
                // 现在没有人会来争抢，再次检查座位是否还空着
                if(currentSeat.isRangeOccupied(departure, arrival)){
                    // 在检查到加锁期间，座位已经被占了，看下一个
                    continue;
                }
                // 很好，座位还是空的，赶紧占上
                this.remainCounter.buyRange(departure, arrival, currentSeat);
                currentSeat.occupyRange(departure, arrival);
                // 标记购票成功
                success = true;
                break;
            } finally {
                // 释放锁
                bitmap.unlockSeat(seatIndex);
            }
        }
        // 看过所有座位，没有发现可用空座，那么本次购票失败
        if (!success) {
            return null;
        }
        // 执行到此处：已经成功锁定席位，开始出票
        Ticket ticket = new Ticket();
        ticket.passenger = passenger;
        ticket.route = this.trainNr;
        ticket.coach = seatIndex / this.seatnumPerCoach + 1; // 车厢
        ticket.seat = seatIndex % this.seatnumPerCoach + 1; // 座位都是要+1的，从1开始
        ticket.departure = departure;
        ticket.arrival = arrival;
        ticket.tid = this.generateTid(ticket);
        // 使用并发hash记录票出售的情况（用于退票验证）
        this.coachTicketRecord.get(ticket.coach - 1).put(ticket.tid, ticket);
        //System.out.printf("成功购票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, passenger, departure, arrival);
        return ticket;
    }

    public int inquiry(int departure, int arrival) {
        return remainCounter.inquiryRemainTicket(departure, arrival);
    }

    public boolean refundTicket(Ticket ticket) {
        AdaptiveGranularityTrainSeatOccupiedBitmap bitmap = (AdaptiveGranularityTrainSeatOccupiedBitmap)this.bitmap;
        //System.out.printf("开始退票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        // 检查票的合法性
        if (ticket.coach <= 0 || ticket.coach > this.coachnum) {
            // 防止 coach 越界
            return false;
        }
        Ticket ticketRecord = this.coachTicketRecord.get(ticket.coach - 1).get(ticket.tid);
        if (ticketRecord == null || ticketRecord == firedTicket || !ticketRecord.equals(ticket)) {
            return false;
        }
        // 运行到此处，票面是合法的，确实存在这样的一张票
        // coach 和 seat 都是加了1的一定要小心！
        int seatIndex = (ticketRecord.coach - 1) * seatnumPerCoach + ticketRecord.seat - 1;
        Seat currentSeat = this.bitmap.pickSeatAtIndex(seatIndex);
        // 获取操作区间的锁
        bitmap.lockSeat(seatIndex);
        try {
            currentSeat.releaseRange(ticketRecord.departure, ticketRecord.arrival);
            this.remainCounter.refundRange(ticketRecord.departure, ticketRecord.arrival, currentSeat);
        } finally {
            bitmap.unlockSeat(seatIndex);
        }
        // 将记录置为空，防止tid重复
        this.coachTicketRecord.get(ticket.coach - 1).replace(ticketRecord.tid, firedTicket);
        // 更新计数器时间，并没有实质上更新计数器
        //System.out.printf("成功退票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        return true;
    }

}

class AdptGraReadWriteTrainTicketingDS extends TrainTicketingDS {

    AdptGraReadWriteTrainTicketingDS(int trainNr, int coachnum, int seatnum, int stationnum, int threadnum){
        super(trainNr, coachnum, seatnum, stationnum);
        this.bitmap = new AdaptiveGranularityTrainSeatOccupiedBitmap(stationnum, coachnum, seatnum, threadnum);
        this.remainCounter = new SeatLevelReadWriteRemainTicketCounter(stationnum,  coachnum, seatnum);
    }

    public Ticket buyTicket(String passenger, int departure, int arrival) {
        AdaptiveGranularityTrainSeatOccupiedBitmap bitmap = (AdaptiveGranularityTrainSeatOccupiedBitmap)this.bitmap;
        //System.out.printf("开始购票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, passenger, departure, arrival);
        // 检查区间是否合法
        if (isLegalRange(departure, arrival)) {
            return null;
        }
        // 随机获取一个座位开始尝试占座
        int seatStartPoint = this.randomSeatIndex();
        int seatIndex = 0;
        Seat currentSeat = null;
        boolean success = false;
        for (int i = 0; i < bitmap.getSeatAmount(); i++) {
            // 当前尝试的座位 index
            seatIndex = (seatStartPoint + i) % bitmap.getSeatAmount();
            // 当前尝试的座位实例
            currentSeat = bitmap.pickSeatAtIndex(seatIndex);
            // 检查区间是否可用
            if(currentSeat.isRangeOccupied(departure, arrival)){
                // 这个座位已经冲突了，看下一个
                continue;
            }
            // 锁定座位所在区间
            bitmap.lockSeat(seatIndex);
            // 成功获取锁
            try {
                // 现在没有人会来争抢，再次检查座位是否还空着
                if(currentSeat.isRangeOccupied(departure, arrival)){
                    // 在检查到加锁期间，座位已经被占了，看下一个
                    continue;
                }
                // 很好，座位还是空的，赶紧占上
                currentSeat.occupyRange(departure, arrival);
                this.remainCounter.buyRange(departure, arrival, currentSeat);
                // 标记购票成功
                success = true;
                break;
            } finally {
                // 释放锁
                bitmap.unlockSeat(seatIndex);
            }
        }
        // 看过所有座位，没有发现可用空座，那么本次购票失败
        if (!success) {
            return null;
        }
        // 执行到此处：已经成功锁定席位，开始出票
        Ticket ticket = new Ticket();
        ticket.passenger = passenger;
        ticket.route = this.trainNr;
        ticket.coach = seatIndex / this.seatnumPerCoach + 1; // 车厢
        ticket.seat = seatIndex % this.seatnumPerCoach + 1; // 座位都是要+1的，从1开始
        ticket.departure = departure;
        ticket.arrival = arrival;
        ticket.tid = this.generateTid(ticket);
        // 使用并发hash记录票出售的情况（用于退票验证）
        this.coachTicketRecord.get(ticket.coach - 1).put(ticket.tid, ticket);
        // System.out.printf("成功购票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, passenger, departure, arrival);
        return ticket;
    }

    public int inquiry(int departure, int arrival) {
        return remainCounter.inquiryRemainTicket(departure, arrival);
    }

    public boolean refundTicket(Ticket ticket) {
        AdaptiveGranularityTrainSeatOccupiedBitmap bitmap = (AdaptiveGranularityTrainSeatOccupiedBitmap)this.bitmap;
        // System.out.printf("开始退票 列车：%d 乘客：%s，出发：%d，到站：%d \n", this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        // 检查票的合法性
        if (ticket.coach <= 0 || ticket.coach > this.coachnum) {
            // 防止 coach 越界
            return false;
        }
        Ticket ticketRecord = this.coachTicketRecord.get(ticket.coach - 1).get(ticket.tid);
        if (ticketRecord == null || ticketRecord == firedTicket || !ticketRecord.equals(ticket)) {
            return false;
        }
        // 运行到此处，票面是合法的，确实存在这样的一张票
        // coach 和 seat 都是加了1的一定要小心！
        int seatIndex = (ticketRecord.coach - 1) * seatnumPerCoach + ticketRecord.seat - 1;
        Seat currentSeat = this.bitmap.pickSeatAtIndex(seatIndex);
        // 获取操作区间的锁
        bitmap.lockSeat(seatIndex);
        try {
            currentSeat.releaseRange(ticketRecord.departure, ticketRecord.arrival);
            this.remainCounter.refundRange(ticketRecord.departure, ticketRecord.arrival, currentSeat);
        } finally {
            bitmap.unlockSeat(seatIndex);
        }
        // 将记录置为空，防止tid重复
        this.coachTicketRecord.get(ticket.coach - 1).replace(ticketRecord.tid, firedTicket);
        // 更新计数器时间，并没有实质上更新计数器
        // System.out.printf("成功退票<%s> 列车：%d 乘客：%s，出发：%d，到站：%d \n", ticket.tid, this.trainNr, ticket.passenger, ticket.departure, ticket.arrival);
        return true;
    }
}