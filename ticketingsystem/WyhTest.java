package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class WyhTest {

    //	final static int threadnum = 4;
    final static int routenum = 20; // route is designed from 1 to 3
    final static int coachnum = 10; // coach is arranged from 1 to 5
    final static int seatnum = 100; // seat is allocated from 1 to 20
    final static int stationnum = 16; // station is designed from 1 to 5

    final static int testnum = 100000;
    final static int retpc = 10; // return ticket operation is 10% percent
    final static int buypc = 30; // buy ticket operation is 30% percent
    final static int inqpc = 100; //inquiry ticket operation is 60% percent

    public static void main(String[] args) throws InterruptedException {
//		System.out.printf("%s   %30s   %20s   %15s   %15s   %15s   %15s   %15s\n",
//				"Thread Num", "Total execution time (ms)", "Total Time", "Kops/s", "Kops2/s", "refund/ns", "buy/ns", "inquiry/ns");
        System.out.printf("%s   %20s   %30s   %15s   %15s   %15s   %15s\n",
                "Thread Num", "Total execution", "Total execution time (ms)", "Kops/s", "refund/ns", "buy/ns", "inquiry/ns");
        for(int threadNum = 4; threadNum <= 64; threadNum *= 2) {
            Tester tester = new Tester(threadNum, routenum, coachnum, seatnum, stationnum, testnum);
            tester.runTest();
        }
    }
}

class Tester {
    final int threadNum;
    final int routeNum;
    final int coachNum;
    final int seatNum;
    final int stationNum;
    final int testNum;
    // refund:0 buy:1 inquiry:2
    AtomicLong[] methodCallCounter = new AtomicLong[3];
    AtomicLong[] methodTime = new AtomicLong[3];

    Tester(int threadNum, int routeNum, int coachNum, int seatNum, int stationNum, int testNum) {
        this.threadNum = threadNum;
        this.routeNum = routeNum;
        this.coachNum = coachNum;
        this.seatNum = seatNum;
        this.stationNum = stationNum;
        this.testNum = testNum;
        for(int i = 0; i < 3; ++i) {
            methodCallCounter[i] = new AtomicLong(0);
            methodTime[i] = new AtomicLong(0);
        }
    }

    String passengerName() {
        Random rand = new Random();
        long uid = rand.nextInt(testNum);
        return "passenger" + uid;
    }

    void runTest() throws InterruptedException {
        Thread[] threads = new Thread[threadNum];

        final TicketingDS tds = new TicketingDS(routeNum, coachNum, seatNum, stationNum, threadNum);

        //ToDo
        final long startTime = System.nanoTime();

        for (int i = 0; i< threadNum; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    Random rand = new Random();
                    Ticket ticket = new Ticket();
                    ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();

                    for (int i = 0; i < testNum; i++) {
                        int sel = rand.nextInt(WyhTest.inqpc);
                        if (0 <= sel && sel < WyhTest.retpc && soldTicket.size() > 0) { // return ticket
                            int select = rand.nextInt(soldTicket.size());
                            if ((ticket = soldTicket.remove(select)) != null) {

                                long preTime = System.nanoTime();
                                tds.refundTicket(ticket);
                                methodTime[0].addAndGet(System.nanoTime() - preTime);
                                methodCallCounter[0].incrementAndGet();

                            }
                        } else if (WyhTest.retpc <= sel && sel < WyhTest.buypc) { // buy ticket
                            String passenger = passengerName();
                            int route = rand.nextInt(routeNum) + 1;
                            int departure = rand.nextInt(stationNum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationNum - departure) + 1; // arrival is always greater than departure

                            long preTime = System.nanoTime();
                            ticket = tds.buyTicket(passenger, route, departure, arrival);
                            methodTime[1].addAndGet(System.nanoTime() - preTime);
                            methodCallCounter[1].incrementAndGet();

                            if(ticket != null) {
                                soldTicket.add(ticket);
                            }

                        } else if (WyhTest.buypc <= sel && sel < WyhTest.inqpc) { // inquiry ticket

                            int route = rand.nextInt(routeNum) + 1;
                            int departure = rand.nextInt(stationNum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationNum - departure) + 1; // arrival is always greater than departure

                            long preTime = System.nanoTime();
                            int leftTicket = tds.inquiry(route, departure, arrival);
                            methodTime[2].addAndGet(System.nanoTime() - preTime);
                            methodCallCounter[2].incrementAndGet();

                        }
                    }

                }
            });
            threads[i].start();
        }

        for (int i = 0; i< threadNum; i++) {
            threads[i].join();
        }
        final long endTime = System.nanoTime();
        getPerformance(startTime, endTime);
    }

    void getPerformance(long startTime, long endTime) {
        long totalExecution = 0;
        for(int i = 0; i < 3; ++i) {
            totalExecution += methodCallCounter[i].get();
        }

        double milliSecond = (endTime - startTime) / 1000000.0;
        double ops = totalExecution / milliSecond;
        long refund = methodTime[0].get() / methodCallCounter[0].get();
        long buy = methodTime[1].get() / methodCallCounter[1].get();
        long inquiry = methodTime[2].get() / methodCallCounter[2].get();

//		System.out.printf("% 10d   % 30f   % 20f   % 15f   % 15f   % 15d   % 15d   %15d\n",
//				threadNum, milliSecond, totalTime, ops, ops2, refund, buy, inquiry);
        System.out.printf("% 10d   %20d   % 30f   % 15f   % 15d   % 15d   %15d\n",
                threadNum, totalExecution, milliSecond, ops, refund, buy, inquiry);
    }
}