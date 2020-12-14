package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;

public class Test {

    final static int threadnum = 4;
    final static int routenum = 5; // route is designed from 1 to 3
    final static int coachnum = 8; // coach is arranged from 1 to 5
    final static int seatnum = 100; // seat is allocated from 1 to 20
    final static int stationnum = 10; // station is designed from 1 to 5

    final static int testnum = 10000;
    final static int retpc = 10; // return ticket operation is 10% percent
    final static int buypc = 40; // buy ticket operation is 30% percent
    final static int inqpc = 100; //inquiry ticket operation is 60% percent

    static TicketingDS tds;

    public static void main(String[] args) throws InterruptedException {

        tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
        TestThread[] t = new TestThread[threadnum];
        for(int i = 0; i < threadnum; i++){
            t[i] = new TestThread();
            t[i].start();
        }
        for(int i = 0; i < threadnum; i++){
            t[i].join();
        }
        long amountTime = 0;
        for(int i = 0; i < threadnum; i++){
            amountTime += t[i].amountTime;
        }
        System.out.printf("%s 线程，总运行时间 %s ns\n", threadnum, amountTime);
    }

    static String passengerName() {
        Random rand = new Random();
        long uid = rand.nextInt(testnum);
        return "passenger" + uid;
    }

    static class TestThread extends Thread {
        public long amountTime;
        final long startTime = System.nanoTime();

        public void run() {
            amountTime = 0;
            Random rand = new Random();
            Ticket ticket = new Ticket();
            ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();
            long preTime = 0, postTime = 0;
            for (int i = 0; i < testnum; i++) {
                int sel = rand.nextInt(inqpc);
                if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
                    int select = rand.nextInt(soldTicket.size());
                    if ((ticket = soldTicket.remove(select)) != null) {
                        preTime = System.nanoTime() - startTime;
                        if (tds.refundTicket(ticket)) {
                            postTime = System.nanoTime() - startTime;
                            System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                            System.out.flush();
                        } else {
                            postTime = System.nanoTime() - startTime;
                            System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
                            System.out.flush();
                        }
                    } else {
                        preTime = System.nanoTime() - startTime;
                        System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
                        System.out.flush();
                    }
                } else if (retpc <= sel && sel < buypc) { // buy ticket
                    String passenger = passengerName();
                    int route = rand.nextInt(routenum) + 1;
                    int departure = rand.nextInt(stationnum - 1) + 1;
                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                    preTime = System.nanoTime() - startTime;
                    if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
                        postTime = System.nanoTime() - startTime;
                        System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                        soldTicket.add(ticket);
                        System.out.flush();
                    } else {
                        postTime = System.nanoTime() - startTime;
                        System.out.println(preTime + " " + String.valueOf(System.nanoTime() - startTime) + " " + ThreadId.get() + " " + "TicketSoldOut" + " " + route + " " + departure + " " + arrival);
                        System.out.flush();
                    }
                } else if (buypc <= sel && sel < inqpc) { // inquiry ticket

                    int route = rand.nextInt(routenum) + 1;
                    int departure = rand.nextInt(stationnum - 1) + 1;
                    int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                    preTime = System.nanoTime() - startTime;
                    int leftTicket = tds.inquiry(route, departure, arrival);
                    postTime = System.nanoTime() - startTime;
                    if (leftTicket < 0) {
                        System.out.println("!!!");
                    }
                    System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "RemainTicket" + " " + leftTicket + " " + route + " " + departure + " " + arrival);
                    System.out.flush();

                }

                amountTime += (postTime - preTime);
            }

        }
    }
}

