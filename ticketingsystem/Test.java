package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class Test {

    final static int maxThreadnum = 256;
    final static int routenum = 20; // route is designed from 1 to 3
    final static int coachnum = 10; // coach is arranged from 1 to 5
    final static int seatnum = 100; // seat is allocated from 1 to 20
    final static int stationnum = 16; // station is designed from 1 to 5

    final static int testnum = 100000;
    final static int retpc = 10; // return ticket operation is 10% percent
    final static int buypc = 30; // buy ticket operation is 30% percent
    final static int inqpc = 100; //inquiry ticket operation is 60% percent


    public static void main(String[] args) throws InterruptedException {
        System.out.printf("%10s %20s %20s %16s %16s %16s\n",
                "#Threads",
                "AmountTimeAvg/ms",
                "kops/s",
                "BuyTimeAvg/ns",
                "InqTimeAvg/ns",
                "RefundTimeAvg/ns"
        );
        for(int i = 4; i <= maxThreadnum; i = i << 2){
            for(int j=0; j < 4; j++){
                runTestOfNrThread(i);
            }
        }
        //runTestOfNrThread(64);
    }

    public static void runTestOfNrThread(int threadnum) throws InterruptedException {
        final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
        TestThread[] t = new TestThread[threadnum];
        for(int i = 0; i < threadnum; i++){
            t[i] = new TestThread(tds);
            t[i].start();
        }
        for(int i = 0; i < threadnum; i++){
            t[i].join();
        }

        double amountTime = 0;
        long amountCount = 0;

        double buyTime=0, inquiryTime=0, refundTime=0;
        long buyCount=0, inquiryCount=0, refundCount=0;

        for(int i = 0; i < threadnum; i++){
            buyTime +=  t[i].buyTime;
            inquiryTime += t[i].inquiryTime;
            refundTime += t[i].refundTime;

            buyCount += t[i].buyCount;
            inquiryCount += t[i].inquiryCount;
            refundCount += t[i].refundCount;
        }

        amountTime = (buyTime/1000000 + inquiryTime/1000000 + refundTime/1000000) / threadnum;
        amountCount = buyCount + inquiryCount + refundCount;
        System.out.printf("%10s %20.3f %20.3f %16.3f %16.3f %16.3f\n",
                threadnum,
                amountTime,
                (amountCount/(amountTime/1000))/1000,
                buyTime/buyCount,
                inquiryTime/inquiryCount,
                refundTime/refundCount
                );

    }

    static public AtomicLong buyTimeAtomic, inquiryTimeAtomic, refundTimeAtomic,
            buyCountAtomic, inquiryCountAtomic, refundCountAtomic;

//    public static void runAtomicTestOfNrThread(int threadnum) throws InterruptedException {
//
//        buyTimeAtomic = new AtomicLong(0);
//        inquiryTimeAtomic  =  new AtomicLong(0);
//        refundTimeAtomic = new AtomicLong(0);
//        buyCountAtomic = new AtomicLong(0);
//        inquiryCountAtomic = new AtomicLong(0);
//        refundCountAtomic = new AtomicLong(0);
//
//        final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
//        AtomicTestThread[] t = new AtomicTestThread[threadnum];
//        for(int i = 0; i < threadnum; i++){
//            t[i] = new AtomicTestThread();
//            t[i].start();
//        }
//        for(int i = 0; i < threadnum; i++){
//            t[i].join();
//        }
//
//        double amountTime = 0;
//        long amountCount = 0;
//
//        double buyTime=buyTimeAtomic.get(), inquiryTime=inquiryTimeAtomic.get(), refundTime=refundTimeAtomic.get();
//        long buyCount=buyCountAtomic.get(), inquiryCount=inquiryCountAtomic.get(), refundCount=refundCountAtomic.get();
//
//
//        amountTime = buyTime + inquiryTime + refundTime;
//        amountCount = buyCount + inquiryCount + refundCount;
//        System.out.printf("%10s %20.3f %20.3f %16.3f %16.3f %16.3f Atomic*\n",
//                threadnum,
//                amountTime/1000000,
//                (amountCount/(amountTime/1000000000))/1000,
//                buyTime/buyCount,
//                inquiryTime/inquiryCount,
//                refundTime/refundCount
//        );
//
//    }

    static String passengerName() {
        Random rand = new Random();
        long uid = rand.nextInt(testnum);
        return "passenger" + uid;
    }

    static class TestThread extends Thread {
        public long amountTime;

        public long buyTime;
        public long inquiryTime;
        public long refundTime;

        public long buyCount;
        public long inquiryCount;
        public long refundCount;

        long startTime;

        private TicketingDS tds;

        TestThread(TicketingDS tds){
            this.tds = tds;
        }
        public void run() {
            amountTime = 0;
            buyTime = 0;
            buyCount = 0;
            inquiryTime = 0;
            inquiryCount = 0;
            refundTime = 0;
            refundCount = 0;
            startTime = System.nanoTime();
            Random rand = new Random();
            Ticket ticket = new Ticket();
            ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();
            long preTime = 0, postTime = 0;
            int finishedTest = 0;
            while (finishedTest < testnum) {
                int sel = rand.nextInt(inqpc);
                if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
                    int select = rand.nextInt(soldTicket.size());
                    if ((ticket = soldTicket.remove(select)) != null) {
                        preTime = System.nanoTime() - startTime;
                        if (tds.refundTicket(ticket)) {
                            postTime = System.nanoTime() - startTime;
                            refundTime += (postTime - preTime);
                            refundCount++;
                            finishedTest++;
                            //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                            //System.out.flush();
                        } else {
                            postTime = System.nanoTime() - startTime;
                            //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
                            //System.out.flush();
                        }
                    } else {
                        preTime = System.nanoTime() - startTime;
                        //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
                        //System.out.flush();
                    }
                } else if (retpc <= sel && sel < buypc) { // buy ticket
                    String passenger = passengerName();
                    int route = rand.nextInt(routenum) + 1;
                    int departure = rand.nextInt(stationnum - 1) + 1;
                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                    preTime = System.nanoTime() - startTime;
                    if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
                        postTime = System.nanoTime() - startTime;
                        //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                        soldTicket.add(ticket);
                        //System.out.flush();
                    } else {
                        postTime = System.nanoTime() - startTime;
                        //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "TicketSoldOut" + " " + route + " " + departure + " " + arrival);
                        //System.out.flush();
                    }
                    buyTime += (postTime - preTime);
                    buyCount++;
                    finishedTest++;
                } else if (buypc <= sel && sel < inqpc) { // inquiry ticket

                    int route = rand.nextInt(routenum) + 1;
                    int departure = rand.nextInt(stationnum - 1) + 1;
                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                    preTime = System.nanoTime() - startTime;
                    int leftTicket = tds.inquiry(route, departure, arrival);
                    postTime = System.nanoTime() - startTime;
                    //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "RemainTicket" + " " + leftTicket + " " + route + " " + departure + " " + arrival);
                    //System.out.flush();
                    inquiryTime += (postTime - preTime);
                    inquiryCount++;
                    finishedTest++;
                }
            }
            amountTime = (System.nanoTime() - startTime) / 1000 / 1000;
        }
    }

