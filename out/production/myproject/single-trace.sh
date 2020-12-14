#!/bin/sh

javac -encoding UTF-8 -cp .:./ticketingsystem ticketingsystem/SingleThreadTrace.java
java -cp . ticketingsystem/SingleThreadTrace
