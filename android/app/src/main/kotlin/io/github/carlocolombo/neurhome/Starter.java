package io.github.carlocolombo.neurhome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Starter {

    public static void main(String[] args) {
        try {
            ServerSocket welcomeSocket = new ServerSocket(6789);
            System.out.println("acccepting on 6789");
            Socket connectionSocket = welcomeSocket.accept();
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

            OutputStream os = connectionSocket.getOutputStream();
            PrintStream pw = new PrintStream(os, true);


            while (true) {
                String clientSentence = inFromClient.readLine();
                System.out.println(clientSentence);
                if (clientSentence == null) {
                    System.out.println("exiting");
                    break;
                }
                pw.println(">>" + clientSentence);
                Trainer.doStuff(pw);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
