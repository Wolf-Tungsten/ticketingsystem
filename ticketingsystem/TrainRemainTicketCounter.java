package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;

interface TrainRemainTicketCounterInterface{
    public int inquiryRemainTicket(int departure, int arrival);
    public boolean buyRange(int departure, int arrival, Seat seat);
    public boolean refundRange(int departure, int arrival, Seat seat);
}

// 每趟列车的余票计数器
public class TrainRemainTicketCounter implements TrainRemainTicketCounterInterface {
    private AtomicInteger[] counterboard;
    private int maxStationnum;
    TrainRemainTicketCounter(int stationnum, int coachnum, int seatnum){
        this.maxStationnum = stationnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.counterboard = new AtomicInteger[rangeCount];
        for(int i=0; i < rangeCount; i++){
            this.counterboard[i] = new AtomicInteger(coachnum * seatnum);
        }
    }

    private boolean rangeLegalCheck(int departure, int arrival){
        // 检查区间合法性
        return departure >= 1 && arrival <= maxStationnum && ( arrival - departure > 0);
    }
    
    private int rangeToIndex(int departure, int arrival){
        return (departure-1)*maxStationnum + (arrival-1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if(!this.rangeLegalCheck(departure, arrival)){
            // 区间不合法直接返回0
            return 0;
        }
        return this.counterboard[rangeToIndex(departure, arrival)].get();
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat){
        if(!this.rangeLegalCheck(departure, arrival)){
            return false;
        }
        for(int d = 1; d < maxStationnum; d++){
            for(int a = d + 1; a <= maxStationnum; a++){
                if(!rangeLegalCheck(d, a)){
                    continue;
                }
                if(d < arrival && a > departure){
                    if(seat.isRangeOccupied(departure, arrival)){
                        continue; // 之前已经记录过了，不需要再修改
                    }
                    if(isBuy){
                        this.counterboard[rangeToIndex(d, a)].decrementAndGet();
                    } else {
                        this.counterboard[rangeToIndex(d, a)].incrementAndGet();
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean buyRange(int departure, int arrival, Seat seat) {
        return this.modifyRange(departure, arrival, true, seat);
    }

    @Override
    public boolean refundRange(int departure, int arrival, Seat seat) {
        return this.modifyRange(departure, arrival, false, seat);
    }
}
