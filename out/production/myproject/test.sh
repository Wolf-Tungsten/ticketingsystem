#!/bin/sh

rm -rf ticketingsystem/*.class
javac -encoding UTF-8 -cp . ticketingsystem/Test.java
java -cp . ticketingsystem/Test
