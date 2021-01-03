#!/bin/sh

javac -encoding UTF-8 -cp . ticketingsystem/Trace.java
javac -encoding UTF-8 -cp . ticketingsystem/*.java
java -cp . ticketingsystem/ForceTest
