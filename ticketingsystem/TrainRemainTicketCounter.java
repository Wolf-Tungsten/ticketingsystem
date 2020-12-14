package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// 每趟列车的余票计数器
public abstract class TrainRemainTicketCounter {

    protected int maxStationnum;

    public abstract int inquiryRemainTicket(int departure, int arrival);

    public abstract boolean buyRange(int departure, int arrival, Seat seat);

    public abstract boolean refundRange(int departure, int arrival, Seat seat);

    protected boolean rangeLegalCheck(int departure, int arrival) {
        // 检查区间合法性
        return departure >= 1 && arrival <= maxStationnum && (arrival - departure > 0);
    }
}


// 座位操作互斥-AtomicInteger实现的计数器
class SeatLevelAtomicRemainTicketCounter extends TrainRemainTicketCounter {
    private AtomicInteger[] counterboard;
    private int maxStationnum;

    SeatLevelAtomicRemainTicketCounter(int stationnum, int coachnum, int seatnum) {
        this.maxStationnum = stationnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.counterboard = new AtomicInteger[rangeCount];
        for (int i = 0; i < rangeCount; i++) {
            this.counterboard[i] = new AtomicInteger(coachnum * seatnum);
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (!this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        return this.counterboard[rangeToIndex(departure, arrival)].get();
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (!this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        for (int d = 1; d < maxStationnum; d++) {
            for (int a = d + 1; a <= maxStationnum; a++) {
                if (!rangeLegalCheck(d, a)) {
                    continue;
                }
                if (d < arrival && a > departure) {
                    if (seat.isRangeOccupied(d, a)) {
                        continue; // 之前已经记录过了，不需要再修改
                    }
                    if (isBuy) {
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

// 座位操作互斥-ReadWriteLock 实现的余票计数器
class SeatLevelReadWriteRemainTicketCounter extends TrainRemainTicketCounter {

    private int[] counterboard;
    private ReadWriteLock readWriteLock;

    SeatLevelReadWriteRemainTicketCounter(int stationnum, int coachnum, int seatnum) {
        this.maxStationnum = stationnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.counterboard = new int[rangeCount];
        this.readWriteLock = new ReentrantReadWriteLock(true);
        for (int i = 0; i < rangeCount; i++) {
            this.counterboard[i] = coachnum * seatnum;
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (!this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        this.readWriteLock.readLock().lock();
        try {
            return this.counterboard[rangeToIndex(departure, arrival)];
        } finally {
            this.readWriteLock.readLock().unlock();
        }

    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (!this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        this.readWriteLock.writeLock().lock();
        try {
            for (int d = 1; d < maxStationnum; d++) {
                for (int a = d + 1; a <= maxStationnum; a++) {
                    if (!rangeLegalCheck(d, a)) {
                        continue;
                    }
                    if (d < arrival && a > departure) {
                        if (seat.isRangeOccupied(d, a)) {
                            continue; // 之前已经记录过了，不需要再修改
                        }
                        if (isBuy) {
                            this.counterboard[rangeToIndex(d, a)]--;
                        } else {
                            this.counterboard[rangeToIndex(d, a)]++;
                        }
                    }
                }
            }
            return true;
        } finally {
            this.readWriteLock.writeLock().unlock();
        }
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

// 座位操作不互斥-现用现算的计数器
class LazyRemainTicketCounter extends TrainRemainTicketCounter {
    private TrainSeatOccupiedBitmap bitmap;
    private boolean enableCache;
    private int[][] counterBoard;
    private long[][] updateTimeBoard;
    private long trainLastModifiedTime;

    LazyRemainTicketCounter(TrainSeatOccupiedBitmap bitmap, boolean enableCache, int stationnum) {
        this.bitmap = bitmap;
        this.maxStationnum = stationnum;
        this.enableCache = enableCache;
        this.counterBoard = new int[stationnum][stationnum];
        this.updateTimeBoard = new long[stationnum][stationnum];
        for (int a = 0; a < stationnum; a++) {
            for (int d = 0; d < stationnum; d++) {
                this.updateTimeBoard[d][a] = 0;
                this.counterBoard[d][a] = bitmap.seatAmount;
            }
        }
        this.trainLastModifiedTime = 0;
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (!rangeLegalCheck(departure, arrival)) {
            return 0;
        }
        //return this.counterBoard[departure-1][arrival-1];
        int counter = 0;
        for (int i = 0; i < this.bitmap.getSeatAmount(); i++) {
            Seat s = this.bitmap.pickSeatAtIndex(i);
            if (!s.isRangeOccupied(departure, arrival)) {
                counter++;
            }
        }
        return counter;


    }

    private boolean modifyRange(int departure, int arrival) {
        if (!this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        int counter = 0;
        for (int d = 1; d < maxStationnum; d++) {
            for (int a = d + 1; a <= maxStationnum; a++) {
                counter = 0;
                if (!rangeLegalCheck(d, a)) {
                    continue;
                }
                if (d < arrival && a > departure) {
                    // 区间受本次操作影响
                    for (int i = 0; i < this.bitmap.getSeatAmount(); i++) {
                        Seat s = this.bitmap.pickSeatAtIndex(i);
                        if (!s.isRangeOccupied(d, a)) {
                            counter++;
                        }
                    }
                    this.counterBoard[d-1][a-1] = counter;
                }
            }
        }
        return true;
    }

    @Override
    public boolean buyRange(int departure, int arrival, Seat seat) {
        //this.modifyRange(departure, arrival);
        return true;
    }

    @Override
    public boolean refundRange(int departure, int arrival, Seat seat) {
        //this.modifyRange(departure, arrival);
        return true;
    }
}

