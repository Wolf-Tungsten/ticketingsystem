package ticketingsystem;

public class ForceTest {
    private final static int ROUTE_NUM = 20;
    private final static int COACH_NUM = 10;
    private final static int SEAT_NUM = 100;
    private final static int STATION_NUM = 16;

    public static void main(String[] args) {
        int threadnum = SEAT_NUM * COACH_NUM;
        TicketingDS ds = new TicketingDS(ROUTE_NUM, COACH_NUM, SEAT_NUM, STATION_NUM, threadnum);
        BuyTest buyTest = new BuyTest(COACH_NUM, SEAT_NUM, STATION_NUM);
        buyTest.fire(ds, threadnum);
        buyTest.verify(ds, threadnum);
    }
}

// 用和一辆车所有座位数一样多的线程数并发购买，所有线程都应该成功
class BuyTest{
    public Ticket ticket[];
    private int coachnum;
    private int seatnum;
    private int stationnum;
    private int seatAmount;
    BuyTest(int coachnum, int seatnum, int stationnum){
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.seatAmount = coachnum * seatnum;
        this.ticket = new Ticket[this.seatAmount];
    }

    public void fire(TicketingDS ds, int testAmount){
        if(testAmount > seatAmount){
            System.out.println("测试数量不可大于座位总数");
        }
        Thread t[] = new Thread[testAmount];
        for(int i=0; i < testAmount; i++){
            t[i] = new BuyThread(i, ds);
        }
        for(int i=0; i < testAmount; i++){
            t[i].start();
        }
        for(int i=0; i < testAmount; i++){
            try{
                t[i].join();
            } catch (InterruptedException e){}
        }
    }

    public void fire(TicketingDS ds){
        this.fire(ds, seatAmount);
    }

    public boolean verify(TicketingDS ds, int testAmount){
        for(int i=0; i < testAmount; i++){
            if(ticket[i] == null){
                System.out.println("Failed");
                return false;
            }
        }
        if(ds.inquiry(1, 1, stationnum) != (seatAmount - testAmount)){
            System.out.println(ds.inquiry(1, 1, stationnum));
            System.out.println("Failed");
            return false;
        }
        System.out.println("Passed");
        return true;
    }

    class BuyThread extends Thread{
        private int index;
        private TicketingDS ds;
        BuyThread(int index, TicketingDS ds){
            super();
            this.index = index;
            this.ds = ds;
        }

        @Override
        public void run() {
            ticket[index] = ds.buyTicket(Integer.valueOf(index).toString(), 1, 1, stationnum);
        }
    }
}
