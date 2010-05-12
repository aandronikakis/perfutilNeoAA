/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.tele.debug.guestvm.xen;

import java.io.*;
import java.net.*;
import com.sun.max.program.*;
import static com.sun.max.tele.debug.guestvm.xen.GuestVMXenDBChannelProtocolAdaptor.*;

/**
 * An agent that handles the dom0 side of the Maxine Inspector debug communication channel.
 *
 * @author Mick Jordan
 *
 */
public class GuestVMXenDBNativeChannelAgent {

    private static int port = GuestVMXenDBTCPNativeChannelProtocol.DEFAULT_PORT;
    private static final String NATIVE = "Native";
    private static String impl = NATIVE;
    /**
     * @param args
     */
    public static void main(String[] args) {
        int traceLevel = 0;
        // Checkstyle: stop modified control variable check
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (arg.equals("-port")) {
                port = Integer.parseInt(args[++i]);
            } else if (arg.equals("-impl")) {
                impl = args[++i];
            } else if (arg.equals("-trace")) {
                traceLevel = Integer.parseInt(args[++i]);
            }
        }
        // Checkstyle: resume modified control variable check
        if (traceLevel > 0) {
            Trace.on(traceLevel);
        }
        if (impl.equals(NATIVE)) {
            System.loadLibrary("tele");
            System.loadLibrary("guk_db");
        }
        listen();
    }

    public static void listen() {
        try {
            final ServerSocket server = new ServerSocket(port);
            for (;;) {
                try {
                    Trace.line(1, "waiting for connection");
                    final Socket sock = server.accept();
                    Trace.line(1, "connection accepted on " + sock.getLocalPort() + " from " + sock.getInetAddress());
                    new Handler(sock).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Handler extends Thread {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private GuestVMXenDBChannelProtocolAdaptor protocol;

        Handler(Socket socket) throws Exception {
            this.socket = socket;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException ex) {
                close();
                throw ex;
            }
            final String protocolClassName = "com.sun.max.tele.debug.guestvm.xen.GuestVMXenDB" + impl + "ChannelProtocol";
            protocol = (GuestVMXenDBChannelProtocolAdaptor) Class.forName(protocolClassName).newInstance();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final String command = in.readUTF();
                    MethodInfo m = protocol.methodMap.get(command);
                    if (m == null) {
                        Trace.line(1, "command " + command + " not available");
                    } else {
                        final Object[] args = readArgs(m);
                        final Object result = m.method.invoke(protocol, args);
                        writeResult(m, result);
                    }
                } catch (Exception ex) {
                    System.err.println(ex);
                    ex.printStackTrace();
                    System.err.println("exiting");
                    close();
                    return;
                }
            }
        }

        private Object[] readArgs(MethodInfo m) throws IOException {
            final Object[] result = new Object[m.parameterTypes.length];
            int index = 0;
            for (Class<?> klass : m.parameterTypes) {
                if (klass == long.class) {
                    result[index] = in.readLong();
                } else if (klass == int.class) {
                    result[index] = in.readInt();
                } else if (klass == boolean.class) {
                    result[index] = in.readBoolean();
                } else if (klass == byte.class) {
                    result[index] = in.readByte();
                } else {
                    ProgramError.unexpected("unexpected argument type readArgs: " + klass.getName());
                }
                index++;
            }
            return result;
        }

        private void writeResult(MethodInfo m, Object result) throws IOException {
            if (m.returnType == void.class) {
                return;
            } else if (m.returnType == boolean.class) {
                out.writeBoolean((Boolean) result);
            } else if (m.returnType == int.class) {
                out.writeInt((Integer) result);
            } else if (m.returnType == long.class) {
                out.writeLong((Long) result);
            } else {
                ProgramError.unexpected("unexpected result type writeResult: " + m.returnType.getName());
            }
        }

        private void close() {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ex) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ex) {
                }
            }
        }
    }

}