package com.stealthcopter.networktools;

import android.os.Build;

import com.stealthcopter.networktools.portscanning.PortScanTCP;
import com.stealthcopter.networktools.portscanning.PortScanUDP;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

public class PortScan {
    private static final int TIMEOUT_LOCALHOST = 25;
    private static final int TIMEOUT_LOCALNETWORK = 1000;
    private static final int TIMEOUT_REMOTE = 2500;
    private static final int DEFAULT_THREADS_LOCALHOST = 7;
    private static final int DEFAULT_THREADS_LOCALNETWORK = 50;
    private static final int DEFAULT_THREADS_REMOTE = 50;
    private static final int METHOD_TCP = 0;
    private static final int METHOD_UDP = 1;

    private int method = METHOD_TCP;
    private int noThreads = 50;
    private InetAddress address;
    private int timeOutMillis = 1000;
    private boolean cancelled = false;
    private List<Integer> ports = new ArrayList<>();
    private List<Integer> openPortsFound = new ArrayList<>();
    private PortListener portListener;
    private Flowable<Unit> runningFlowable;

    public interface PortListener {
        void onResult(int portNo, boolean open);
        void onFinished(List<Integer> openPorts);
    }

    private static class Unit {}  //Kotlin Unit corresponds to Java Void

    private PortScan() {
        // This class is not to be instantiated
    }

    private PortScan setMethod(int method) {
        if (method == METHOD_UDP || method == METHOD_TCP) {
            this.method = method;
        } else {
            throw new IllegalArgumentException("Invalid method type " + method);
        }
        return this;
    }


    public PortScan setTimeOutMillis(int timeOutMillis) {
        if (timeOutMillis >= 0) {
            this.timeOutMillis = timeOutMillis;
        } else {
            throw new IllegalArgumentException("Timeout cannot be less than 0");
        }
        return this;
    }

    public PortScan setPort(int port) {
        ports.clear();
        validatePort(port);
        ports.add(port);
        return this;
    }

    public PortScan setPorts(List<Integer> ports) {
        for (int port : ports) {
            validatePort(port);
        }
        this.ports = ports;
        return this;
    }

    public PortScan setPorts(String portString) {
        ports.clear();
        String[] portsArray = portString.split(",");
        for (String portEntry : portsArray) {
            if (portEntry.contains("-")) {
                String[] range = portEntry.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                validatePort(start);
                validatePort(end);
                if (end > start) {
                    for (int j = start; j <= end; j++) {
                        ports.add(j);
                    }
                } else {
                    throw new IllegalArgumentException("Start port cannot be greater than or equal to the end port");
                }
            } else {
                int port = Integer.parseInt(portEntry);
                validatePort(port);
                ports.add(port);
            }
        }
        return this;
    }

    private void validatePort(int port) {
        if (port < 1) {
            throw new IllegalArgumentException("Start port cannot be less than 1");
        }
        if (port > 65535) {
            throw new IllegalArgumentException("Start cannot be greater than 65535");
        }
    }

    private void setAddress(InetAddress address) {
        this.address = address;
    }

    private void setDefaultThreadsAndTimeouts() {
        if (IPTools.isIpAddressLocalhost(address)) {
            timeOutMillis = TIMEOUT_LOCALHOST;
            noThreads = DEFAULT_THREADS_LOCALHOST;
        } else if (IPTools.isIpAddressLocalNetwork(address)) {
            timeOutMillis = TIMEOUT_LOCALNETWORK;
            noThreads = DEFAULT_THREADS_LOCALNETWORK;
        } else {
            timeOutMillis = TIMEOUT_REMOTE;
            noThreads = DEFAULT_THREADS_REMOTE;
        }
    }

    public PortScan setNoThreads(int noThreads) {
        if (noThreads >= 1) {
            this.noThreads = noThreads;
        } else {
            throw new IllegalArgumentException("Cannot have less than 1 thread");
        }
        return this;
    }

    public PortScan setMethodUDP() {
        setMethod(METHOD_UDP);
        return this;
    }

    public PortScan setMethodTCP() {
        setMethod(METHOD_TCP);
        return this;
    }

    public void cancel() {
        cancelled = true;
        runningFlowable.unsubscribeOn(Schedulers.computation());
    }

