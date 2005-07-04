/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.observation;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.BufferUtils;
import org.apache.commons.collections.buffer.UnboundedFifoBuffer;
import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * The class <code>ObservationManagerFactory</code> creates new
 * <code>ObservationManager</code> instances for sessions. It also
 * creates new {@link EventStateCollection}s that can be dispatched
 * to registered {@link javax.jcr.observation.EventListener}s.
 */
public final class ObservationManagerFactory implements Runnable {

    /**
     * Logger instance for this class
     */
    private static final Logger log
            = Logger.getLogger(ObservationManagerFactory.class);

    /**
     * Dummy DispatchAction indicating the notification thread to end
     */
    private static final DispatchAction DISPOSE_MARKER = new DispatchAction(null, null);

    /**
     * Currently active <code>EventConsumer</code>s for notification.
     */
    private Set activeConsumers = new HashSet();

    /**
     * Currently active synchronous <code>EventConsumer</code>s for notification.
     */
    private Set synchronousConsumers = new HashSet();

    /**
     * Set of <code>EventConsumer</code>s for read only Set access
     */
    private Set readOnlyConsumers;

    /**
     * Set of synchronous <code>EventConsumer</code>s for read only Set access.
     */
    private Set synchronousReadOnlyConsumers;

    /**
     * synchronization monitor for listener changes
     */
    private Object consumerChange = new Object();

    /**
     * Contains the pending events that will be delivered to event listeners
     */
    private Buffer eventQueue
            = BufferUtils.blockingBuffer(new UnboundedFifoBuffer());

    /**
     * The background notification thread
     */
    private Thread notificationThread;

    /**
     * Creates a new <code>ObservationManagerFactory</code> instance
     * and starts the notification thread deamon.
     */
    public ObservationManagerFactory() {
        notificationThread = new Thread(this, "ObservationManager");
        notificationThread.setDaemon(true);
        notificationThread.start();
    }

    /**
     * Disposes this <code>ObservationManager</code>. This will
     * effectively stop the background notification thread.
     */
    public void dispose() {
        // dispatch dummy event to mark end of notification
        eventQueue.add(DISPOSE_MARKER);
        try {
            notificationThread.join();
        } catch (InterruptedException e) {
            // FIXME log exception ?
        }
        log.info("Notification of EventListeners stopped.");
    }

    /**
     * Returns an unmodifieable <code>Set</code> of <code>EventConsumer</code>s.
     *
     * @return <code>Set</code> of <code>EventConsumer</code>s.
     */
    Set getAsynchronousConsumers() {
        synchronized (consumerChange) {
            if (readOnlyConsumers == null) {
                readOnlyConsumers = Collections.unmodifiableSet(new HashSet(activeConsumers));
            }
            return readOnlyConsumers;
        }
    }

    Set getSynchronousConsumers() {
        synchronized (consumerChange) {
            if (synchronousReadOnlyConsumers == null) {
                synchronousReadOnlyConsumers = Collections.unmodifiableSet(new HashSet(synchronousConsumers));
            }
            return synchronousReadOnlyConsumers;
        }
    }

    /**
     * Creates a new <code>session</code> local <code>ObservationManager</code>
     * with an associated <code>NamespaceResolver</code>.
     *
     * @param session the session.
     * @param itemMgr the <code>ItemManager</code> of the <code>session</code>.
     * @return an <code>ObservationManager</code>.
     */
    public ObservationManagerImpl createObservationManager(SessionImpl session,
                                                           ItemManager itemMgr) {
        return new ObservationManagerImpl(this, session, itemMgr);
    }

    /**
     * Implements the run method of the background notification
     * thread.
     */
    public void run() {
        DispatchAction action;
        while ((action = (DispatchAction) eventQueue.remove()) != DISPOSE_MARKER) {

            log.debug("got EventStateCollection");
            log.debug("event delivery to " + action.getEventConsumers().size() + " consumers started...");
            for (Iterator it = action.getEventConsumers().iterator(); it.hasNext();) {
                EventConsumer c = (EventConsumer) it.next();
                try {
                    c.consumeEvents(action.getEventStates());
                } catch (Throwable t) {
                    log.warn("EventConsumer threw exception: " + t.toString());
                    log.debug("Stacktrace: ", t);
                    // move on to the next consumer
                }
            }
            log.debug("event delivery finished.");

        }
    }

    /**
     * Gives this observation manager the oportunity to
     * prepare the events for dispatching.
     *
     * @param events the {@link EventState}s to prepare.
     */
    void prepareEvents(EventStateCollection events) {
        Set consumers = new HashSet();
        consumers.addAll(getSynchronousConsumers());
        consumers.addAll(getAsynchronousConsumers());
        for (Iterator it = consumers.iterator(); it.hasNext();) {
            EventConsumer c = (EventConsumer) it.next();
            c.prepareEvents(events);
        }
    }

    /**
     * Dispatches the {@link EventStateCollection events} to all
     * registered {@link javax.jcr.observation.EventListener}s.
     *
     * @param events the {@link EventState}s to dispatch.
     */
    void dispatchEvents(EventStateCollection events) {
        // notify synchronous listeners
        Set synchronous = getSynchronousConsumers();
        if (log.isDebugEnabled()) {
            log.debug("notifying " + synchronous.size() + " synchronous listeners.");
        }
        for (Iterator it = synchronous.iterator(); it.hasNext();) {
            EventConsumer c = (EventConsumer) it.next();
            try {
                c.consumeEvents(events);
            } catch (Throwable t) {
                log.error("Synchronous EventConsumer threw exception.", t);
                // move on to next consumer
            }
        }
        eventQueue.add(new DispatchAction(events, getAsynchronousConsumers()));
    }

    /**
     * Adds or replaces an event consumer.
     * @param consumer the <code>EventConsumer</code> to add or replace.
     */
    void addConsumer(EventConsumer consumer) {
        synchronized (consumerChange) {
            if (consumer.getEventListener() instanceof SynchronousEventListener) {
                // remove existing if any
                synchronousConsumers.remove(consumer);
                // re-add it
                synchronousConsumers.add(consumer);
                // reset read only consumer set
                synchronousReadOnlyConsumers = null;
            } else {
                // remove existing if any
                activeConsumers.remove(consumer);
                // re-add it
                activeConsumers.add(consumer);
                // reset read only consumer set
                readOnlyConsumers = null;
            }
        }
    }

    /**
     * Unregisters an event consumer from event notification.
     * @param consumer the consumer to deregister.
     */
    void removeConsumer(EventConsumer consumer) {
        synchronized (consumerChange) {
            if (consumer.getEventListener() instanceof SynchronousEventListener) {
                synchronousConsumers.remove(consumer);
                // reset read only listener set
                synchronousReadOnlyConsumers = null;
            } else {
                activeConsumers.remove(consumer);
                // reset read only listener set
                readOnlyConsumers = null;
            }
        }
    }

}