//    static class AtomicTestThread extends Thread {
//
//        final long startTime = System.nanoTime();
//
//        public void run() {
//            Random rand = new Random();
//            Ticket ticket = new Ticket();
//            ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();
//            long preTime = 0, postTime = 0;
//            int finishedTest = 0;
//            while (finishedTest < testnum) {
//                int sel = rand.nextInt(inqpc);
//                if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
//                    int select = rand.nextInt(soldTicket.size());
//                    if ((ticket = soldTicket.remove(select)) != null) {
//                        preTime = System.nanoTime() - startTime;
//                        if (tds.refundTicket(ticket)) {
//                            postTime = System.nanoTime() - startTime;
//                            finishedTest++;
//                            //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
//                            //System.out.flush();
//                        } else {
//                            postTime = System.nanoTime() - startTime;
//                            //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
//                            //System.out.flush();
//                        }
//                        refundTimeAtomic.addAndGet(postTime - preTime);
//                        refundCountAtomic.incrementAndGet();
//                    } else {
//                        preTime = System.nanoTime() - startTime;
//                        //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
//                        //System.out.flush();
//                    }
//                } else if (retpc <= sel && sel < buypc) { // buy ticket
//                    String passenger = passengerName();
//                    int route = rand.nextInt(routenum) + 1;
//                    int departure = rand.nextInt(stationnum - 1) + 1;
//                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//                    preTime = System.nanoTime() - startTime;
//                    if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
//                        postTime = System.nanoTime() - startTime;
//                        //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
//                        soldTicket.add(ticket);
//                        //System.out.flush();
//                    } else {
//                        postTime = System.nanoTime() - startTime;
//                        //System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "TicketSoldOut" + " " + route + " " + departure + " " + arrival);
//                        //System.out.flush();
//                    }
//                    buyTimeAtomic.addAndGet(postTime - preTime);
//                    buyCountAtomic.incrementAndGet();
//                    finishedTest++;
//                } else if (buypc <= sel && sel < inqpc) { // inquiry ticket
//
//                    int route = rand.nextInt(routenum) + 1;
//                    int departure = rand.nextInt(stationnum - 1) + 1;
//                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//                    preTime = System.nanoTime() - startTime;
//                    int leftTicket = tds.inquiry(route, departure, arrival);
//                    postTime = System.nanoTime() - startTime;
//                    //System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "RemainTicket" + " " + leftTicket + " " + route + " " + departure + " " + arrival);
//                    //System.out.flush();
//                    inquiryTimeAtomic.addAndGet(postTime - preTime);
//                    inquiryCountAtomic.incrementAndGet();
//                    finishedTest++;
//                }
//            }
//
//        }
//    }
}