    public List<Integer> doScan() {
        cancelled = false;
        openPortsFound.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runningFlowable = createPortScanFlowable().doOnComplete(() -> openPortsFound.sort(null));
        }
        runningFlowable.blockingSubscribe();
        return openPortsFound;
    }

    private Flowable<Unit> createPortScanFlowable() {
        return Flowable.fromIterable(ports)
                .parallel(noThreads)
                .runOn(Schedulers.io())
                .map(portNo -> {
                    portScanned(portNo, method == METHOD_UDP ? PortScanUDP.scanAddress(address, portNo, timeOutMillis) :
                            PortScanTCP.scanAddress(address, portNo, timeOutMillis));
                    return new Unit();
                }).sequential()
                .subscribeOn(Schedulers.computation());
    }

    private synchronized void portScanned(int port, boolean open) {
        if (open) {
            openPortsFound.add(port);
        }
        if (portListener != null) {
            portListener.onResult(port, open);
        }
    }

    private static class PortScanRunnable implements Runnable {
        private final InetAddress address;
        private final int portNo;
        private final int timeOutMillis;
        private final int method;
        private final PortScan portScan;

        PortScanRunnable(PortScan portScan, InetAddress address, int portNo, int timeOutMillis, int method) {
            this.portScan = portScan;
            this.address = address;
            this.portNo = portNo;
            this.timeOutMillis = timeOutMillis;
            this.method = method;
        }

        @Override
        public void run() {
            if (portScan.cancelled) {
                return;
            }
            boolean open;
            if (method == METHOD_UDP) {
                open = PortScanUDP.scanAddress(address, portNo, timeOutMillis);
            } else if (method == METHOD_TCP) {
                open = PortScanTCP.scanAddress(address, portNo, timeOutMillis);
            } else {
                throw new IllegalArgumentException("Invalid method");
            }
            portScan.portScanned(portNo, open);
        }
    }

    public static PortScan onAddress(String address) throws UnknownHostException {
        return onAddress(InetAddress.getByName(address));
    }

    public static PortScan onAddress(InetAddress ia) {
        PortScan portScan = new PortScan();
        portScan.setAddress(ia);
        portScan.setDefaultThreadsAndTimeouts();
        return portScan;
    }
}

/*
If you want to avoid using RxJava's Flowable and perform the port scanning operation synchronously
without using reactive programming, you can modify the PortScan class to perform the scan directly
in a loop.

package com.stealthcopter.networktools;

import com.stealthcopter.networktools.portscanning.PortScanTCP;
import com.stealthcopter.networktools.portscanning.PortScanUDP;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class PortScan {

    private static final int TIMEOUT_LOCALHOST = 25;
    private static final int TIMEOUT_LOCALNETWORK = 1000;
    private static final int TIMEOUT_REMOTE = 2500;
    private static final int DEFAULT_THREADS_LOCALHOST = 7;
    private static final int DEFAULT_THREADS_LOCALNETWORK = 50;
    private static final int DEFAULT_THREADS_REMOTE = 50;
    private static final int METHOD_TCP = 0;
    private static final int METHOD_UDP = 1;

    private int method = METHOD_TCP;
    private int noThreads = 50;
    private InetAddress address;
    private int timeOutMillis = 1000;
    private boolean cancelled = false;
    private List<Integer> ports = new ArrayList<>();
    private List<Integer> openPortsFound = new ArrayList<>();
    private PortListener portListener;

    public interface PortListener {
        void onResult(int portNo, boolean open);
        void onFinished(List<Integer> openPorts);
    }

    // ... (other methods remain the same)

    public List<Integer> doScan() {
        cancelled = false;
        openPortsFound.clear();
        for (int portNo : ports) {
            if (cancelled) break;
            boolean open = false;
            switch (method) {
                case METHOD_UDP:
                    open = PortScanUDP.scanAddress(address, portNo, timeOutMillis);
                    break;
                case METHOD_TCP:
                    open = PortScanTCP.scanAddress(address, portNo, timeOutMillis);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid method");
            }
            portScanned(portNo, open);
        }
        return openPortsFound;
    }

    // ... (other methods remain the same)

    public static PortScan onAddress(String address) throws UnknownHostException {
        return onAddress(InetAddress.getByName(address));
    }

    public static PortScan onAddress(InetAddress ia) {
        PortScan portScan = new PortScan();
        portScan.setAddress(ia);
        portScan.setDefaultThreadsAndTimeouts();
        return portScan;
    }
}

Yes, there are several advantages to using `Flowable` from RxJava or other reactive programming libraries in your code:

1. **Asynchronous and Non-blocking:** `Flowable` allows you to perform operations asynchronously, meaning you can initiate tasks and continue with other tasks without waiting for the results. It doesn't block the calling thread, allowing for a more responsive application, especially in UI contexts.

2. **Backpressure Handling:** `Flowable` handles backpressure, which occurs when the producer is generating data at a faster rate than the consumer can consume. It provides strategies to deal with this situation, ensuring that your application doesn't run out of memory or crash due to overwhelming data.

3. **Composability:** Reactive programming allows you to compose complex operations from simpler ones. You can combine and transform streams of data using various operators, making your code more readable, maintainable, and modular.

4. **Error Handling:** Reactive libraries provide powerful error handling mechanisms. You can handle errors at various points in the stream, allowing for graceful degradation and recovery in case of failures.

5. **Concurrency Management:** Reactive libraries handle threading and concurrency for you. You can specify on which thread each part of your stream should operate, making it easier to manage concurrency in complex applications.

6. **Stream Processing:** Reactive programming is particularly useful for processing streams of data, such as user input, sensor data, or network responses. It simplifies managing and transforming these streams of data over time.

7. **Testability:** Code written in a reactive style is often more testable. Since functions are pure (no side effects) and streams of data are immutable, it's easier to write unit tests for reactive code.

8. **Community and Ecosystem:** Reactive programming has a vibrant community and a rich ecosystem of libraries and tools. You can find numerous resources, tutorials, and examples to help you leverage reactive programming in your projects.

However, it's important to note that while reactive programming offers these advantages, it might introduce a learning curve, especially if you are new to the paradigm. Additionally, not all applications or components benefit from reactive programming. Simple or linear tasks might be more readable and maintainable with traditional imperative or object-oriented approaches. It's crucial to evaluate the specific needs of your application before deciding whether to adopt reactive programming.
 */

