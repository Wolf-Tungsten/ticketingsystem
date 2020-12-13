package ticketingsystem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Random;

interface TrainTicketingSystem {
    Ticket buyTicket(String passenger, int departure, int arrival);
	int inquiry(int departure, int arrival);
	boolean refundTicket(Ticket ticket);
}
/**
 * 针对指定列车的计票数据结构
 */
public class TrainTicketingDS implements TrainTicketingSystem{

    private ArrayList<ConcurrentHashMap<Long, Ticket>> coachTicketRecord;
    private TrainRemainTicketCounter remainCounter;
    private TrainSeatOccupiedBitmap bitmap;
    private int trainNr;
    private Random rnd;
    private int stationnum;
    private int coachnum;
    private int seatnumPerCoach;
    
    TrainTicketingDS(int routenum, int coachnum, int seatnum, int stationnum){
        this.trainNr = routenum;
        this.stationnum = stationnum;
        this.coachnum = coachnum;
        this.seatnumPerCoach = seatnum;
        // 模拟每节车厢列车员的换票本
        this.coachTicketRecord = new ArrayList<>(coachnum);
        for(int i = 0; i < coachnum; i++){
            this.coachTicketRecord.add(new ConcurrentHashMap<>());
        }
        this.remainCounter = new TrainRemainTicketCounter(stationnum, coachnum, seatnum);
        this.bitmap = new TrainSeatOccupiedBitmap(stationnum, coachnum, seatnum);
        this.rnd = new Random(this.trainNr);
    }

    @Override
    public Ticket buyTicket(String passenger, int departure, int arrival) {
        // 检查区间是否合法
        if(departure < 1 || arrival > this.stationnum || (arrival - departure) <= 0){
            return null;
        }
        // 随机获取一个座位开始尝试占座
        int seatStartPoint = this.randomSeatIndex();
        int seatIndex = 0;
        Seat currentSeat = null;
        boolean success = false;
        iterSeat:for(int i = 0; i < this.bitmap.getSeatAmount(); i++){
            // 当前尝试的座位 index
            seatIndex = (seatStartPoint + i) % this.bitmap.getSeatAmount();
            // 当前尝试的座位实例
            currentSeat = this.bitmap.pickSeatAtIndex(seatIndex);
            for(int range = departure - 1; range < arrival - 1; range++){
                currentSeat.seatOccupiedLock[range].lock();
            }
            for(int range = departure - 1; range < arrival - 1; range++){
                if(currentSeat.seatOccupiedRange[i]){
                    // 如果区间已经被占用了，放弃这个座位，看下一个
                    continue iterSeat;
                }
            }
            // 开始获取锁
            for(int range = departure - 1; range < arrival - 1; range++){
                currentSeat.seatOccupiedLock[range].lock();
            }
            // 成功获取锁
            try {
                // 现在没有人会来争抢，再次检查座位是否还空着
                for(int range = departure - 1; range < arrival - 1; range++){
                    if(currentSeat.seatOccupiedRange[i]){
                        // 如果区间已经被占用了，放弃这个座位，看下一个
                        continue iterSeat;
                    }
                }
                // 很好，座位还是空的，更新计数器
                this.remainCounter.buyRange(departure, arrival, currentSeat);
                // 更新座位占用情况
                for(int range = departure - 1; range < arrival - 1; range++){
                    currentSeat.seatOccupiedRange[i] = true;
                }
                // 标记购票成功
                success = true;
            } finally {
                // 释放锁
                for(int range = departure - 1; range < arrival - 1; range++){
                    currentSeat.seatOccupiedLock[range].unlock();
                }
            }
        }
        // 看过所有座位，没有发现可用空座，那么本次购票失败
        if(!success){
            return null;
        }
        // 执行到此处：已经成功锁定席位，开始出票
        Ticket ticket = new Ticket();
        ticket.route = this.trainNr;
        ticket.coach = seatIndex / this.seatnumPerCoach;
        ticket.seat = seatIndex % this.seatnumPerCoach;
        ticket.departure = departure;
        ticket.arrival = arrival;
        ticket.tid = this.generateTid(ticket);
        // 使用并发hash记录票出售的情况（用于退票验证）
        this.coachTicketRecord.get(ticket.coach).put(ticket.tid, ticket);
        return ticket;
    }

    @Override
    public int inquiry(int departure, int arrival) {
        return remainCounter.inquiryRemainTicket(departure, arrival);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        // 检查票的合法性
        if(ticket.coach > this.coachnum){
            // 防止 coach 越界
            return false;
        }
        Ticket ticketRecord = this.coachTicketRecord.get(ticket.coach).get(ticket.tid);
        if(ticketRecord == null || !ticketRecord.equals(ticket)){
            return false;
        }
        // 运行到此处，票面是合法的，确实存在这样的一张票
        // TODO：退票逻辑
        return true;
    }

    private int randomSeatIndex(){
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
        public static final int TIMESTAMP_MASK= 0xFFFF;
        private  TidComponent() {}
    }

    private long generateTid(Ticket ticketWithoutTid){
        long tid = 0;
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

        return tid;
    }
}
