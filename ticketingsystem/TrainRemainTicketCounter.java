package ticketingsystem;

import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

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
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        return this.counterboard[rangeToIndex(departure, arrival)].get();
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        for (int d = 1; d < maxStationnum; d++) {
            for (int a = d + 1; a <= maxStationnum; a++) {
                if (rangeLegalCheck(d, a)) {
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

// 座位操作互斥-LongAdder实现的计数器
class SeatLevelLongAdderRemainTicketCounter extends TrainRemainTicketCounter {
    private LongAdder[] counterboard;
    private int maxStationnum;

    SeatLevelLongAdderRemainTicketCounter(int stationnum, int coachnum, int seatnum) {
        this.maxStationnum = stationnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.counterboard = new LongAdder[rangeCount];
        for (int i = 0; i < rangeCount; i++) {
            this.counterboard[i] = new LongAdder();
            this.counterboard[i].add(coachnum * seatnum);
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        return this.counterboard[rangeToIndex(departure, arrival)].intValue();
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        for (int d = 1; d < maxStationnum; d++) {
            for (int a = d + 1; a <= maxStationnum; a++) {
                if (rangeLegalCheck(d, a)) {
                    continue;
                }
                if (d < arrival && a > departure) {
                    if (seat.isRangeOccupied(d, a)) {
                        continue; // 之前已经记录过了，不需要再修改
                    }
                    if (isBuy) {
                        this.counterboard[rangeToIndex(d, a)].decrement();
                    } else {
                        this.counterboard[rangeToIndex(d, a)].increment();
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

class SeatLevelReadWriteRemainTicketCounter extends TrainRemainTicketCounter {
    private int[] counterboard;
    private int maxStationnum;
    private ReentrantReadWriteLock lock;

    SeatLevelReadWriteRemainTicketCounter(int stationnum, int coachnum, int seatnum) {
        this.maxStationnum = stationnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.counterboard = new int[rangeCount];
        this.lock = new ReentrantReadWriteLock(true);
        for (int i = 0; i < rangeCount; i++) {
            this.counterboard[i] = coachnum * seatnum;
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        this.lock.readLock().lock();
        try{
            return this.counterboard[rangeToIndex(departure, arrival)];
        } finally {
            this.lock.readLock().unlock();
        }

    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        this.lock.writeLock().lock();
        try {
            for (int d = 1; d < maxStationnum; d++) {
                for (int a = d + 1; a <= maxStationnum; a++) {
                    if (rangeLegalCheck(d, a)) {
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
        } finally {
            this.lock.writeLock().unlock();
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

class SeatLevelFCRemainTicketCounter extends TrainRemainTicketCounter {
    private int[][] counterboard;
    private StampedLock[] threadLock;
    private int maxStationnum;
    private StampedLock lock;
    private int amountTicket;
    private int threadnum;

    SeatLevelFCRemainTicketCounter(int stationnum, int coachnum, int seatnum, int threadnum) {
        this.maxStationnum = stationnum;
        this.threadnum = threadnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.amountTicket = coachnum * seatnum;
        this.threadLock = new StampedLock[threadnum];
        this.counterboard = new int[threadnum][rangeCount];
        this.lock = new StampedLock();
        for (int i = 0; i < threadnum; i++) {
            this.threadLock[i] = new StampedLock();
            for(int j = 0; j < rangeCount; j++) {
                this.counterboard[i][j] = 0;
            }
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        int delta = 0, threadDelta=0;
        long stamp, counter=0;
        for(int i=0; i < this.threadnum; i++){
            counter = 0;
            do {
                counter++;
                stamp = this.threadLock[i].tryOptimisticRead();
                threadDelta = this.counterboard[i][rangeToIndex(departure, arrival)];
            } while (!this.threadLock[i].validate(stamp));
            //System.out.println(counter);
            delta += threadDelta;
        }
        return this.amountTicket + delta;
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        int threadNr = MyThreadId.get() % this.threadnum;
        long stamp = this.threadLock[threadNr].writeLock();
        try {
            for (int d = 1; d < maxStationnum; d++) {
                for (int a = d + 1; a <= maxStationnum; a++) {
                    if (rangeLegalCheck(d, a)) {
                        continue;
                    }
                    if (d < arrival && a > departure) {
                        if (seat.isRangeOccupied(d, a)) {
                            continue; // 之前已经记录过了，不需要再修改
                        }
                        if (isBuy) {
                            this.counterboard[threadNr][rangeToIndex(d, a)]--;
                        } else {
                            this.counterboard[threadNr][rangeToIndex(d, a)]++;
                        }
                    }
                }
            }
            return true;
        } finally {
            this.threadLock[threadNr].unlockWrite(stamp);
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

class AtomicStampedRemainTicketCounter extends TrainRemainTicketCounter {
    private AtomicStampedReference<int[]> counterboard;
    private ReentrantLock aBigLock;
    private int maxStationnum;
    private int amountTicket;
    private int threadnum;

    AtomicStampedRemainTicketCounter(int stationnum, int coachnum, int seatnum, int threadnum) {
        this.maxStationnum = stationnum;
        this.threadnum = threadnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.amountTicket = coachnum * seatnum;
        this.aBigLock = new ReentrantLock();
        int[] innerCounterboard = new int[stationnum * stationnum];
        for (int i = 0; i < stationnum * stationnum; i++) {
            innerCounterboard[i] = 0;
        }
        this.counterboard.set(innerCounterboard, 0);
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        int stamp;
        int[] board;
        int result;
        // 乐观查找
        do {
            stamp = this.counterboard.getStamp();
            board = this.counterboard.getReference();
            result = board[rangeToIndex(departure, arrival)];
        } while (stamp != this.counterboard.getStamp());
        return this.amountTicket - result;
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        this.aBigLock.lock();
        try {
            int[] newBoard = new int[this.maxStationnum * this.maxStationnum];
            int[] oldBoard = this.counterboard.getReference();
            for (int d = 1; d < maxStationnum; d++) {
                for (int a = d + 1; a <= maxStationnum; a++) {
                    if (rangeLegalCheck(d, a)) {
                        continue;
                    }
                    newBoard[rangeToIndex(d, a)] = oldBoard[rangeToIndex(d, a)];
                    if (d < arrival && a > departure) {
                        if (seat.isRangeOccupied(d, a)) {
                            continue; // 之前已经记录过了，不需要再修改
                        }
                        if (isBuy) {
                            newBoard[rangeToIndex(d, a)]--;
                        } else {
                            newBoard[rangeToIndex(d, a)]++;
                        }
                    }
                }
            }
            this.counterboard.set(newBoard, this.counterboard.getStamp()+1);
            return true;
        } finally {
            this.aBigLock.unlock();
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

class SeatLevelFCStampedRemainTicketCounter extends TrainRemainTicketCounter {
    private int[][] counterboard;
    private StampedLock[] threadLock; // 线程间同步
    private AtomicInteger stamp;
    private int maxStationnum;
    private int amountTicket;
    private int threadnum;

    SeatLevelFCStampedRemainTicketCounter(int stationnum, int coachnum, int seatnum, int threadnum) {
        this.maxStationnum = stationnum;
        this.threadnum = threadnum;
        int rangeCount = stationnum * stationnum; // 这里浪费了一半内存
        this.amountTicket = coachnum * seatnum;
        this.threadLock = new StampedLock[threadnum];
        this.counterboard = new int[threadnum][rangeCount];
        this.stamp = new AtomicInteger(0);
        for (int i = 0; i < threadnum; i++) {
            this.threadLock[i] = new StampedLock();
            for(int j = 0; j < rangeCount; j++) {
                this.counterboard[i][j] = 0;
            }
        }
    }

    private int rangeToIndex(int departure, int arrival) {
        return (departure - 1) * maxStationnum + (arrival - 1);
    }

    @Override
    public int inquiryRemainTicket(int departure, int arrival) {
        if (this.rangeLegalCheck(departure, arrival)) {
            // 区间不合法直接返回0
            return 0;
        }
        int delta = 0, threadDelta=0;
        long threadStamp, globalStamp ,counter=0;

        do {
            delta = 0;
            globalStamp = stamp.get();
            for(int i=0; i < this.threadnum; i++){
                do {
                    threadStamp = this.threadLock[i].tryOptimisticRead();
                    threadDelta = this.counterboard[i][rangeToIndex(departure, arrival)];
                } while (!this.threadLock[i].validate(threadStamp));
                delta += threadDelta;
            }
        } while (globalStamp != stamp.get());

        return this.amountTicket + delta;
    }

    private boolean modifyRange(int departure, int arrival, boolean isBuy, Seat seat) {
        if (this.rangeLegalCheck(departure, arrival)) {
            return false;
        }
        int threadNr = MyThreadId.get() % this.threadnum;
        long threadStamp = this.threadLock[threadNr].writeLock();
        try {
            for (int d = 1; d < maxStationnum; d++) {
                for (int a = d + 1; a <= maxStationnum; a++) {
                    if (rangeLegalCheck(d, a)) {
                        continue;
                    }
                    if (d < arrival && a > departure) {
                        if (seat.isRangeOccupied(d, a)) {
                            continue; // 之前已经记录过了，不需要再修改
                        }
                        if (isBuy) {
                            this.counterboard[threadNr][rangeToIndex(d, a)]--;
                        } else {
                            this.counterboard[threadNr][rangeToIndex(d, a)]++;
                        }
                    }
                }
            }
            stamp.getAndIncrement();
            return true;
        } finally {
            this.threadLock[threadNr].unlockWrite(threadStamp);
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