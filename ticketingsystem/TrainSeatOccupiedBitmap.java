package ticketingsystem;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 对每个座位、每个区间的占用情况进行建模
 */
public class TrainSeatOccupiedBitmap {
    protected Seat[] allSeats;
    protected int seatAmount; // 用于遍历全车找座位
    protected int threadnum;
    TrainSeatOccupiedBitmap(int stationnum, int coachnum, int seatnum, int threadnum){
        this.allSeats = new Seat[coachnum * seatnum];
        this.seatAmount = coachnum * seatnum;
        this.threadnum = threadnum;
    }

    public int getSeatAmount(){
        return this.seatAmount;
    }

    public Seat pickSeatAtIndex(int seatIndex){
        return this.allSeats[seatIndex];
    }
}

class RangeLockTrainSeatOccupiedBitmap extends TrainSeatOccupiedBitmap {
    RangeLockTrainSeatOccupiedBitmap(int stationnum, int coachnum, int seatnum, int threadnum){
        super(stationnum, coachnum, seatnum, threadnum);
        for(int i=0; i < coachnum; i++){
            for(int j=0; j < seatnum; j++){
                allSeats[i * seatnum + j] = new RangeLockSeat(stationnum);
            }
        }
    }
}

// 根据线程数自动调节的
class AdaptiveGranularityTrainSeatOccupiedBitmap extends TrainSeatOccupiedBitmap {

    private final int SEAT_FACTOR = 1; // 多少个座位共用一把锁
    private int locknum;
    private ReentrantLock[] locks;
    AdaptiveGranularityTrainSeatOccupiedBitmap(int stationnum, int coachnum, int seatnum, int threadnum){
        super(stationnum, coachnum, seatnum, threadnum);
        this.locknum = this.seatAmount / SEAT_FACTOR + 1;
        this.locks = new ReentrantLock[this.locknum];
        for(int i=0; i < coachnum; i++){
            for(int j=0; j < seatnum; j++){
                allSeats[i * seatnum + j] = new Seat(stationnum);
            }
        }
        for(int i=0; i < this.locknum; i++){
            this.locks[i] =  new ReentrantLock();
        }
    }

    private ReentrantLock getLockOfSeat(int seatIndex){
        return this.locks[seatIndex / this.SEAT_FACTOR];
    }

    public boolean lockSeat(int seatIndex){
       this.getLockOfSeat(seatIndex).lock();
       return true;
    }

    public void unlockSeat(int seatIndex){
        if(this.getLockOfSeat(seatIndex).isHeldByCurrentThread()){
            this.getLockOfSeat(seatIndex).unlock();
        } else {
            return;
        }
    }

}