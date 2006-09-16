/*
 * $Id: $
 *
 * Copyright 1997-2004 Day Management AG
 * Barfuesserplatz 6, 4001 Basel, Switzerland
 * All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Day Management AG, ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Day.
 */
package org.apache.jackrabbit.core.observation;

import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.nodetype.NodeTypeImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.PathFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.EventListenerIterator;
import javax.jcr.observation.ObservationManager;

/**
 * Each <code>Session</code> instance has its own <code>ObservationManager</code>
 * instance. The class <code>SessionLocalObservationManager</code> implements
 * this behaviour.
 */
public class ObservationManagerImpl implements ObservationManager, EventStateCollectionFactory {

    /**
     * The logger instance of this class
     */
    private static final Logger log = LoggerFactory.getLogger(ObservationManagerImpl.class);

    /**
     * The <code>Session</code> this <code>ObservationManager</code>
     * belongs to.
     */
    private final SessionImpl session;

    /**
     * The <code>ItemManager</code> for this <code>ObservationManager</code>.
     */
    private final ItemManager itemMgr;

    /**
     * The <code>ObservationDispatcher</code>
     */
    private final ObservationDispatcher dispatcher;

    static {
        // preload EventListenerIteratorImpl to prevent classloader issues during shutdown
        EventListenerIteratorImpl.class.hashCode();
    }

    /**
     * Creates an <code>ObservationManager</code> instance.
     *
     * @param dispatcher observation dispatcher
     * @param session the <code>Session</code> this ObservationManager
     *                belongs to.
     * @param itemMgr {@link org.apache.jackrabbit.core.ItemManager} of the passed
     *                <code>Session</code>.
     * @throws NullPointerException if <code>session</code> or <code>itemMgr</code>
     *                              is <code>null</code>.
     */
    public ObservationManagerImpl(ObservationDispatcher dispatcher,
                           SessionImpl session,
                           ItemManager itemMgr) throws NullPointerException {
        if (session == null) {
            throw new NullPointerException("session");
        }
        if (itemMgr == null) {
            throw new NullPointerException("itemMgr");
        }

        this.dispatcher = dispatcher;
        this.session = session;
        this.itemMgr = itemMgr;
    }

    /**
     * {@inheritDoc}
     */
    public void addEventListener(EventListener listener,
                                 int eventTypes,
                                 String absPath,
                                 boolean isDeep,
                                 String[] uuid,
                                 String[] nodeTypeName,
                                 boolean noLocal)
            throws RepositoryException {

        // create NodeType instances from names
        NodeTypeImpl[] nodeTypes;
        if (nodeTypeName == null) {
            nodeTypes = null;
        } else {
            NodeTypeManagerImpl ntMgr = session.getNodeTypeManager();
            nodeTypes = new NodeTypeImpl[nodeTypeName.length];
            for (int i = 0; i < nodeTypes.length; i++) {
                nodeTypes[i] = (NodeTypeImpl) ntMgr.getNodeType(nodeTypeName[i]);
            }
        }

        Path path;
        try {
            path = PathFormat.parse(absPath, session.getNamespaceResolver()).getNormalizedPath();
        } catch (MalformedPathException mpe) {
            String msg = "invalid path syntax: " + absPath;
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }
        NodeId[] ids = null;
        if (uuid != null) {
            ids = new NodeId[uuid.length];
            for (int i = 0; i < uuid.length; i++) {
                ids[i] = NodeId.valueOf(uuid[i]);
            }
        }
        // create filter
        EventFilter filter = new EventFilter(itemMgr,
                session,
                eventTypes,
                path,
                isDeep,
                ids,
                nodeTypes,
                noLocal);

        dispatcher.addConsumer(new EventConsumer(session, listener, filter));
    }

    /**
     * {@inheritDoc}
     */
    public void removeEventListener(EventListener listener)
            throws RepositoryException {
        dispatcher.removeConsumer(
                new EventConsumer(session, listener, EventFilter.BLOCK_ALL));

    }

    /**
     * {@inheritDoc}
     */
    public EventListenerIterator getRegisteredEventListeners()
            throws RepositoryException {
        return new EventListenerIteratorImpl(
                session,
                dispatcher.getSynchronousConsumers(),
                dispatcher.getAsynchronousConsumers());
    }

    /**
     * Unregisters all EventListeners.
     */
    public void dispose() {
        try {
            EventListenerIterator it = getRegisteredEventListeners();
            while (it.hasNext()) {
                EventListener l = it.nextEventListener();
                log.debug("removing EventListener: " + l);
                removeEventListener(l);
            }
        } catch (RepositoryException e) {
            log.error("Internal error: Unable to dispose ObservationManager.", e);
        }

    }

    //------------------------------------------< EventStateCollectionFactory >

    /**
     * {@inheritDoc}
     * <p/>
     * Creates an <code>EventStateCollection</code> tied to the session
     * which is attached to this <code>ObservationManager</code> instance.
     */
    public EventStateCollection createEventStateCollection() {
        return new EventStateCollection(dispatcher, session, null);
    }
}
