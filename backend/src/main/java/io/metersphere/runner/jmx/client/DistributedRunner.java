package io.metersphere.runner.jmx.client;

import org.apache.jmeter.engine.ClientJMeterEngine;
import org.apache.jmeter.engine.JMeterEngine;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.samplers.Remoteable;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributedRunner extends org.apache.jmeter.engine.DistributedRunner {
    private static final String HOSTS_SEPARATOR = ",";
    private HashTree jmxTree;
    private String hosts;

    public DistributedRunner(HashTree jmxTree, String hosts) {
        this.jmxTree = jmxTree;
        this.hosts = hosts;
    }

    public void run() {
        final List<String> hosts = getRemoteHosts();
        final ListenToTest listener = new ListenToTest(false, null);
        jmxTree.add(jmxTree.getArray()[0], listener);
        init(hosts, jmxTree);
        listener.setStartedRemoteEngines(new ArrayList<>(getEngines()));
        start();
    }

    private List<String> getRemoteHosts() {
        StringTokenizer st = new StringTokenizer(hosts, HOSTS_SEPARATOR);
        List<String> list = new LinkedList<>();
        while (st.hasMoreElements()) {
            list.add((String) st.nextElement());
        }
        return list;
    }

    private static class ListenToTest implements TestStateListener, Remoteable {
        private final Logger log = LoggerFactory.getLogger(ListenToTest.class);
        private final ReportGenerator reportGenerator;
        private AtomicInteger startedRemoteEngines = new AtomicInteger(0);
        private ConcurrentLinkedQueue<JMeterEngine> remoteEngines = new ConcurrentLinkedQueue<>();
        private boolean remoteStop;

        ListenToTest(boolean remoteStop, ReportGenerator reportGenerator) {
            this.remoteStop = remoteStop;
            this.reportGenerator = reportGenerator;
        }

        void setStartedRemoteEngines(List<JMeterEngine> engines) {
            this.remoteEngines.clear();
            this.remoteEngines.addAll(engines);
            this.startedRemoteEngines = new AtomicInteger(remoteEngines.size());
        }

        @Override
        // N.B. this is called by a daemon RMI thread from the remote host
        public void testEnded(String host) {
            final long now = System.currentTimeMillis();
            log.info("Finished remote host: {} ({})", host, now);
            if (startedRemoteEngines.decrementAndGet() <= 0) {
                log.info("All remote engines have ended test, starting RemoteTestStopper thread");
                Thread stopSoon = new Thread(() -> endTest(true), "RemoteTestStopper");
                // the calling thread is a daemon; this thread must not be
                // see Bug 59391
                stopSoon.setDaemon(false);
                stopSoon.start();
            }
        }

        @Override
        public void testEnded() {
            endTest(false);
        }

        @Override
        public void testStarted(String host) {
            final long now = System.currentTimeMillis();
            log.info("Started remote host:  {} ({})", host, now);
        }

        @Override
        public void testStarted() {
            if (log.isInfoEnabled()) {
                final long now = System.currentTimeMillis();
                log.info("{} ({})", JMeterUtils.getResString("running_test"), now);//$NON-NLS-1$
            }
        }

        private void endTest(boolean isDistributed) {
            long now = System.currentTimeMillis();
            if (isDistributed) {
                log.info("Tidying up remote @ " + new Date(now) + " (" + now + ")");
            } else {
                log.info("Tidying up ...    @ " + new Date(now) + " (" + now + ")");
            }

            if (isDistributed) {
                if (remoteStop) {
                    log.info("Exiting remote servers:" + remoteEngines);
                    for (JMeterEngine engine : remoteEngines) {
                        log.info("Exiting remote server:" + engine);
                        engine.exit();
                    }
                }
                try {
                    TimeUnit.SECONDS.sleep(5); // Allow listeners to close files
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                ClientJMeterEngine.tidyRMI(log);
            }

            if (reportGenerator != null) {
                try {
                    log.info("Generating Dashboard");
                    reportGenerator.generate();
                    log.info("Dashboard generated");
                } catch (Exception ex) {
                    System.err.println("Error generating the report: " + ex);//NOSONAR
                    log.error("Error generating the report: {}", ex.getMessage(), ex);
                }
            }
            checkForRemainingThreads();
            log.info("... end of run");
        }

        /**
         * Runs daemon thread which waits a short while;
         * if JVM does not exit, lists remaining non-daemon threads on stdout.
         */
        private void checkForRemainingThreads() {
            // This cannot be a JMeter class variable, because properties
            // are not initialised until later.
            final int pauseToCheckForRemainingThreads =
                    JMeterUtils.getPropDefault("jmeter.exit.check.pause", 2000); // $NON-NLS-1$

            if (pauseToCheckForRemainingThreads > 0) {
                Thread daemon = new Thread(() -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep(pauseToCheckForRemainingThreads); // Allow enough time for JVM to exit
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    // This is a daemon thread, which should only reach here if there are other
                    // non-daemon threads still active
                    System.out.println("The JVM should have exited but did not.");//NOSONAR
                    System.out.println("The following non-daemon threads are still running (DestroyJavaVM is OK):");//NOSONAR
                    JOrphanUtils.displayThreads(false);
                });
                daemon.setDaemon(true);
                daemon.start();
            } else if (pauseToCheckForRemainingThreads <= 0) {
                log.debug("jmeter.exit.check.pause is <= 0, JMeter won't check for unterminated non-daemon threads");
            }
        }
    }
}