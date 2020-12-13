package ticketingsystem;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 对每个座位、每个区间的占用情况进行建模
 */
public class TrainSeatOccupiedBitmap {
    private Coach[] coachs;
    private Seat[] allSeats;
    private int seatAmount; // 用于遍历全车找座位
    TrainSeatOccupiedBitmap(int stationnum, int coachnum, int seatnum){
        this.coachs = new Coach[coachnum];
        this.allSeats = new Seat[coachnum * seatnum];
        this.seatAmount = coachnum * seatnum;
        for(int i=0; i < coachnum; i++){
            this.coachs[i] = new Coach(seatnum, stationnum);
            for(int j=0; j < seatnum; j++){
                allSeats[i * coachnum + j] = this.coachs[i].seats[j];
            }
        }
    }

    public int getSeatAmount(){
        return this.seatAmount;
    }

    public Seat pickSeatAtIndex(int seatIndex){
        return this.allSeats[seatIndex];
    }
}

class Coach {
    public Seat[] seats;
    Coach(int seatnum, int stationnum){
        this.seats = new Seat[seatnum];
        for(int i=0; i < seatnum; i++){
            this.seats[i] = new Seat(stationnum);
        }
    }
}

class Seat {
    public boolean[] seatOccupiedRange;
    public ReentrantLock[] seatOccupiedLock;
    Seat(int stationnum){
        this.seatOccupiedRange = new boolean[stationnum-1];
        this.seatOccupiedLock =  new ReentrantLock[stationnum-1];
        for(int i=0; i < stationnum-1; i++){
            this.seatOccupiedLock[i] = new ReentrantLock(true);
        }
    }

    public boolean isRangeOccupied(int departure, int arrival){
        departure -= 1;
        arrival -= 1;
        for(int i = departure; i < arrival; i++){
            if(this.seatOccupiedRange[i]){
                return true;
            }
        }
        return false;
    }

    public void occupyRange(int departure, int arrival){
        departure -= 1;
        arrival -= 1;
        for(int i = departure; i < arrival; i++){
            this.seatOccupiedRange[i] = true;
        }
    }

    public void releaseRange(int departure, int arrival){
        departure -= 1;
        arrival -= 1;
        for(int i = departure; i < arrival; i++){
            this.seatOccupiedRange[i] = false;
        }
    }
}